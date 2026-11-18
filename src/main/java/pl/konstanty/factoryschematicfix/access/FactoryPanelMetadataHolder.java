package pl.konstanty.factoryschematicfix.access;

import net.minecraft.nbt.CompoundTag;

public interface FactoryPanelMetadataHolder {
    CompoundTag factorySchematicFix$getMetadata();
    void factorySchematicFix$setMetadata(CompoundTag metadata);
}
