package com.crow.locrowai.api.registration;

import com.crow.locrowai.api.AIContext;
import com.crow.locrowai.internal.LocrowAI;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;

@LocrowAIPlugin
public class RegistrationExample implements AIPlugin {

    public static AIContext context;

    @Override
    public void register(AIContext context) {
        // Save this so you can run AI scripts.
        RegistrationExample.context = context;

        // Used for registering a new extension.
        // If your extension has not been officially signed, you must add an argument for your third-party security key.
        context.registerExtension(Path.of(LocrowAI.MODID, "extensions/audio/"));
        context.registerExtension(Path.of(LocrowAI.MODID, "extensions/base64/"));
        context.registerExtension(Path.of(LocrowAI.MODID, "extensions/llm/"), Path.of(LocrowAI.MODID,"fake_key.pem"));
        context.registerExtension(Path.of(LocrowAI.MODID, "extensions/math/"));
        context.registerExtension(Path.of(LocrowAI.MODID, "extensions/rvc/"));
        context.registerExtension(Path.of(LocrowAI.MODID, "extensions/tensor/"));
        context.registerExtension(Path.of(LocrowAI.MODID, "extensions/tts/"));
        context.registerExtension(Path.of(LocrowAI.MODID, "extensions/utils/"));
        context.registerExtension(Path.of(LocrowAI.MODID, "extensions/whisper/"));

        // Used for declaring usage of registered extensions (both registered in the current mod or other mods).
        context.declareExtension("audio");
        context.declareExtension("base64");
        context.declareExtension("llm");
        context.declareExtension("math");
        context.declareExtension("rvc");
        context.declareExtension("tensor");
        context.declareExtension("tts");
        context.declareExtension("utils");
        context.declareExtension("whisper");

        RegistrationExample.context = null;
    }
}
