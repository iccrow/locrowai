package com.crow.locrowai.installer;

import com.crow.locrowai.config.Config;
import com.crow.locrowai.LocrowAI;
import com.crow.locrowai.loader.SetupToast;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;

import org.apache.commons.io.FileUtils;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.io.IOUtils;


import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Objects;
import java.util.Set;

import static com.crow.locrowai.LocrowAI.*;
import static com.crow.locrowai.LocrowAI.LOGGER;
import static com.crow.locrowai.installer.InstallationManager.*;

public class EnvironmentInstaller {

    private static final int DOWNLOAD_INDEX = 0;

    private static final int SETUP_INDEX = 1;
    private static final int SETUP_STEPS = 3;

    private static final int DEPENDENCIES_INDEX = 2;
    private static final int DEPENDENCIES_STEPS = 474;

    private static final int INSTALLING_INDEX = 3;
    private static final int INSTALLING_STEPS = 246;

    private static final int VERIFYING_INDEX = 4;

    private static final int DONE_INDEX = 5;

    public static boolean installed() throws IOException {
        Path root = FMLPaths.GAMEDIR.get().resolve(MODID);

        if (!Files.exists(root)) {
            Files.createDirectory(root);
            return false;
        }
        Path pyapp = root.resolve(PY_VERSION());

        return Files.exists(pyapp);
    }


    public static void downloadAndUnzip(String url, Path gameDir) throws IOException {
        Path base = gameDir.resolve(MODID).resolve(PY_VERSION());
        Path zip  = base.resolve("pyenv.zip");
        Files.createDirectories(base);

        LocrowAI.LOGGER().info("Downloading PyEnv package from " + url + " to " + base);

        // Download with timeouts (ms): connect=10s, read=10min
        FileUtils.copyURLToFile(new URL(url), zip.toFile(), 10_000, 600_000);

        unzipZipFile(zip, base);

        // Optional: clean up
        Files.deleteIfExists(zip);
    }

    /** Uses Commons Compress ZipFile (handles ZIP64, good perf). */
    public static void unzipZipFile(Path zipPath, Path destDir) throws IOException {
        Files.createDirectories(destDir);
        LocrowAI.LOGGER().info("Unzipping PyEnv package");
        try (ZipFile zf = new ZipFile(zipPath.toFile(), StandardCharsets.UTF_8.name(), true)) {
            var entries = zf.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry e = entries.nextElement();
                Path out = safeResolve(destDir, e.getName());

                if (e.isDirectory()) {
                    Files.createDirectories(out);
                    continue;
                }

                Files.createDirectories(out.getParent());
                try (InputStream in = zf.getInputStream(e);
                     OutputStream os = Files.newOutputStream(out,
                             StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    IOUtils.copy(in, os);
                }

                // Restore executable bit if present in archive (Unix only)
                if ((e.getUnixMode() & 0100) != 0) { // Owner execute bit
                    try {
                        Set<PosixFilePermission> perms = Files.getPosixFilePermissions(out);
                        perms.add(PosixFilePermission.OWNER_EXECUTE);
                        Files.setPosixFilePermissions(out, perms);
                    } catch (UnsupportedOperationException ignored) {
                        // Not a POSIX filesystem (likely Windows) — ignore
                    }
                }
            }
        }
    }

    /** Prevents zip-slip (../../) */
    private static Path safeResolve(Path destDir, String entryName) throws IOException {
        Path target = destDir.resolve(entryName).normalize();
        if (!target.startsWith(destDir)) {
            throw new IOException("Blocked zip entry escaping target dir: " + entryName);
        }
        return target;
    }

    public static void install(String base, SystemProbe.ProbeResult probeResult) {
        try {
            if (!EnvironmentInstaller.installed()) {
                if (Config.offloading)
                    return;

                Path pyPath = FMLPaths.GAMEDIR.get().resolve(MODID).resolve(PY_VERSION());
                if (!Files.exists(pyPath))
                    Files.createDirectory(pyPath);


                if (PY_TEMPLATE.equals(PY_VERSION())) return;

                installing.set(true);
                hadError.set(false);

                File modFolder = FMLPaths.GAMEDIR.get().resolve(MODID).toFile();

                for (File version : Objects.requireNonNull(modFolder.listFiles((dir, name) -> !name.equals(PY_VERSION())))) {
                    LOGGER().info("Uninstalling old version: {}", version.getName());
                    FileUtils.deleteDirectory(version);
                }

                LOGGER().info("Downloading build");
                currentStageIndex.set(DOWNLOAD_INDEX);
                stagePercent.set(0);

                String uri = URIBuilder.pythonBuildUrl(base, probeResult);
                LOGGER().info("Package location: " + uri);
                EnvironmentInstaller.downloadAndUnzip(uri, FMLPaths.GAMEDIR.get());

                currentStageIndex.set(SETUP_INDEX);
                stagePercent.set(0);
                LOGGER().info("Setting up virtual environment");
                // Build the process: use "cmd /c" to run a .bat file on Windows
                ProcessBuilder setupBuilder = new ProcessBuilder("cmd.exe", "/c", pyPath.resolve("setup.bat").toString());

                // Redirect stdout + stderr into the log file
                setupBuilder.directory(pyPath.toFile());
                setupBuilder.redirectErrorStream(true); // merge stderr into stdout
                setupBuilder.redirectOutput(ProcessBuilder.Redirect.PIPE);

                // Start and wait for exit

                Process setupProcess = setupBuilder.start();
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(setupProcess.getInputStream()));

                BufferedWriter logSetup = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(pyPath.resolve("setup_log.txt").toFile(), true), StandardCharsets.UTF_8));

                String line;
                while ((line = reader.readLine()) != null) {
                    logLine(line, logSetup);
                    switch (currentStageIndex.get()) {
                        case SETUP_INDEX -> {
                            if (line.contains("Setting up virtual environment") || line.contains("Upgrading pip")) {
                                stagePercent.addAndGet(100.0D / SETUP_STEPS);
                            } else if (line.contains("Downloading dependencies")) {
                                LOGGER().info("Downloading dependencies");
                                currentStageIndex.set(DEPENDENCIES_INDEX);
                                stagePercent.set(-100.0D / DEPENDENCIES_STEPS);
                            }
                        } case DEPENDENCIES_INDEX -> {
                            if (line.contains("Downloading")) {
                                stagePercent.addAndGet(100.0D / DEPENDENCIES_STEPS);
                            } else if (line.contains("Installing dependencies")) {
                                LOGGER().info("Installing packages");
                                currentStageIndex.set(INSTALLING_INDEX);
                                stagePercent.set(0);
                            }
                        } case INSTALLING_INDEX -> {
                            if (line.contains("Successfully installed")) {
                                stagePercent.addAndGet(100.0D / INSTALLING_STEPS);
                            } else if (line.contains("Running test")) {
                                LOGGER().info("Verifying install");
                                currentStageIndex.set(VERIFYING_INDEX);
                                stagePercent.set(0);
                            }
                        } case VERIFYING_INDEX -> {
                            if (line.contains("AI packages were successfully set up")) {
                                currentStageIndex.set(DONE_INDEX);
                                stagePercent.set(100);
                            }
                        }
                    }
                }
                logSetup.flush();
                logSetup.close();
                reader.close();

                int exitCode = setupProcess.waitFor();
                if (exitCode == 0) {
                    if (awaitingStartup.get()) {
                        outThread.start();
                        awaitingStartup.set(false);
                    }
                    installing.set(false);
                    if (FMLEnvironment.dist.isClient()) SetupToast.done();
                    LOGGER().info("PyEnv install is complete! Log saved to {}.", pyPath.resolve("setup_log.txt"));
                } else {
                    installing.set(false);
                    hadError.set(true);
                    LOGGER().info("PyEnv install encountered an error and exited with code {}! Log saved to {}.", exitCode, pyPath.resolve("setup_log.txt"));
                }
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static void logLine(String line, BufferedWriter logSetup) throws IOException {
        if (line == null) return;
        logSetup.write(line);
        logSetup.newLine();
        logSetup.flush();
    }
}
