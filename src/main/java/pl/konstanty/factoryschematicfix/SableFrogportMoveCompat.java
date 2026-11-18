package pl.konstanty.factoryschematicfix;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.logistics.packagePort.PackagePortTarget.ChainConveyorFrogportTarget;
import com.simibubi.create.content.logistics.packagePort.frogport.FrogportBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Rebuilds Frogport targets after Sable moves blocks between level/sub-level. */
public final class SableFrogportMoveCompat {
    private static final ThreadLocal<List<MoveSnapshot>> SNAPSHOT_STACK =
        ThreadLocal.withInitial(ArrayList::new);

    private SableFrogportMoveCompat() {}

    public static void beforeMove(ServerLevel sourceLevel, Object assemblyTransform, Iterable<BlockPos> blocks) {
        Set<BlockPos> moved = new HashSet<>();
        for (BlockPos pos : blocks)
            moved.add(pos.immutable());

        List<FrogSnapshot> frogs = new ArrayList<>();
        for (BlockPos frogPos : moved) {
            if (!(sourceLevel.getBlockEntity(frogPos) instanceof FrogportBlockEntity frogport))
                continue;
            if (!(frogport.target instanceof ChainConveyorFrogportTarget target))
                continue;

            BlockPos targetPos = frogPos.offset(target.relativePos).immutable();
            BlockPos connectionEnd = target.connection == null
                ? null
                : targetPos.offset(target.connection).immutable();

            boolean internal = moved.contains(targetPos)
                && (connectionEnd == null || moved.contains(connectionEnd));

            // Remove the source-world registration before the blocks disappear.
            target.deregister(frogport, sourceLevel, frogPos);

            frogs.add(new FrogSnapshot(
                frogPos.immutable(),
                targetPos,
                connectionEnd,
                target.chainPos,
                target.flipped,
                internal
            ));
        }

        SNAPSHOT_STACK.get().add(new MoveSnapshot(assemblyTransform, frogs));
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

        for (FrogSnapshot frogSnapshot : snapshot.frogs) {
            BlockPos newFrogPos = transformBlockPos(assemblyTransform, frogSnapshot.oldFrogPos);
            if (newFrogPos == null)
                continue;
            if (!(destinationLevel.getBlockEntity(newFrogPos) instanceof FrogportBlockEntity frogport))
                continue;

            if (frogport.target != null)
                frogport.target.deregister(frogport, destinationLevel, newFrogPos);

            // Cross-boundary targets are deliberately unsupported: a Frogport
            // in a sub-level must not point into normal-world conveyor space.
            if (!frogSnapshot.internal) {
                frogport.target = null;
                frogport.notifyUpdate();
                continue;
            }

            BlockPos newTargetPos = transformBlockPos(assemblyTransform, frogSnapshot.oldTargetPos);
            if (newTargetPos == null
                || !(destinationLevel.getBlockEntity(newTargetPos) instanceof ChainConveyorBlockEntity targetConveyor)) {
                frogport.target = null;
                frogport.notifyUpdate();
                continue;
            }

            BlockPos newConnection = null;
            if (frogSnapshot.oldConnectionEnd != null) {
                BlockPos newConnectionEnd = transformBlockPos(assemblyTransform, frogSnapshot.oldConnectionEnd);
                if (newConnectionEnd == null
                    || !(destinationLevel.getBlockEntity(newConnectionEnd) instanceof ChainConveyorBlockEntity)) {
                    frogport.target = null;
                    frogport.notifyUpdate();
                    continue;
                }
                newConnection = newConnectionEnd.subtract(newTargetPos);
            }

            float newChainPos = frogSnapshot.chainPos;
            if (frogSnapshot.oldConnectionEnd == null) {
                Vec3 transformedDirection = transformDirection(
                    assemblyTransform,
                    frogSnapshot.oldTargetPos,
                    FrogportTargetGeometry.directionForAngle(frogSnapshot.chainPos)
                );
                if (transformedDirection != null)
                    newChainPos = FrogportTargetGeometry.angleForDirection(transformedDirection);
            }

            ChainConveyorFrogportTarget rebuilt = new ChainConveyorFrogportTarget(
                newTargetPos.subtract(newFrogPos),
                newChainPos,
                newConnection,
                frogSnapshot.flipped
            );

            if (targetConveyor.getSpeed() != 0)
                rebuilt.setup(frogport, destinationLevel, newFrogPos);

            frogport.target = rebuilt;
            rebuilt.register(frogport, destinationLevel, newFrogPos);
            frogport.notifyUpdate();
        }
    }


    private static Vec3 transformDirection(Object assemblyTransform, BlockPos origin, Vec3 direction) {
        try {
            Method apply = assemblyTransform.getClass().getMethod("apply", Vec3.class);
            Vec3 base = Vec3.atCenterOf(origin);
            Object transformedBase = apply.invoke(assemblyTransform, base);
            Object transformedTip = apply.invoke(assemblyTransform, base.add(direction));
            if (transformedBase instanceof Vec3 baseResult && transformedTip instanceof Vec3 tipResult)
                return tipResult.subtract(baseResult);
            return null;
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
            return null;
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

    private record MoveSnapshot(Object assemblyTransform, List<FrogSnapshot> frogs) {}

    private record FrogSnapshot(
        BlockPos oldFrogPos,
        BlockPos oldTargetPos,
        BlockPos oldConnectionEnd,
        float chainPos,
        boolean flipped,
        boolean internal
    ) {}
}
