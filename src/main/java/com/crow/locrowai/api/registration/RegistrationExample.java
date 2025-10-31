package com.crow.locrowai.api.registration;

import com.crow.locrowai.api.AIContext;
import net.minecraft.resources.ResourceLocation;

@LocrowAIPlugin
public class RegistrationExample implements AIPlugin {

    public static AIContext context;

    @Override
    public void register(AIContext context) {
        RegistrationExample.context = context;

        // Used for registering a new extension
        context.registerExtension(ResourceLocation.fromNamespaceAndPath("locrowai", "locrowai/extensions/llm"));

        // Used for declaring usage of extensions registered in other mods
        context.declareExtension("audio");
    }
}
