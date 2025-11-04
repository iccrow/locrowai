package com.crow.locrowai.installer;

import com.crow.locrowai.LocrowAI;
import com.crow.locrowai.api.registration.AIExtension;
import com.crow.locrowai.api.registration.PackageManifest;
import com.crow.locrowai.api.registration.exceptions.UnsupportedRequirementException;
import net.minecraft.resources.ResourceLocation;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class ExtensionInstaller {

    public static void install(AIExtension extension, ClassLoader loader, int total) throws IOException, InterruptedException {
        if (extension.installed()) {
            InstallationManager.stagePercent.addAndGet(100.0 * extension.getRequirements().size() / total);
            return;
        };

        ResourceLocation loc = extension.getLoc();

        Path ePath = InstallationManager.getInstallPath().resolve("extensions").resolve(extension.getId());
        String locStr = '/' + LocrowAI.MODID + '/' + loc.getPath() + '/';

        List<String> files = new ArrayList<>(extension.getFiles().stream().toList());

        files.add("manifest.json");
        files.add("manifest.json.sig.b64");

        if (!Files.exists(ePath))
            Files.createDirectory(ePath);

        for (String file : files) {
            InputStream stream = loader.getResourceAsStream( locStr + file);
            if (stream == null) throw new MissingResourceException("Missing file: " + file, "ExtensionInstaller", file);

            Path out = ePath.resolve(file);
            Files.copy(stream, out, StandardCopyOption.REPLACE_EXISTING);
            stream.close();
        }


        for (PackageManifest.ModelCard model : extension.getModels()) {
            URL url = new URL("https://huggingface.co/" + model.repo + "/resolve/" + model.revision + "/" + model.filename);

            Path modelFolder = ePath.resolve(model.model_folder);

            FileUtils.copyURLToFile(url, modelFolder.toFile(), 10_000, 600_000);
        }


        for (String requirement : extension.getRequirements()) {
            String pip = InstallationManager.getLib(requirement);
            if (pip == null) {
                LocrowAI.LOGGER().error(requirement);
                throw new UnsupportedRequirementException(requirement);
            }
            pip = pip.replace("+xxxxx", "+cu121");

            if (InstallationManager.handleLib(pip) ||
                    InstallationManager.libInstalled(requirement)) {
                InstallationManager.stagePercent.addAndGet(100.0 / total);
                continue;
            }

            LocrowAI.LOGGER().info(pip);

            ProcessBuilder builder = InstallationManager.buildScriptProcess(
                    InstallationManager.getInstallPath(),
                    "python", "-m", "pip", "install", pip,
                    "--index-url", InstallationManager.CORE_INDEX,
                    "--extra-index-url", InstallationManager.TORCH_INDICES.get("cuda"),
                    "--extra-index-url", InstallationManager.PYPI_INDEX,
                    "--progress-bar", "off", "--no-deps", "--no-cache-dir", "--disable-pip-version-check"
            );

            builder.redirectErrorStream(true);

            System.out.println(builder.command());

            InstallationManager.queueLib(builder);

            Process downloader = builder.start();
//            LocrowAI.LOGGER().info("running tasks");

            CompletableFuture<Process> future = downloader.onExit();
            future.thenAccept(process -> {
                InstallationManager.removeQueuedLib(builder);
                InstallationManager.stagePercent.addAndGet(100.0 / total);
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream())
                )) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\n");
                    }

                    LocrowAI.LOGGER().info(sb.toString().trim());
                } catch (IOException e) {

                }
            }).exceptionally(err -> {
                InstallationManager.removeQueuedLib(builder);
                InstallationManager.stagePercent.addAndGet(100.0 / total);
                InstallationManager.hadError.set(true);

                LocrowAI.LOGGER().info("error oops");
                err.printStackTrace();
                return null;
            });
        }
    }

    public static void waitForInstalls() throws InterruptedException {
        while (InstallationManager.stagePercent.get() + 1e-6 < 100) {
            Thread.sleep(50);
        }
    }

    public static void verify() throws IOException {
//        verify python dist
//        verify install path code

        Path loc = InstallationManager.getInstallPath().resolve("extensions");

        DirectoryStream<Path> stream = Files.newDirectoryStream(loc);

        for (Path ext : stream) {
            if (Files.isDirectory(ext)) {
//                verify signatures and hashes for each extension
            }
        }
    }
}
