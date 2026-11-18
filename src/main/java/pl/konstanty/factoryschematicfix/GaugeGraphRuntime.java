package pl.konstanty.factoryschematicfix;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock.PanelSlot;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public final class GaugeGraphRuntime {
    private static final Map<SessionKey, Session> SESSIONS = new HashMap<>();
    private static final long SESSION_TIMEOUT_TICKS = 20L * 60L * 10L;

    private GaugeGraphRuntime() {}

    public static synchronized void register(ServerLevel level, FactoryPanelBlockEntity blockEntity, CompoundTag metadata) {
        if (metadata.getInt("Version") != GaugeGraphData.VERSION || !metadata.hasUUID("Instance"))
            return;

        UUID instance = metadata.getUUID("Instance");
        SessionKey key = new SessionKey(level.dimension(), instance);
        Session session = SESSIONS.computeIfAbsent(key, ignored -> new Session());
        session.expectedNodes = Math.max(session.expectedNodes, metadata.getInt("TotalNodes"));
        session.lastTouched = level.getGameTime();

        ListTag nodeList = metadata.getList("Nodes", Tag.TAG_COMPOUND);
        for (int i = 0; i < nodeList.size(); i++) {
            CompoundTag nodeTag = nodeList.getCompound(i);
            PanelSlot slot;
            try {
                slot = PanelSlot.valueOf(nodeTag.getString("Slot"));
            } catch (IllegalArgumentException ignored) {
                continue;
            }

            FactoryPanelBehaviour panel = blockEntity.panels.get(slot);
            if (panel == null || !panel.active)
                continue;

            session.nodes.put(nodeTag.getInt("Id"), panel.getPanelPosition());
        }

        ListTag edgeList = metadata.getList("Edges", Tag.TAG_COMPOUND);
        for (int i = 0; i < edgeList.size(); i++) {
            CompoundTag edgeTag = edgeList.getCompound(i);
            session.pendingEdges.add(new Edge(
                edgeTag.getInt("From"),
                edgeTag.getInt("To"),
                edgeTag.getInt("Amount"),
                edgeTag.getInt("Bend")
            ));
        }

        resolve(level, session);

        if (session.expectedNodes > 0
            && session.nodes.size() >= session.expectedNodes
            && session.pendingEdges.isEmpty()) {
            SESSIONS.remove(key);
        }

        cleanup(level.getGameTime());
    }

    private static void resolve(ServerLevel level, Session session) {
        Iterator<Edge> iterator = session.pendingEdges.iterator();
        while (iterator.hasNext()) {
            Edge edge = iterator.next();
            FactoryPanelPosition sourcePos = session.nodes.get(edge.from);
            FactoryPanelPosition targetPos = session.nodes.get(edge.to);
            if (sourcePos == null || targetPos == null)
                continue;

            FactoryPanelBehaviour source = FactoryPanelBehaviour.at(level, sourcePos);
            FactoryPanelBehaviour target = FactoryPanelBehaviour.at(level, targetPos);
            if (source == null || target == null)
                continue;

            FactoryPanelConnection connection = target.targetedBy.get(sourcePos);
            if (connection == null) {
                target.addConnection(sourcePos);
                connection = target.targetedBy.get(sourcePos);
            }

            if (connection == null)
                continue;

            connection.amount = edge.amount;
            connection.arrowBendMode = edge.bend;
            source.targeting.add(targetPos);

            source.panelBE().notifyUpdate();
            target.panelBE().notifyUpdate();
            iterator.remove();
        }
    }

    private static void cleanup(long gameTime) {
        SESSIONS.entrySet().removeIf(entry -> gameTime - entry.getValue().lastTouched > SESSION_TIMEOUT_TICKS);
    }

    private record SessionKey(ResourceKey<Level> dimension, UUID instance) {}
    private record Edge(int from, int to, int amount, int bend) {}

    private static final class Session {
        final Map<Integer, FactoryPanelPosition> nodes = new HashMap<>();
        final Set<Edge> pendingEdges = new HashSet<>();
        int expectedNodes;
        long lastTouched;
    }
}
