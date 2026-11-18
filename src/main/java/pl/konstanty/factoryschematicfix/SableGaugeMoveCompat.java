package pl.konstanty.factoryschematicfix;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock.PanelSlot;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Optional compatibility layer for Sable sub-level assembly/disassembly.
 *
 * Sable moves block entities by copying their full NBT and only rewriting the
 * block entity's own x/y/z. Create Factory Gauge connections, however, contain
 * absolute FactoryPanelPosition values inside their NBT. Those positions become
 * stale as soon as the gauge is moved into/out of a Sable sub-level.
 *
 * We therefore snapshot only gauge->gauge edges whose two endpoint blocks are
 * part of the same Sable move. After Sable finishes moving the blocks, all
 * gauge->gauge references on the moved gauges are cleared and that internal
 * graph is rebuilt using Sable's AssemblyTransform.
 *
 * Connections crossing the move boundary are deliberately NOT restored. A
 * normal-world gauge and a sub-level gauge are different address spaces and
 * should not remain connected.
 */
public final class SableGaugeMoveCompat {
    private static final ThreadLocal<List<MoveSnapshot>> SNAPSHOT_STACK =
        ThreadLocal.withInitial(ArrayList::new);

    private SableGaugeMoveCompat() {}

    public static void beforeMove(ServerLevel sourceLevel, Object assemblyTransform, Iterable<BlockPos> blocks) {
        Set<BlockPos> movedBlocks = new HashSet<>();
        for (BlockPos pos : blocks)
            movedBlocks.add(pos.immutable());

        Set<FactoryPanelPosition> movedPanels = new LinkedHashSet<>();
        Set<InternalEdge> internalEdges = new LinkedHashSet<>();
        Set<ExternalOutgoing> externalOutgoing = new LinkedHashSet<>();
        Set<ExternalIncoming> externalIncoming = new LinkedHashSet<>();

        for (BlockPos blockPos : movedBlocks) {
            if (!(sourceLevel.getBlockEntity(blockPos) instanceof FactoryPanelBlockEntity panelBe))
                continue;

            for (PanelSlot slot : PanelSlot.values()) {
                FactoryPanelBehaviour panel = panelBe.panels.get(slot);
                if (panel == null || !panel.active)
                    continue;

                FactoryPanelPosition targetPos = panel.getPanelPosition();
                movedPanels.add(targetPos);

                // target.targetedBy is the authoritative place for connection
                // parameters (amount and arrow bend mode).
                for (var entry : panel.targetedBy.entrySet()) {
                    FactoryPanelPosition sourcePos = entry.getKey();
                    FactoryPanelConnection connection = entry.getValue();

                    if (movedBlocks.contains(sourcePos.pos())) {
                        internalEdges.add(new InternalEdge(
                            sourcePos,
                            targetPos,
                            connection.amount,
                            connection.arrowBendMode
                        ));
                    } else {
                        externalIncoming.add(new ExternalIncoming(sourcePos, targetPos));
                    }
                }

                for (FactoryPanelPosition outgoingTarget : panel.targeting) {
                    if (!movedBlocks.contains(outgoingTarget.pos()))
                        externalOutgoing.add(new ExternalOutgoing(targetPos, outgoingTarget));
                }
            }
        }

        SNAPSHOT_STACK.get().add(new MoveSnapshot(
            assemblyTransform,
            movedPanels,
            internalEdges,
            externalOutgoing,
            externalIncoming
        ));
    }

    public static void afterMove(ServerLevel sourceLevel, Object assemblyTransform) {
        List<MoveSnapshot> stack = SNAPSHOT_STACK.get();
        if (stack.isEmpty())
            return;

        MoveSnapshot snapshot = stack.remove(stack.size() - 1);
        if (stack.isEmpty())
            SNAPSHOT_STACK.remove();

        // Guard against a nested/unexpected move pairing. It is better to skip
        // reconstruction than to apply a graph to a different Sable transform.
        if (snapshot.assemblyTransform != assemblyTransform)
            return;

        ServerLevel destinationLevel = resultingLevel(assemblyTransform);
        if (destinationLevel == null)
            return;

        // First remove the two halves of any connection that crossed the move
        // boundary. Those connections are intentionally unsupported.
        cleanupExternalSourceEndpoints(sourceLevel, snapshot);

        // Sable copied full NBT, so moved gauges currently contain stale absolute
        // positions. Clear only gauge<->gauge references. targetedByLinks is a
        // separate Create mechanism and is intentionally left untouched.
        for (FactoryPanelPosition oldPanelPos : snapshot.movedPanels) {
            FactoryPanelPosition newPanelPos = transform(assemblyTransform, oldPanelPos);
            if (newPanelPos == null)
                continue;

            FactoryPanelBehaviour panel = FactoryPanelBehaviour.at(destinationLevel, newPanelPos);
            if (panel == null)
                continue;

            panel.targeting.clear();
            panel.targetedBy.clear();
            panel.panelBE().notifyUpdate();
        }

        // Rebuild only edges whose two endpoint gauges moved together.
        for (InternalEdge edge : snapshot.internalEdges) {
            FactoryPanelPosition newSourcePos = transform(assemblyTransform, edge.source);
            FactoryPanelPosition newTargetPos = transform(assemblyTransform, edge.target);
            if (newSourcePos == null || newTargetPos == null)
                continue;

            FactoryPanelBehaviour source = FactoryPanelBehaviour.at(destinationLevel, newSourcePos);
            FactoryPanelBehaviour target = FactoryPanelBehaviour.at(destinationLevel, newTargetPos);
            if (source == null || target == null)
                continue;

            target.addConnection(newSourcePos);
            FactoryPanelConnection connection = target.targetedBy.get(newSourcePos);
            if (connection == null)
                continue;

            connection.amount = edge.amount;
            connection.arrowBendMode = edge.bend;
            source.targeting.add(newTargetPos);

            source.panelBE().notifyUpdate();
            target.panelBE().notifyUpdate();
        }
    }

    private static void cleanupExternalSourceEndpoints(ServerLevel sourceLevel, MoveSnapshot snapshot) {
        for (ExternalOutgoing edge : snapshot.externalOutgoing) {
            FactoryPanelBehaviour externalTarget = FactoryPanelBehaviour.at(sourceLevel, edge.externalTarget);
            if (externalTarget == null)
                continue;

            if (externalTarget.targetedBy.remove(edge.movedSource) != null)
                externalTarget.panelBE().notifyUpdate();
        }

        for (ExternalIncoming edge : snapshot.externalIncoming) {
            FactoryPanelBehaviour externalSource = FactoryPanelBehaviour.at(sourceLevel, edge.externalSource);
            if (externalSource == null)
                continue;

            if (externalSource.targeting.remove(edge.movedTarget))
                externalSource.panelBE().notifyUpdate();
        }
    }

    private static FactoryPanelPosition transform(Object assemblyTransform, FactoryPanelPosition oldPos) {
        BlockPos newBlockPos = transformBlockPos(assemblyTransform, oldPos.pos());
        if (newBlockPos == null)
            return null;
        return new FactoryPanelPosition(newBlockPos, oldPos.slot());
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
        Set<FactoryPanelPosition> movedPanels,
        Set<InternalEdge> internalEdges,
        Set<ExternalOutgoing> externalOutgoing,
        Set<ExternalIncoming> externalIncoming
    ) {}

    private record InternalEdge(
        FactoryPanelPosition source,
        FactoryPanelPosition target,
        int amount,
        int bend
    ) {}

    private record ExternalOutgoing(
        FactoryPanelPosition movedSource,
        FactoryPanelPosition externalTarget
    ) {}

    private record ExternalIncoming(
        FactoryPanelPosition externalSource,
        FactoryPanelPosition movedTarget
    ) {}
}
