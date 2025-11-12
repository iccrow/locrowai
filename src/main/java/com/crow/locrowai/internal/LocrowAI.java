package com.crow.locrowai.internal;

import com.mojang.logging.LogUtils;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@ApiStatus.Internal
@Mod(LocrowAI.MODID)
public class LocrowAI
{
    // Define mod id in a common place for everything to reference
    public static final String MODID = "locrowai";

    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();

    public LocrowAI(FMLJavaModLoadingContext context)
    {
        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        if (FMLEnvironment.dist.isClient()) {
            context.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new ConfigScreenHandler.ConfigScreenFactory((mc, prevScreen) -> new AIBackendManagerScreen(prevScreen)));
        }
    }

    public static Logger LOGGER() {
        return LOGGER;
    }
}
