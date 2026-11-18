package pl.konstanty.factoryschematicfix;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import net.createmod.catnip.levelWrappers.SchematicLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public final class PrintInstanceIds {
    private static final Map<Level, State> IDS = Collections.synchronizedMap(new WeakHashMap<>());

    private PrintInstanceIds() {}

    public static UUID forLevel(Level level) {
        BlockPos anchor = level instanceof SchematicLevel schematic
            ? schematic.anchor.immutable()
            : BlockPos.ZERO;

        synchronized (IDS) {
            State current = IDS.get(level);
            if (current == null || !current.anchor.equals(anchor)) {
                current = new State(anchor, UUID.randomUUID());
                IDS.put(level, current);
            }
            return current.id;
        }
    }

    private record State(BlockPos anchor, UUID id) {}
}
