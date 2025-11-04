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
        context.registerExtension(ResourceLocation.fromNamespaceAndPath("locrowai", "extensions/audio/"));
        context.registerExtension(ResourceLocation.fromNamespaceAndPath("locrowai", "extensions/base64/"));
        context.registerExtension(ResourceLocation.fromNamespaceAndPath("locrowai", "extensions/llm/"));
        context.registerExtension(ResourceLocation.fromNamespaceAndPath("locrowai", "extensions/math/"));
        context.registerExtension(ResourceLocation.fromNamespaceAndPath("locrowai", "extensions/rvc/"));
        context.registerExtension(ResourceLocation.fromNamespaceAndPath("locrowai", "extensions/tensor/"));
        context.registerExtension(ResourceLocation.fromNamespaceAndPath("locrowai", "extensions/tts/"));
        context.registerExtension(ResourceLocation.fromNamespaceAndPath("locrowai", "extensions/utils/"));
        context.registerExtension(ResourceLocation.fromNamespaceAndPath("locrowai", "extensions/whisper/"));

        // Used for declaring usage of extensions registered in other mods
        context.declareExtension("utils");
    }
}
