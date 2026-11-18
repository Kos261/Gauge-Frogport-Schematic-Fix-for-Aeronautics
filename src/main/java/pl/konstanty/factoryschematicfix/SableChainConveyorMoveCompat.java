package pl.konstanty.factoryschematicfix;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorPackage;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Sable rotates BlockState during a move but copies block-entity NBT verbatim.
 * Create stores Chain Conveyor links as relative BlockPos vectors, so those
 * vectors must be reconstructed from transformed absolute endpoints.
 *
 * Connections crossing the assembly boundary are deliberately dropped: a
 * sub-level chain must never keep a relative pointer into normal-world space.
 */
public final class SableChainConveyorMoveCompat {
    private static final ThreadLocal<List<MoveSnapshot>> SNAPSHOT_STACK =
        ThreadLocal.withInitial(ArrayList::new);

    private SableChainConveyorMoveCompat() {}

    public static void beforeMove(ServerLevel sourceLevel, Object assemblyTransform, Iterable<BlockPos> blocks) {
        Set<BlockPos> moved = new HashSet<>();
        for (BlockPos pos : blocks)
            moved.add(pos.immutable());

        List<ConveyorSnapshot> conveyors = new ArrayList<>();
        for (BlockPos pos : moved) {
            if (!(sourceLevel.getBlockEntity(pos) instanceof ChainConveyorBlockEntity conveyor))
                continue;

            List<BlockPos> internalConnectionEndpoints = conveyor.connections.stream()
                .map(pos::offset)
                .filter(moved::contains)
                .map(BlockPos::immutable)
                .toList();

            conveyors.add(new ConveyorSnapshot(pos.immutable(), internalConnectionEndpoints));
        }

        SNAPSHOT_STACK.get().add(new MoveSnapshot(assemblyTransform, moved, conveyors));
    }

    public static void afterMove(Object assemblyTransform) {
        List<MoveSnapshot> stack = SNAPSHOT_STACK.get();
        if (stack.isEmpty())
            return;

        MoveSnapshot snapshot = stack.remove(stack.size() - 1);
        if (stack.isEmpty())
            SNAPSHOT_STACK.remove();

        if (snapshot.assemblyTransform != assemblyTransform)
            return;

        ServerLevel destinationLevel = resultingLevel(assemblyTransform);
        if (destinationLevel == null)
            return;

        for (ConveyorSnapshot conveyorSnapshot : snapshot.conveyors) {
            BlockPos newPos = transformBlockPos(assemblyTransform, conveyorSnapshot.oldPos);
            if (newPos == null)
                continue;
            if (!(destinationLevel.getBlockEntity(newPos) instanceof ChainConveyorBlockEntity conveyor))
                continue;

            Set<BlockPos> newConnections = new HashSet<>();
            for (BlockPos oldEndpoint : conveyorSnapshot.internalConnectionEndpoints) {
                BlockPos newEndpoint = transformBlockPos(assemblyTransform, oldEndpoint);
                if (newEndpoint != null)
                    newConnections.add(newEndpoint.subtract(newPos));
            }
            conveyor.connections = newConnections;

            // Remap the keys of packages already loaded from NBT, preserving the
            // destination BE's package objects rather than reusing source objects.
            Map<BlockPos, List<ChainConveyorPackage>> travelling = conveyor.getTravellingPackages();
            Map<BlockPos, List<ChainConveyorPackage>> oldTravelling = new HashMap<>(travelling);
            travelling.clear();
            for (Map.Entry<BlockPos, List<ChainConveyorPackage>> entry : oldTravelling.entrySet()) {
                BlockPos oldEndpoint = conveyorSnapshot.oldPos.offset(entry.getKey());
                if (!snapshot.movedBlocks.contains(oldEndpoint))
                    continue;
                BlockPos newEndpoint = transformBlockPos(assemblyTransform, oldEndpoint);
                if (newEndpoint != null)
                    travelling.put(newEndpoint.subtract(newPos), entry.getValue());
            }

            conveyor.connectionStats = null;
            conveyor.notifyUpdate();
        }
    }

    private static BlockPos transformBlockPos(Object assemblyTransform, BlockPos oldPos) {
        try {
            Method apply = assemblyTransform.getClass().getMethod("apply", BlockPos.class);
            Object result = apply.invoke(assemblyTransform, oldPos);
            return result instanceof BlockPos pos ? pos : null;
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
            return null;
        }
    }

    private static ServerLevel resultingLevel(Object assemblyTransform) {
        try {
            Method getLevel = assemblyTransform.getClass().getMethod("getLevel");
            Object result = getLevel.invoke(assemblyTransform);
            return result instanceof ServerLevel level ? level : null;
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
            return null;
        }
    }

    private record MoveSnapshot(
        Object assemblyTransform,
        Set<BlockPos> movedBlocks,
        List<ConveyorSnapshot> conveyors
    ) {}

    private record ConveyorSnapshot(
        BlockPos oldPos,
        List<BlockPos> internalConnectionEndpoints
    ) {}
}
