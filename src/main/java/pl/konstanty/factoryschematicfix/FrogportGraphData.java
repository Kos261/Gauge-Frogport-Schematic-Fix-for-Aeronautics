package pl.konstanty.factoryschematicfix;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.logistics.packagePort.PackagePortTarget.ChainConveyorFrogportTarget;
import com.simibubi.create.content.logistics.packagePort.frogport.FrogportBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Stores Frogport -> Chain Conveyor links as schematic-local node ids.
 *
 * Raw Frogport target data contains relative BlockPos vectors. Those are fragile
 * when a schematic is rotated/mirrored. Instead, the schematic stores ids for
 * the exact conveyor block (and, for a travelling chain, the opposite conveyor
 * endpoint). At print time the ids are resolved to the actual transformed world
 * positions and the target vectors are rebuilt from those positions.
 */
public final class FrogportGraphData {
    public static final String ROOT_TAG = "FactorySchematicFixFrogport";
    public static final int VERSION = 2;

    private FrogportGraphData() {}

    public static void annotateExport(Level level, AABB selection, CompoundTag structureNbt) {
        if (!structureNbt.contains("blocks", Tag.TAG_LIST))
            return;

        BlockPos origin = BlockPos.containing(selection.minX, selection.minY, selection.minZ);
        ListTag blocks = structureNbt.getList("blocks", Tag.TAG_COMPOUND);

        Map<BlockPos, CompoundTag> nbtByWorldPos = new HashMap<>();
        Set<BlockPos> conveyorPositions = new HashSet<>();

        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag blockEntry = blocks.getCompound(i);
            if (!blockEntry.contains("nbt", Tag.TAG_COMPOUND))
                continue;

            BlockPos localPos = readStructurePosition(blockEntry);
            if (localPos == null)
                continue;

            BlockPos worldPos = origin.offset(localPos);
            CompoundTag beNbt = blockEntry.getCompound("nbt");
            beNbt.remove(ROOT_TAG);
            nbtByWorldPos.put(worldPos, beNbt);

            if (level.getBlockEntity(worldPos) instanceof ChainConveyorBlockEntity)
                conveyorPositions.add(worldPos);
        }

        Map<BlockPos, Integer> conveyorIds = new HashMap<>();
        List<FrogMetadata> validFrogs = new ArrayList<>();
        int nextConveyorId = 0;
        int nextFrogId = 0;

        for (Map.Entry<BlockPos, CompoundTag> entry : nbtByWorldPos.entrySet()) {
            BlockPos frogPos = entry.getKey();
            if (!(level.getBlockEntity(frogPos) instanceof FrogportBlockEntity frogport))
                continue;
            if (!(frogport.target instanceof ChainConveyorFrogportTarget chainTarget))
                continue;

            // Mark the Frogport as managed even if its target leaves the schematic.
            // That way printing never keeps a stale pointer into the source factory.
            CompoundTag frogMeta = new CompoundTag();
            frogMeta.putInt("Version", VERSION);
            frogMeta.putBoolean("InternalTarget", false);
            entry.getValue().put(ROOT_TAG, frogMeta);

            BlockPos targetPos = frogPos.offset(chainTarget.relativePos);
            if (!conveyorPositions.contains(targetPos))
                continue;

            BlockPos connectionEndPos = null;
            if (chainTarget.connection != null) {
                connectionEndPos = targetPos.offset(chainTarget.connection);
                if (!conveyorPositions.contains(connectionEndPos))
                    continue;
            }

            Integer targetId = conveyorIds.get(targetPos);
            if (targetId == null) {
                targetId = nextConveyorId++;
                conveyorIds.put(targetPos, targetId);
            }

            Integer connectionId = null;
            if (connectionEndPos != null) {
                connectionId = conveyorIds.get(connectionEndPos);
                if (connectionId == null) {
                    connectionId = nextConveyorId++;
                    conveyorIds.put(connectionEndPos, connectionId);
                }
            }

            frogMeta.putBoolean("InternalTarget", true);
            frogMeta.putInt("FrogId", nextFrogId++);
            frogMeta.putInt("TargetNode", targetId);
            frogMeta.putFloat("ChainPos", chainTarget.chainPos);
            frogMeta.putBoolean("Flipped", chainTarget.flipped);
            if (connectionId != null)
                frogMeta.putInt("ConnectionNode", connectionId);

            validFrogs.add(new FrogMetadata(frogMeta));
        }

        int totalFrogs = validFrogs.size();
        if (totalFrogs == 0)
            return;

        for (FrogMetadata frog : validFrogs)
            frog.tag.putInt("TotalFrogs", totalFrogs);

        for (Map.Entry<BlockPos, Integer> node : conveyorIds.entrySet()) {
            CompoundTag beNbt = nbtByWorldPos.get(node.getKey());
            if (beNbt == null)
                continue;

            CompoundTag metadata = new CompoundTag();
            metadata.putInt("Version", VERSION);
            metadata.putBoolean("ConveyorNode", true);
            metadata.putInt("NodeId", node.getValue());
            metadata.putInt("TotalFrogs", totalFrogs);
            beNbt.put(ROOT_TAG, metadata);
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

    private record FrogMetadata(CompoundTag tag) {}
}
