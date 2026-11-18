package pl.konstanty.factoryschematicfix;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(FactorySchematicFix.MOD_ID)
public final class FactorySchematicFix {
    public static final String MOD_ID = "factory_schematic_fix";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FactorySchematicFix() {
        LOGGER.info("Create Factory Schematic Fix loaded");
    }
}
