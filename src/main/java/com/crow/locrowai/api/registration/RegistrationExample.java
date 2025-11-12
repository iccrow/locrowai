package com.crow.locrowai.api.registration;

import com.crow.locrowai.api.AIContext;
import com.crow.locrowai.internal.LocrowAI;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;
import java.util.Map;

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
        context.registerExtension(Path.of(LocrowAI.MODID, "extensions/llm/"));
        context.registerExtension(Path.of(LocrowAI.MODID, "extensions/math/"));
        context.registerExtension(Path.of(LocrowAI.MODID, "extensions/rvc/"));
        context.registerExtension(Path.of(LocrowAI.MODID, "extensions/tensor/"));
        context.registerExtension(Path.of(LocrowAI.MODID, "extensions/tts/"));
        context.registerExtension(Path.of(LocrowAI.MODID, "extensions/utils/"));
        context.registerExtension(Path.of(LocrowAI.MODID, "extensions/whisper/"));

        // Used for declaring usage of registered extensions (both registered in the current mod or other mods).
        // Registered extensions must be declared by at least one hooked mod to be installed.
//        context.declareExtension("audio");
//        context.declareExtension("base64");
//        context.declareExtension("llm");
//        context.declareExtension("math");
//        context.declareExtension("rvc");
//        context.declareExtension("tensor");
//        context.declareExtension("tts");
//        context.declareExtension("utils");
//        context.declareExtension("whisper");

        // Used for registering new models.
//        context.registerModel(
//                PackageManifest.ModelCard.of(
//                        "Linkario/The-AI-Bakery-Collection" // repo
//                )
//                .revision(
//                        "main" // revision (defaults to main)
//                )
//                .filename(
//                        "Villager (Minecraft).zip" // file within repo
//                )
//                .modelFolder(
//                        "extensions/rvc/models" // output folder
//                )
//                .rename(
//                        "villager.zip" // filename used on local machine
//                )
//                .extract(
//                        Map.of( // rename map of <file path> : <new filename>
//                                "Villager (Minecraft)", "villager",
//                                "Villager (Minecraft)/villagerminecraft.pth", "model.pth",
//                                "Villager (Minecraft)/added_IVF99_Flat_nprobe_1_villagerminecraft_v2.index", "model.index"
//                        )
//                )
//        );

        RegistrationExample.context = null;
    }
}
