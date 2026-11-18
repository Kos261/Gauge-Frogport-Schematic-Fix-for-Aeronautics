package pl.konstanty.factoryschematicfix.mixin;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import net.createmod.catnip.levelWrappers.SchematicLevel;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.konstanty.factoryschematicfix.FrogportGraphData;
import pl.konstanty.factoryschematicfix.FrogportGraphRuntime;
import pl.konstanty.factoryschematicfix.PrintInstanceIds;
import pl.konstanty.factoryschematicfix.access.FrogportSchematicMetadataHolder;

@Mixin(ChainConveyorBlockEntity.class)
public abstract class ChainConveyorBlockEntityMixin implements FrogportSchematicMetadataHolder {
    @Unique
    private CompoundTag factorySchematicFix$frogportMetadata = new CompoundTag();

    @Unique
    private boolean factorySchematicFix$conveyorRegistered;

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

        factorySchematicFix$conveyorRegistered = false;
        factorySchematicFix$tryRegisterConveyor();
    }

    @Inject(method = "writeSafe", at = @At("TAIL"))
    private void factorySchematicFix$carryNodeThroughSafeNbt(
        CompoundTag tag,
        HolderLookup.Provider registries,
        CallbackInfo ci
    ) {
        ChainConveyorBlockEntity self = (ChainConveyorBlockEntity) (Object) this;
        Level level = self.getLevel();
        if (!(level instanceof SchematicLevel))
            return;
        if (factorySchematicFix$frogportMetadata.isEmpty())
            return;
        if (factorySchematicFix$frogportMetadata.getInt("Version") != FrogportGraphData.VERSION)
            return;

        CompoundTag copy = factorySchematicFix$frogportMetadata.copy();
        copy.putUUID("Instance", PrintInstanceIds.forLevel(level));
        tag.put(FrogportGraphData.ROOT_TAG, copy);
    }

    @Inject(method = "lazyTick", at = @At("HEAD"))
    private void factorySchematicFix$retryConveyorRegistration(CallbackInfo ci) {
        factorySchematicFix$tryRegisterConveyor();
    }

    @Unique
    private void factorySchematicFix$tryRegisterConveyor() {
        if (factorySchematicFix$conveyorRegistered || factorySchematicFix$frogportMetadata.isEmpty())
            return;
        if (!factorySchematicFix$frogportMetadata.hasUUID("Instance"))
            return;

        ChainConveyorBlockEntity self = (ChainConveyorBlockEntity) (Object) this;
        if (!(self.getLevel() instanceof ServerLevel serverLevel))
            return;

        FrogportGraphRuntime.registerConveyor(serverLevel, self, factorySchematicFix$frogportMetadata);
        factorySchematicFix$conveyorRegistered = true;
    }

    @Override
    public CompoundTag factorySchematicFix$getFrogportMetadata() {
        return factorySchematicFix$frogportMetadata;
    }

    @Override
    public void factorySchematicFix$setFrogportMetadata(CompoundTag metadata) {
        factorySchematicFix$frogportMetadata = metadata == null ? new CompoundTag() : metadata;
        factorySchematicFix$conveyorRegistered = false;
    }
}
