package pl.konstanty.factoryschematicfix.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.konstanty.factoryschematicfix.SableChainConveyorMoveCompat;
import pl.konstanty.factoryschematicfix.SableFrogportMoveCompat;
import pl.konstanty.factoryschematicfix.SableGaugeMoveCompat;

/** Optional Sable integration without a hard compile/runtime dependency. */
@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.api.SubLevelAssemblyHelper", remap = false)
public abstract class SableSubLevelAssemblyHelperMixin {
    @Inject(method = "moveBlocks(Lnet/minecraft/server/level/ServerLevel;Ldev/ryanhcode/sable/api/SubLevelAssemblyHelper$AssemblyTransform;Ljava/lang/Iterable;)V", at = @At("HEAD"), remap = false)
    private static void factorySchematicFix$captureBeforeMove(
        ServerLevel sourceLevel,
        @Coerce Object assemblyTransform,
        Iterable<BlockPos> blocks,
        CallbackInfo ci
    ) {
        SableGaugeMoveCompat.beforeMove(sourceLevel, assemblyTransform, blocks);
        SableChainConveyorMoveCompat.beforeMove(sourceLevel, assemblyTransform, blocks);
        SableFrogportMoveCompat.beforeMove(sourceLevel, assemblyTransform, blocks);
    }

    @Inject(method = "moveBlocks(Lnet/minecraft/server/level/ServerLevel;Ldev/ryanhcode/sable/api/SubLevelAssemblyHelper$AssemblyTransform;Ljava/lang/Iterable;)V", at = @At("TAIL"), remap = false)
    private static void factorySchematicFix$restoreAfterMove(
        ServerLevel sourceLevel,
        @Coerce Object assemblyTransform,
        Iterable<BlockPos> blocks,
        CallbackInfo ci
    ) {
        // Conveyor vectors must be fixed before Frogports register against them.
        SableChainConveyorMoveCompat.afterMove(assemblyTransform);
        SableGaugeMoveCompat.afterMove(sourceLevel, assemblyTransform);
        SableFrogportMoveCompat.afterMove(assemblyTransform);
    }
}
