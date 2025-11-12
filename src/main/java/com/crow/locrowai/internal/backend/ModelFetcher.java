package com.crow.locrowai.internal.backend;

import com.crow.locrowai.api.registration.PackageManifest;
import com.google.common.util.concurrent.AtomicDouble;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.ApiStatus;

import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@ApiStatus.Internal
public class ModelFetcher {
    static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static boolean isFetched(PackageManifest.ModelCard model) {
        String[] segments = model.filename.split("/");
        return Files.exists(InstallationManager.getBackendPath().resolve(model.model_folder).resolve(segments[segments.length - 1] + ".json"));
    }
    static void fetch(PackageManifest.ModelCard model, double delta) throws IOException {
        InstallationManager.logMessage("Downloading model '" + model.filename + "' from '" + model.repo + "'.");

        String[] segments = model.repo.split("/");
        StringBuilder encodedPath = new StringBuilder();
        for (String segment : segments) {
            encodedPath.append("/").append(URLEncoder.encode(segment, StandardCharsets.UTF_8));
        }
        String repo = encodedPath.toString().replace("+", "%20").replace("%28", "(").replace("%29", ")");

        System.out.println("RESOLVE: " + model.revision);
        String revision = URLEncoder.encode(model.revision, StandardCharsets.UTF_8);

        segments = model.filename.split("/");
        encodedPath = new StringBuilder();
        for (String segment : segments) {
            encodedPath.append("/").append(URLEncoder.encode(segment, StandardCharsets.UTF_8));
        }
        String filename = encodedPath.toString().replace("+", "%20").replace("%28", "(").replace("%29", ")");

        URL url = new URL("https://huggingface.co" + repo + "/resolve/" + revision + filename);
        InstallationManager.logMessage("Querying " + url);
        String localName = model.rename != null ? model.rename : model.filename;
        Path modelFolder = InstallationManager.getBackendPath().resolve(model.model_folder);
        Path modelPath = modelFolder.resolve(localName);

        Files.createDirectories(modelFolder);
        FileUtils.copyURLToFile(url, modelPath.toFile(), 10_000, 600_000);
        InstallationManager.logMessage("Downloaded model '" + model.filename + "' from '" + model.repo + "'.");

        InstallationManager.stagePercent.addAndGet(delta);

        if (model.extract_options.enabled) {
            if (model.filename.endsWith(".zip")) {
                ExtractUtils.unzipFile(modelPath, modelFolder);
                InstallationManager.logMessage("Extracting zip model '" + model.filename + "'!");
            } else if (model.filename.endsWith(".tar.gz")) {
                ExtractUtils.untarGzFile(modelPath, modelFolder);
                InstallationManager.logMessage("Extracting tar model '" + model.filename + "'!");
            } else {
                InstallationManager.logMessage("Unsupported archive type!");
            }
            Files.deleteIfExists(modelPath);
            InstallationManager.logMessage("Model extracted!");

            Map<String,String> renames = model.extract_options.renames;

            List<Map.Entry<String,String>> entries = new ArrayList<>(renames.entrySet());
            entries.sort(Comparator.comparingInt(e -> -modelFolder.resolve(e.getKey()).getNameCount()));

            for (Map.Entry<String, String> e : entries) {
                Path src = modelFolder.resolve(e.getKey());
                Path tgt = src.resolveSibling(e.getValue());
                try {
                    if (!Files.exists(src)) {
                        InstallationManager.logMessage("Source not found, skipping rename: " + src);
                        continue;
                    }
                    Files.createDirectories(tgt.getParent());
                    Files.move(src, tgt, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ex) {
                    InstallationManager.logMessage("Failed to rename '" + e.getKey() + "' -> '" + e.getValue() + "' : " + ex);
                }
            }

        }

        FileWriter writer = new FileWriter(modelFolder.resolve(segments[segments.length - 1] + ".json").toFile());
        gson.toJson(model, writer);
        writer.close();
        InstallationManager.logMessage("Saved model card to '" + segments[segments.length - 1] + "'.");
        InstallationManager.stagePercent.addAndGet(delta);
    }

    static void fetch(List<PackageManifest.ModelCard> modelCards) {
        int threads = Math.min(Runtime.getRuntime().availableProcessors(), 4);
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        List<Callable<Void>> tasks = new ArrayList<>();
        AtomicDouble delta = new AtomicDouble();
        for (PackageManifest.ModelCard model : modelCards) {
            if (!isFetched(model))
                tasks.add(() -> {
                    fetch(model, delta.get());
                    return null;
                });
        }

        if (tasks.isEmpty()) {
            executor.shutdown();
            return;
        }

        delta.set(50.0 / tasks.size());

        try {
            List<Future<Void>> futures = executor.invokeAll(tasks);

            for (Future<Void> f : futures) {
                try {
                    f.get();
                } catch (ExecutionException e) {
                    e.getCause().printStackTrace();
                    InstallationManager.logMessage(e.getMessage());
                }
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            executor.shutdown();
        }
    }
}
