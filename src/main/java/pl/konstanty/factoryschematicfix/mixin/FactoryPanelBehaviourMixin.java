package pl.konstanty.factoryschematicfix.mixin;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import net.createmod.catnip.levelWrappers.SchematicLevel;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.konstanty.factoryschematicfix.GaugeGraphData;
import pl.konstanty.factoryschematicfix.PrintInstanceIds;
import pl.konstanty.factoryschematicfix.access.FactoryPanelMetadataHolder;

@Mixin(FactoryPanelBehaviour.class)
public abstract class FactoryPanelBehaviourMixin {
    @Inject(method = "writeSafe", at = @At("TAIL"))
    private void factorySchematicFix$carryGraphThroughSafeNbt(
        CompoundTag nbt,
        HolderLookup.Provider registries,
        CallbackInfo ci
    ) {
        FactoryPanelBehaviour self = (FactoryPanelBehaviour) (Object) this;
        FactoryPanelBlockEntity blockEntity = self.panelBE();
        Level level = blockEntity.getLevel();

        if (!(level instanceof SchematicLevel))
            return;
        if (!(blockEntity instanceof FactoryPanelMetadataHolder holder))
            return;

        CompoundTag metadata = holder.factorySchematicFix$getMetadata();
        if (metadata == null || metadata.isEmpty())
            return;

        CompoundTag copy = metadata.copy();
        copy.putUUID("Instance", PrintInstanceIds.forLevel(level));
        nbt.put(GaugeGraphData.ROOT_TAG, copy);
    }
}
