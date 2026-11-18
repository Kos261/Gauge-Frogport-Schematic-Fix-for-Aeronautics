package pl.konstanty.factoryschematicfix.mixin;

import com.simibubi.create.content.logistics.packagePort.PackagePortTarget;
import com.simibubi.create.content.logistics.packagePort.frogport.FrogportBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.createmod.catnip.codecs.CatnipCodecUtils;
import net.createmod.catnip.levelWrappers.SchematicLevel;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.konstanty.factoryschematicfix.FrogportGraphData;
import pl.konstanty.factoryschematicfix.PrintInstanceIds;
import pl.konstanty.factoryschematicfix.access.FrogportSchematicMetadataHolder;

@Mixin(SmartBlockEntity.class)
public abstract class SmartBlockEntityMixin {
    /**
     * Preserve Frogport user configuration but not Inventory.
     *
     * New schematics use FrogportGraphData rather than copying the raw Target,
     * because raw relative vectors are exactly what breaks after rotation.
     * Old/unannotated schematics keep the legacy Target fallback.
     */
    @Inject(method = "writeSafe", at = @At("TAIL"))
    private void factorySchematicFix$preserveFrogportConfiguration(
        CompoundTag tag,
        HolderLookup.Provider registries,
        CallbackInfo ci
    ) {
        if (!((Object) this instanceof FrogportBlockEntity frogport))
            return;

        tag.putString("AddressFilter", frogport.addressFilter);
        tag.putBoolean("AcceptsPackages", frogport.acceptsPackages);

        Level level = frogport.getLevel();
        if (level instanceof SchematicLevel
            && frogport instanceof FrogportSchematicMetadataHolder holder) {
            CompoundTag metadata = holder.factorySchematicFix$getFrogportMetadata();
            if (metadata != null
                && !metadata.isEmpty()
                && metadata.getInt("Version") == FrogportGraphData.VERSION) {
                CompoundTag copy = metadata.copy();
                copy.putUUID("Instance", PrintInstanceIds.forLevel(level));
                tag.put(FrogportGraphData.ROOT_TAG, copy);
                // Intentionally do not copy Target. It will be rebuilt from
                // transformed conveyor node positions after placement.
                return;
            }
        }

        if (frogport.target != null) {
            tag.put(
                "Target",
                CatnipCodecUtils.encode(PackagePortTarget.CODEC, registries, frogport.target).orElseThrow()
            );
        }
    }
}
