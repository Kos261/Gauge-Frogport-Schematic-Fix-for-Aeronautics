package pl.konstanty.factoryschematicfix.mixin;

import com.simibubi.create.api.contraption.transformable.TransformableBlockEntity;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.logistics.packagePort.PackagePortTarget;
import com.simibubi.create.content.logistics.packagePort.PackagePortTarget.ChainConveyorFrogportTarget;
import com.simibubi.create.content.logistics.packagePort.frogport.FrogportBlockEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.konstanty.factoryschematicfix.FrogportGraphData;
import pl.konstanty.factoryschematicfix.FrogportGraphRuntime;
import pl.konstanty.factoryschematicfix.FrogportTargetGeometry;
import pl.konstanty.factoryschematicfix.access.FrogportSchematicMetadataHolder;

/**
 * Adds legacy/fallback StructureTransform support and carries the graph metadata
 * used by the robust Frogport schematic reconstruction path.
 */
@Mixin(FrogportBlockEntity.class)
public abstract class FrogportBlockEntityMixin
    implements TransformableBlockEntity, FrogportSchematicMetadataHolder {

    @Unique
    private CompoundTag factorySchematicFix$frogportMetadata = new CompoundTag();

    @Unique
    private boolean factorySchematicFix$frogportRegistered;

    @Override
    public void transform(BlockEntity blockEntity, StructureTransform transform) {
        FrogportBlockEntity frogport = (FrogportBlockEntity) (Object) this;
        PackagePortTarget target = frogport.target;
        if (target != null) {
            target.relativePos = transform.applyWithoutOffset(target.relativePos);

            if (target instanceof ChainConveyorFrogportTarget chainTarget) {
                if (chainTarget.connection != null) {
                    // Travelling-chain targets store distance along the chain; only
                    // the connection vector itself needs transforming.
                    chainTarget.connection = transform.applyWithoutOffset(chainTarget.connection);
                } else {
                    // Loop targets store an absolute horizontal angle around the
                    // conveyor. Rotate/mirror that angle together with the schematic.
                    chainTarget.chainPos = FrogportTargetGeometry.transformLoopChainPos(
                        chainTarget.chainPos, transform
                    );
                }
            }
        }

        // New schematics rebuild their target from graph metadata rather than
        // the raw Target NBT. Keep the stored loop angle in that metadata in sync
        // with the same StructureTransform as well.
        if (!factorySchematicFix$frogportMetadata.isEmpty()
            && factorySchematicFix$frogportMetadata.getInt("Version") == FrogportGraphData.VERSION
            && factorySchematicFix$frogportMetadata.getBoolean("InternalTarget")
            && !factorySchematicFix$frogportMetadata.contains("ConnectionNode")) {
            factorySchematicFix$frogportMetadata.putFloat(
                "ChainPos",
                FrogportTargetGeometry.transformLoopChainPos(
                    factorySchematicFix$frogportMetadata.getFloat("ChainPos"), transform
                )
            );
        }
    }

    @Inject(method = "read", at = @At("TAIL"))
    private void factorySchematicFix$readFrogportMetadata(
        CompoundTag tag,
        HolderLookup.Provider registries,
        boolean clientPacket,
        CallbackInfo ci
    ) {
        if (tag.contains(FrogportGraphData.ROOT_TAG, Tag.TAG_COMPOUND))
            factorySchematicFix$frogportMetadata = tag.getCompound(FrogportGraphData.ROOT_TAG).copy();
        else
            factorySchematicFix$frogportMetadata = new CompoundTag();

        factorySchematicFix$frogportRegistered = false;
        factorySchematicFix$tryRegisterFrogport();
    }

    @Inject(method = "lazyTick", at = @At("HEAD"))
    private void factorySchematicFix$retryFrogportRegistration(CallbackInfo ci) {
        factorySchematicFix$tryRegisterFrogport();
    }

    @Unique
    private void factorySchematicFix$tryRegisterFrogport() {
        if (factorySchematicFix$frogportRegistered || factorySchematicFix$frogportMetadata.isEmpty())
            return;
        if (!factorySchematicFix$frogportMetadata.hasUUID("Instance"))
            return;

        FrogportBlockEntity self = (FrogportBlockEntity) (Object) this;
        if (!(self.getLevel() instanceof ServerLevel serverLevel))
            return;

        FrogportGraphRuntime.registerFrogport(serverLevel, self, factorySchematicFix$frogportMetadata);
        factorySchematicFix$frogportRegistered = true;
    }

    @Override
    public CompoundTag factorySchematicFix$getFrogportMetadata() {
        return factorySchematicFix$frogportMetadata;
    }

    @Override
    public void factorySchematicFix$setFrogportMetadata(CompoundTag metadata) {
        factorySchematicFix$frogportMetadata = metadata == null ? new CompoundTag() : metadata;
        factorySchematicFix$frogportRegistered = false;
    }
}
