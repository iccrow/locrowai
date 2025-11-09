package com.crow.locrowai.internal.backend;

import com.crow.locrowai.api.registration.AIRegistry;
import com.crow.locrowai.internal.LocrowAI;
import com.crow.locrowai.api.registration.AIExtension;
import com.crow.locrowai.api.registration.PackageManifest;
import net.minecraft.resources.ResourceLocation;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

class ExtensionInstaller {

    static void install(AIExtension extension, ClassLoader loader, int total) throws IOException, InterruptedException {
        if (extension.installed()) {
            InstallationManager.logMessage("Skipping extension '" + extension.getId() + "' as it is already installed!");
            InstallationManager.stagePercent.addAndGet(100.0 * extension.getRequirements().size() / total);
            return;
        };

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

        for (PackageManifest.ModelCard model : extension.getModels()) {
            InstallationManager.logMessage("Downloading model '" + model.filename + "' from '" + model.repo + "'.");
            URL url = new URL("https://huggingface.co/" + model.repo + "/resolve/" + model.revision + "/" + model.filename);

            Path modelFolder = ePath.resolve(model.model_folder);

            FileUtils.copyURLToFile(url, modelFolder.toFile(), 10_000, 600_000);
            InstallationManager.logMessage("Downloaded model '" + model.filename + "' from '" + model.repo + "'.");
        }

        InstallationManager.logMessage("Installing extension '" + extension.getId() + "' required Python libraries...");
        InstallationManager.installPythonLibraries(extension.getRequirements(), 100.0 / total);
    }

    static void waitForInstalls() throws InterruptedException {
        while (InstallationManager.stagePercent.get() + 1e-6 < 100) {
            Thread.sleep(50);
        }
    }

    static void verify() throws IOException {
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
                    InstallationManager.hadError.set(true);
                    continue;
                }

                PackageManifest manifest = PackageManifest.fetch(ext.resolve("manifest.json"), AIRegistry.getExtension(name).getKey());

                for (String file : manifest.hashes.keySet()) {
                    Path path = ext.resolve(file);
                    String expectedHash = manifest.hashes.get(file);

                    // Add verification task for each file
                    tasks.add(() -> {
                        if (InstallationManager.hadError.get()) return null;
                        if (!Files.exists(path) || (expectedHash != null && !SecurityManager.verifyHash(path, expectedHash))) {
                            InstallationManager.logMessage("Error verifying file: 'extensions/" + name + "/" + file + "'. File may be missing or have been tampered with.");
                            InstallationManager.hadError.set(true);
                        }
                        return null;
                    });
                }
            }
        }

        try {
            // Run all tasks in parallel
            List<Future<Void>> futures = executor.invokeAll(tasks);

            // Optional: handle exceptions
            for (Future<Void> f : futures) {
                try {
                    f.get();
                } catch (ExecutionException e) {
                    e.getCause().printStackTrace();
                    InstallationManager.hadError.set(true);
                }
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            executor.shutdown();
        }
    }
}
