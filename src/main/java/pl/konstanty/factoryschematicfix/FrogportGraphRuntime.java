package pl.konstanty.factoryschematicfix;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.logistics.packagePort.PackagePortTarget.ChainConveyorFrogportTarget;
import com.simibubi.create.content.logistics.packagePort.frogport.FrogportBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** Rebuilds schematic Frogport targets from transformed conveyor node positions. */
public final class FrogportGraphRuntime {
    private static final Map<SessionKey, Session> SESSIONS = new HashMap<>();
    private static final long SESSION_TIMEOUT_TICKS = 20L * 60L * 10L;

    private FrogportGraphRuntime() {}

    public static synchronized void registerConveyor(
        ServerLevel level,
        ChainConveyorBlockEntity conveyor,
        CompoundTag metadata
    ) {
        if (!valid(metadata) || !metadata.getBoolean("ConveyorNode") || !metadata.hasUUID("Instance"))
            return;

        Session session = session(level, metadata);
        session.nodes.put(metadata.getInt("NodeId"), conveyor.getBlockPos().immutable());
        session.lastTouched = level.getGameTime();
        resolve(level, session);
        finishIfComplete(level, metadata.getUUID("Instance"), session);
    }

    public static synchronized void registerFrogport(
        ServerLevel level,
        FrogportBlockEntity frogport,
        CompoundTag metadata
    ) {
        if (!valid(metadata) || !metadata.hasUUID("Instance"))
            return;
        if (!metadata.getBoolean("InternalTarget")) {
            if (frogport.target != null) {
                frogport.target.deregister(frogport, level, frogport.getBlockPos());
                frogport.target = null;
                frogport.notifyUpdate();
            }
            return;
        }

        Session session = session(level, metadata);
        int frogId = metadata.getInt("FrogId");
        session.registeredFrogs.add(frogId);
        session.pending.put(frogId, new PendingFrog(
            frogport.getBlockPos().immutable(),
            metadata.getInt("TargetNode"),
            metadata.contains("ConnectionNode") ? metadata.getInt("ConnectionNode") : null,
            metadata.getFloat("ChainPos"),
            metadata.getBoolean("Flipped")
        ));
        session.lastTouched = level.getGameTime();

        resolve(level, session);
        finishIfComplete(level, metadata.getUUID("Instance"), session);
    }

    private static Session session(ServerLevel level, CompoundTag metadata) {
        UUID instance = metadata.getUUID("Instance");
        SessionKey key = new SessionKey(level.dimension(), instance);
        Session session = SESSIONS.computeIfAbsent(key, ignored -> new Session());
        session.expectedFrogs = Math.max(session.expectedFrogs, metadata.getInt("TotalFrogs"));
        return session;
    }

    private static boolean valid(CompoundTag metadata) {
        return metadata != null && metadata.getInt("Version") == FrogportGraphData.VERSION;
    }

    private static void resolve(ServerLevel level, Session session) {
        Iterator<Map.Entry<Integer, PendingFrog>> iterator = session.pending.entrySet().iterator();
        while (iterator.hasNext()) {
            PendingFrog pending = iterator.next().getValue();
            BlockPos targetPos = session.nodes.get(pending.targetNode);
            if (targetPos == null)
                continue;

            if (!(level.getBlockEntity(pending.frogPos) instanceof FrogportBlockEntity frogport))
                continue;
            if (!(level.getBlockEntity(targetPos) instanceof ChainConveyorBlockEntity targetConveyor))
                continue;

            BlockPos connection = null;
            if (pending.connectionNode != null) {
                BlockPos connectionEnd = session.nodes.get(pending.connectionNode);
                if (connectionEnd == null)
                    continue;
                if (!(level.getBlockEntity(connectionEnd) instanceof ChainConveyorBlockEntity))
                    continue;

                connection = connectionEnd.subtract(targetPos);
                // Wait until the transformed conveyor NBT has been loaded as well.
                if (!targetConveyor.connections.contains(connection))
                    continue;
            }

            if (frogport.target != null)
                frogport.target.deregister(frogport, level, frogport.getBlockPos());

            ChainConveyorFrogportTarget rebuilt = new ChainConveyorFrogportTarget(
                targetPos.subtract(frogport.getBlockPos()),
                pending.chainPos,
                connection,
                pending.flipped
            );

            // If the conveyor is already powered, align Create's direction marker
            // with the current network. If it is stopped, keep the saved marker.
            if (targetConveyor.getSpeed() != 0)
                rebuilt.setup(frogport, level, frogport.getBlockPos());

            frogport.target = rebuilt;
            rebuilt.register(frogport, level, frogport.getBlockPos());
            frogport.notifyUpdate();
            iterator.remove();
            session.resolvedFrogs++;
        }
    }

    private static void finishIfComplete(ServerLevel level, UUID instance, Session session) {
        if (session.expectedFrogs > 0
            && session.registeredFrogs.size() >= session.expectedFrogs
            && session.pending.isEmpty()
            && session.resolvedFrogs >= session.expectedFrogs) {
            SESSIONS.remove(new SessionKey(level.dimension(), instance));
        }
        cleanup(level.getGameTime());
    }

    private static void cleanup(long gameTime) {
        SESSIONS.entrySet().removeIf(entry -> gameTime - entry.getValue().lastTouched > SESSION_TIMEOUT_TICKS);
    }

    private record SessionKey(ResourceKey<Level> dimension, UUID instance) {}

    private record PendingFrog(
        BlockPos frogPos,
        int targetNode,
        Integer connectionNode,
        float chainPos,
        boolean flipped
    ) {}

    private static final class Session {
        final Map<Integer, BlockPos> nodes = new HashMap<>();
        final Map<Integer, PendingFrog> pending = new HashMap<>();
        final Set<Integer> registeredFrogs = new HashSet<>();
        int expectedFrogs;
        int resolvedFrogs;
        long lastTouched;
    }
}
