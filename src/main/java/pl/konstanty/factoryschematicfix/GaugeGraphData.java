package pl.konstanty.factoryschematicfix;

import java.util.HashMap;
import java.util.Map;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock.PanelSlot;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public final class GaugeGraphData {
    public static final String ROOT_TAG = "FactorySchematicFix";
    public static final int VERSION = 1;

    private GaugeGraphData() {}

    /**
     * Adds a small, schematic-local graph to Factory Gauge block-entity NBT.
     * The graph uses integer node ids instead of absolute coordinates, so the
     * connections can be reconstructed after translating/rotating/mirroring a schematic.
     */
    public static void annotateExport(Level level, AABB selection, CompoundTag structureNbt) {
        if (!structureNbt.contains("blocks", Tag.TAG_LIST))
            return;

        BlockPos origin = BlockPos.containing(selection.minX, selection.minY, selection.minZ);
        ListTag blocks = structureNbt.getList("blocks", Tag.TAG_COMPOUND);

        Map<FactoryPanelPosition, Node> nodes = new HashMap<>();
        Map<BlockPos, CompoundTag> metadataByBlock = new HashMap<>();
        int nextId = 0;

        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag blockEntry = blocks.getCompound(i);
            if (!blockEntry.contains("nbt", Tag.TAG_COMPOUND))
                continue;

            CompoundTag blockEntityNbt = blockEntry.getCompound("nbt");
            blockEntityNbt.remove(ROOT_TAG);

            BlockPos localPos = readStructurePosition(blockEntry);
            if (localPos == null)
                continue;

            BlockPos worldPos = origin.offset(localPos);
            if (!(level.getBlockEntity(worldPos) instanceof FactoryPanelBlockEntity panelBe))
                continue;

            CompoundTag metadata = new CompoundTag();
            metadata.putInt("Version", VERSION);
            metadata.put("Nodes", new ListTag());
            metadata.put("Edges", new ListTag());
            blockEntityNbt.put(ROOT_TAG, metadata);
            metadataByBlock.put(worldPos, metadata);

            for (PanelSlot slot : PanelSlot.values()) {
                FactoryPanelBehaviour panel = panelBe.panels.get(slot);
                if (panel == null || !panel.active)
                    continue;

                int id = nextId++;
                CompoundTag nodeTag = new CompoundTag();
                nodeTag.putInt("Id", id);
                nodeTag.putString("Slot", slot.name());
                metadata.getList("Nodes", Tag.TAG_COMPOUND).add(nodeTag);
                nodes.put(panel.getPanelPosition(), new Node(id, panel, metadata));
            }
        }

        if (nodes.isEmpty()) {
            metadataByBlock.values().forEach(tag -> tag.putInt("TotalNodes", 0));
            return;
        }

        int totalNodes = nodes.size();
        metadataByBlock.values().forEach(tag -> tag.putInt("TotalNodes", totalNodes));

        // target.targetedBy stores the source panel and all connection parameters.
        for (Node targetNode : nodes.values()) {
            for (Map.Entry<FactoryPanelPosition, FactoryPanelConnection> entry : targetNode.panel.targetedBy.entrySet()) {
                Node sourceNode = nodes.get(entry.getKey());
                if (sourceNode == null)
                    continue; // connection leaves the copied schematic; do not recreate it

                FactoryPanelConnection connection = entry.getValue();
                CompoundTag edgeTag = new CompoundTag();
                edgeTag.putInt("From", sourceNode.id);
                edgeTag.putInt("To", targetNode.id);
                edgeTag.putInt("Amount", connection.amount);
                edgeTag.putInt("Bend", connection.arrowBendMode);
                sourceNode.metadata.getList("Edges", Tag.TAG_COMPOUND).add(edgeTag);
            }
        }
    }

    private static BlockPos readStructurePosition(CompoundTag blockEntry) {
        if (!blockEntry.contains("pos", Tag.TAG_LIST))
            return null;

        ListTag pos = blockEntry.getList("pos", Tag.TAG_INT);
        if (pos.size() != 3)
            return null;

        return new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2));
    }

    private record Node(int id, FactoryPanelBehaviour panel, CompoundTag metadata) {}
}
