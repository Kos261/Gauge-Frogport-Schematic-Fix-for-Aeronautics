package pl.konstanty.factoryschematicfix;

import com.simibubi.create.content.contraptions.StructureTransform;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Geometry helpers for Frogport positions on a Chain Conveyor loop. */
public final class FrogportTargetGeometry {
    private FrogportTargetGeometry() {}

    /**
     * Create stores the position on a single-conveyor loop as an angle in degrees.
     * Relative BlockPos transformation alone is therefore insufficient when the
     * whole structure is rotated or mirrored.
     */
    public static float transformLoopChainPos(float chainPos, StructureTransform transform) {
        Vec3 direction = directionForAngle(chainPos);
        Vec3 transformed = transform.applyWithoutOffsetUncentered(direction);
        return angleForDirection(transformed);
    }

    public static Vec3 directionForAngle(float angle) {
        double radians = Math.toRadians(angle);
        return new Vec3(Math.sin(radians), 0, Math.cos(radians));
    }

    public static float angleForDirection(Vec3 direction) {
        float angle = (float) (Mth.atan2(direction.x, direction.z) * Mth.RAD_TO_DEG);
        angle %= 360f;
        if (angle < 0)
            angle += 360f;
        return angle;
    }
}
