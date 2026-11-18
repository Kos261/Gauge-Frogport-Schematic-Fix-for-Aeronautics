package pl.konstanty.factoryschematicfix.mixin;

import com.simibubi.create.content.schematics.SchematicAndQuillItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.konstanty.factoryschematicfix.FrogportGraphData;
import pl.konstanty.factoryschematicfix.GaugeGraphData;

@Mixin(SchematicAndQuillItem.class)
public abstract class SchematicAndQuillItemMixin {
    @Inject(method = "clampGlueBoxes", at = @At("TAIL"))
    private static void factorySchematicFix$annotateGraphs(
        Level level,
        AABB selection,
        CompoundTag schematicNbt,
        CallbackInfo ci
    ) {
        GaugeGraphData.annotateExport(level, selection, schematicNbt);
        FrogportGraphData.annotateExport(level, selection, schematicNbt);
    }
}
