package pl.konstanty.factoryschematicfix.mixin;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.konstanty.factoryschematicfix.GaugeGraphData;
import pl.konstanty.factoryschematicfix.GaugeGraphRuntime;
import pl.konstanty.factoryschematicfix.access.FactoryPanelMetadataHolder;

@Mixin(FactoryPanelBlockEntity.class)
public abstract class FactoryPanelBlockEntityMixin implements FactoryPanelMetadataHolder {
    @Unique
    private CompoundTag factorySchematicFix$metadata = new CompoundTag();

    @Unique
    private boolean factorySchematicFix$registered;

    @Inject(method = "read", at = @At("TAIL"))
    private void factorySchematicFix$readMetadata(
        CompoundTag tag,
        HolderLookup.Provider registries,
        boolean clientPacket,
        CallbackInfo ci
    ) {
        if (tag.contains(GaugeGraphData.ROOT_TAG, Tag.TAG_COMPOUND))
            factorySchematicFix$metadata = tag.getCompound(GaugeGraphData.ROOT_TAG).copy();
        else
            factorySchematicFix$metadata = new CompoundTag();

        factorySchematicFix$registered = false;
        factorySchematicFix$tryRegister();
    }

    @Inject(method = "lazyTick", at = @At("HEAD"))
    private void factorySchematicFix$retryRegistration(CallbackInfo ci) {
        factorySchematicFix$tryRegister();
    }

    @Unique
    private void factorySchematicFix$tryRegister() {
        if (factorySchematicFix$registered || factorySchematicFix$metadata.isEmpty())
            return;
        if (!factorySchematicFix$metadata.hasUUID("Instance"))
            return;

        FactoryPanelBlockEntity self = (FactoryPanelBlockEntity) (Object) this;
        if (!(self.getLevel() instanceof ServerLevel serverLevel))
            return;

        GaugeGraphRuntime.register(serverLevel, self, factorySchematicFix$metadata);
        factorySchematicFix$registered = true;
    }

    @Override
    public CompoundTag factorySchematicFix$getMetadata() {
        return factorySchematicFix$metadata;
    }

    @Override
    public void factorySchematicFix$setMetadata(CompoundTag metadata) {
        factorySchematicFix$metadata = metadata == null ? new CompoundTag() : metadata;
        factorySchematicFix$registered = false;
    }
}
