package com.crow.locrowai.internal.backend;

import com.crow.locrowai.api.registration.AIRegistry;
import com.crow.locrowai.internal.AIBackendManagerScreen;
import com.crow.locrowai.internal.LocrowAI;
import com.crow.locrowai.api.registration.AIExtension;
import com.crow.locrowai.api.registration.PackageManifest;
import com.google.common.util.concurrent.AtomicDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

class ExtensionInstaller {

    static AtomicBoolean allowThirdParty = new AtomicBoolean(false);
    static AtomicBoolean decidedThirdParty = new AtomicBoolean(false);

    static void install(AIExtension extension, ClassLoader loader, int total) throws IOException, InterruptedException {
        if (extension.installed()) {
            InstallationManager.logMessage("Skipping extension '" + extension.getId() + "' as it is already installed.");
            InstallationManager.stagePercent.addAndGet(100.0 * extension.getRequirements().size() / total);
            return;
        };

        if (!extension.getKey().equals(SecurityManager.OFFICIAL_KEY) && !allowThirdParty.get()) {
            DistExecutor.safeRunWhenOn(Dist.CLIENT, ExtensionInstaller::showThirdPartyWarning);
            if (!allowThirdParty.get()) {
                InstallationManager.logMessage("Skipping third party extension '" + extension.getId() + "' as the user declined third party extensions.");
                InstallationManager.stagePercent.addAndGet(100.0 * extension.getRequirements().size() / total);
                return;
            }
        }

        Path source = extension.getSource();

        Path ePath = InstallationManager.getBackendPath().resolve("extensions").resolve(extension.getId());

        List<String> files = new ArrayList<>(extension.getFiles().stream().toList());

        files.add("manifest.json");
        files.add("manifest.json.sig.b64");

        InstallationManager.logMessage("Copying extension '" + extension.getId() + "' files from '" + extension.getMODID() + "' JAR resources to disk.");
        InstallationManager.copyFilesFromResources(
                loader,
                source,
                ePath,
                files
        );
        InstallationManager.logMessage("Copied extension '" + extension.getId() + "' files to disk.");

        InstallationManager.logMessage("Installing extension '" + extension.getId() + "' required Python libraries...");
        InstallationManager.installPythonLibraries(extension.getRequirements(), 100.0 / total);
    }

    static void waitForInstalls() throws InterruptedException {
        while (InstallationManager.stagePercent.get() + 1e-6 < 100) {
            Thread.sleep(50);
        }
    }

    static boolean verify() throws IOException {
        AtomicBoolean success = new AtomicBoolean(true);
        AtomicDouble delta = new AtomicDouble(100);
        Path loc = InstallationManager.getBackendPath().resolve("extensions");
        Set<String> declared = AIRegistry.getDeclared();

        int threads = Math.min(Runtime.getRuntime().availableProcessors(), 4);
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        List<Callable<Void>> tasks = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(loc)) {
            for (Path ext : stream) {
                if (!Files.isDirectory(ext)) continue;

                String name = ext.getFileName().toString();
                if (!declared.contains(name)) {
                    InstallationManager.logMessage("Backend contains undeclared extension '" + name + "'. File may be malicious!");
                    success.set(false);
                    continue;
                }

                PackageManifest manifest = PackageManifest.fetch(ext.resolve("manifest.json"), AIRegistry.getExtension(name).getKey());

                for (String file : manifest.hashes.keySet()) {
                    Path path = ext.resolve(file);
                    String expectedHash = manifest.hashes.get(file);

                    // Add verification task for each file
                    tasks.add(() -> {
                        if (!success.get()) return null;
                        if (!Files.exists(path) || (expectedHash != null && !SecurityManager.verifyHash(path, expectedHash))) {
                            InstallationManager.logMessage("Error verifying file: 'extensions/" + name + "/" + file + "'. File may be missing or have been tampered with.");
                            success.set(false);
                        }
                        InstallationManager.stagePercent.addAndGet(delta.get());
                        return null;
                    });
                }
            }
        }

        if (tasks.isEmpty()) {
            executor.shutdown();
            return false;
        }

        delta.set(100.0 / tasks.size());

        try {
            // Run all tasks in parallel
            List<Future<Void>> futures = executor.invokeAll(tasks);

            // Optional: handle exceptions
            for (Future<Void> f : futures) {
                try {
                    f.get();
                } catch (ExecutionException e) {
                    e.getCause().printStackTrace();
                    InstallationManager.logMessage(e.getMessage());
                    success.set(false);
                }
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            executor.shutdown();
        }

        return success.get();
    }

    static DistExecutor.SafeRunnable showThirdPartyWarning() {
        return new DistExecutor.SafeRunnable() {
            @Override
            public void run() {
                if (decidedThirdParty.get()) return;

                if (!EventManager.screenReady.get()) {
                    while (!EventManager.screenReady.get()) {
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }

                Minecraft mc = Minecraft.getInstance();
                mc.execute(() -> {
                    Screen prevScreen = mc.screen;

                    String titleText = "Third Party AI Extensions Detected!";
                    int titleColor = 0xFFFF55;

                    Component message = Component.empty()
                            .append(Component.literal("Your mod pack uses unreviewed, third party AI extensions.\n\n")
                                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))))
                            .append(Component.literal("You can disable these extensions or proceed if you trust them.")
                                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xAAAAAA))));

                    mc.setScreen(
                            new ConfirmScreen(
                                    result -> {
                                        EventManager.awaitingThirdPartyWarning.set(false);
                                        decidedThirdParty.set(true);
                                        allowThirdParty.set(result);
                                        Minecraft.getInstance().setScreen(prevScreen);
                                    },
                                    Component.literal(titleText).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(titleColor))),
                                    message,
                                    Component.literal("Proceed Anyway"),
                                    Component.literal("Disable Extras")
                            )
                    );
                });
                EventManager.awaitingThirdPartyWarning.set(true);
                while (EventManager.awaitingThirdPartyWarning.get()) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        };
    }
}
