package com.crow.locrowai.installer;

import com.crow.locrowai.api.registration.PackageManifest;
import com.crow.locrowai.api.registration.exceptions.MissingManifestException;
import com.crow.locrowai.LocrowAI;
import com.crow.locrowai.api.registration.exceptions.UnsupportedRequirementException;
import com.google.gson.Gson;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;


import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class EnvironmentInstaller {

    private static final Gson gson = new Gson();

    private static final Logger LOG = LocrowAI.LOGGER();

    private static PackageManifest man;

    public static boolean installed() throws IOException {
        Path path = InstallationManager.getInstallPath();

        File manifest = path.resolve("core-manifest.json").toFile();

        if (!manifest.exists()) return false;

        BufferedReader reader = Files.newBufferedReader(manifest.toPath());

        return gson.fromJson(reader, PackageManifest.class).version.equals("0.4.0.dev1");
    }


    public static void download() throws IOException {
        Path backend = InstallationManager.getInstallPath();
        Path tar  = backend.resolve("python.tar.gz");

        String url = URIBuilder.buildPythonUrl();
        Files.createDirectories(backend);

        LocrowAI.LOGGER().info("Downloading Python package from " + url + " to " + backend);

        // Download with timeouts (ms): connect=10s, read=10min
        FileUtils.copyURLToFile(new URL(url), tar.toFile(), 10_000, 600_000);
        InstallationManager.stagePercent.set(50);


        String locStr = '/' + LocrowAI.MODID + "/core/";

        InputStream manStream = LocrowAI.class.getClassLoader().getResourceAsStream(locStr + "core-manifest.json");
        if (manStream == null) throw new MissingManifestException(locStr + "core-manifest.json", "none");
        BufferedReader manReader = new BufferedReader(new InputStreamReader(manStream));

        man = gson.fromJson(manReader, PackageManifest.class);
        manReader.close();
        manStream.close();


        List<String> files = new ArrayList<>(man.hashes.keySet());

        files.add("core-manifest.json");
        files.add("core-manifest.json.sig.b64");

        for (String file : files) {
            InputStream stream = LocrowAI.class.getClassLoader().getResourceAsStream( locStr + file);
            if (stream == null) throw new MissingResourceException("Missing file: " + file, "ExtensionInstaller", file);

            Path out = backend.resolve(file);
            Files.copy(stream, out, StandardCopyOption.REPLACE_EXISTING);
            stream.close();
        }
    }

    public static void install() throws IOException, InterruptedException {
        Path backend = InstallationManager.getInstallPath();
        Path tar  = backend.resolve("python.tar.gz");

        untarGzFile(tar, backend);
        Files.deleteIfExists(tar);
        InstallationManager.stagePercent.set(20);


        Path exe = backend.resolve("python").resolve("python.exe");
        ProcessBuilder venvBuilder = new ProcessBuilder(exe.toString(), "-m", "venv", "venv")
                .directory(backend.toFile());

        Process venvProcess = venvBuilder.start();

        venvProcess.waitFor();
        InstallationManager.stagePercent.set(60);


        int total = man.requirements.size();

        for (String requirement : man.requirements) {
            String pip = InstallationManager.getLib(requirement);
            if (pip == null) {
                LocrowAI.LOGGER().error(requirement);
                throw new UnsupportedRequirementException(requirement);
            }
            pip = pip.replace("+xxxxx", "+cu121");

            if (InstallationManager.handleLib(pip) ||
                    InstallationManager.libInstalled(requirement)) {
                InstallationManager.stagePercent.addAndGet(40.0 / total);
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
                InstallationManager.stagePercent.addAndGet(40.0 / total);
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
                InstallationManager.stagePercent.addAndGet(40.0 / total);
                InstallationManager.hadError.set(true);

                LocrowAI.LOGGER().info("error oops");
                err.printStackTrace();
                return null;
            });
        }

        while (InstallationManager.stagePercent.get() + 1e-6 < 100) {
            Thread.sleep(50);
        }
    }

    /** Untar a .tar.gz archive safely (prevents tar-slip) and preserves executable bit when possible. */
    public static void untarGzFile(Path tarGzPath, Path destDir) throws IOException {
        Files.createDirectories(destDir);
        LOG.info("Extracting .tar.gz package: " + tarGzPath);

        try (InputStream fis = Files.newInputStream(tarGzPath);
             GzipCompressorInputStream gis = new GzipCompressorInputStream(fis);
             TarArchiveInputStream tis = new TarArchiveInputStream(gis)) {

            TarArchiveEntry entry;
            while ((entry = tis.getNextTarEntry()) != null) {
                String entryName = entry.getName();
                Path out = safeResolve(destDir, entryName);

                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                    continue;
                }

                // Handle symbolic links
                if (entry.isSymbolicLink()) {
                    String linkName = entry.getLinkName(); // target of symlink as stored in archive
                    try {
                        Path linkTarget = Path.of(linkName);
                        // if linkTarget is relative, resolve against the entry's parent
                        if (!linkTarget.isAbsolute()) {
                            linkTarget = out.getParent().resolve(linkTarget).normalize();
                        }
                        // Prevent creating links that escape destDir
                        if (!linkTarget.startsWith(destDir)) {
                            LOG.warn("Skipping symlink that would escape destination: " + entryName + " -> " + linkName);
                        } else {
                            // Ensure parent exists
                            Files.createDirectories(out.getParent());
                            try {
                                Files.createSymbolicLink(out, destDir.relativize(linkTarget));
                            } catch (UnsupportedOperationException | IOException ex) {
                                LOG.warn("Could not create symlink for " + entryName + ": " + ex.getMessage());
                            }
                        }
                    } catch (Exception ex) {
                        LOG.warn("Failed to process symlink " + entryName + ": " + ex.getMessage());
                    }
                    continue;
                }

                // Regular file
                Files.createDirectories(out.getParent());
                try (OutputStream os = Files.newOutputStream(out,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    // TarArchiveInputStream will only provide the current entry's bytes
                    IOUtils.copy(tis, os);
                }

                // Try to restore POSIX permissions (executable bits etc.)
                int mode = entry.getMode(); // unix mode bits
                try {
                    Set<PosixFilePermission> perms = modeToPosix(mode);
                    if (!perms.isEmpty()) {
                        Files.setPosixFilePermissions(out, perms);
                    }
                } catch (UnsupportedOperationException ignored) {
                    // Filesystem does not support POSIX permissions (likely Windows) — ignore
                }
            }
        }
    }

    /** Convert unix mode bits (as returned by TarArchiveEntry.getMode()) into PosixFilePermission set. */
    private static Set<PosixFilePermission> modeToPosix(int mode) {
        Set<PosixFilePermission> perms = new HashSet<>();

        // Owner
        if ((mode & 0400) != 0) perms.add(PosixFilePermission.OWNER_READ);
        if ((mode & 0200) != 0) perms.add(PosixFilePermission.OWNER_WRITE);
        if ((mode & 0100) != 0) perms.add(PosixFilePermission.OWNER_EXECUTE);

        // Group
        if ((mode & 0040) != 0) perms.add(PosixFilePermission.GROUP_READ);
        if ((mode & 0020) != 0) perms.add(PosixFilePermission.GROUP_WRITE);
        if ((mode & 0010) != 0) perms.add(PosixFilePermission.GROUP_EXECUTE);

        // Others
        if ((mode & 0004) != 0) perms.add(PosixFilePermission.OTHERS_READ);
        if ((mode & 0002) != 0) perms.add(PosixFilePermission.OTHERS_WRITE);
        if ((mode & 0001) != 0) perms.add(PosixFilePermission.OTHERS_EXECUTE);

        return perms;
    }

    /** Prevents archive-slip (../ escaping) */
    private static Path safeResolve(Path destDir, String entryName) throws IOException {
        Path target = destDir.resolve(entryName).normalize();
        if (!target.startsWith(destDir.normalize())) {
            throw new IOException("Blocked archive entry escaping target dir: " + entryName);
        }
        return target;
    }

//    public static void install(String base, SystemProbe.ProbeResult probeResult) {
//        try {
//            if (!EnvironmentInstaller.installed()) {
//                if (Config.offloading)
//                    return;
//
//                Path pyPath = FMLPaths.GAMEDIR.get().resolve(MODID).resolve(PY_VERSION());
//                if (!Files.exists(pyPath))
//                    Files.createDirectory(pyPath);
//
//
//                if (PY_TEMPLATE.equals(PY_VERSION())) return;
//
//                installing.set(true);
//                hadError.set(false);
//
//                File modFolder = FMLPaths.GAMEDIR.get().resolve(MODID).toFile();
//
//                for (File version : Objects.requireNonNull(modFolder.listFiles((dir, name) -> !name.equals(PY_VERSION())))) {
//                    LOGGER().info("Uninstalling old version: {}", version.getName());
//                    FileUtils.deleteDirectory(version);
//                }
//
//                LOGGER().info("Downloading build");
//                currentStageIndex.set(DOWNLOAD_INDEX);
//                stagePercent.set(0);
//
//                String uri = URIBuilder.pythonBuildUrl(base, probeResult);
//                LOGGER().info("Package location: " + uri);
//                EnvironmentInstaller.downloadAndUnzip(uri, FMLPaths.GAMEDIR.get());
//
//                currentStageIndex.set(SETUP_INDEX);
//                stagePercent.set(0);
//                LOGGER().info("Setting up virtual environment");
//                // Build the process: use "cmd /c" to run a .bat file on Windows
//                ProcessBuilder setupBuilder = new ProcessBuilder("cmd.exe", "/c", pyPath.resolve("setup.bat").toString());
//
//                // Redirect stdout + stderr into the log file
//                setupBuilder.directory(pyPath.toFile());
//                setupBuilder.redirectErrorStream(true); // merge stderr into stdout
//                setupBuilder.redirectOutput(ProcessBuilder.Redirect.PIPE);
//
//                // Start and wait for exit
//
//                Process setupProcess = setupBuilder.start();
//                BufferedReader reader = new BufferedReader(
//                        new InputStreamReader(setupProcess.getInputStream()));
//
//                BufferedWriter logSetup = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(pyPath.resolve("setup_log.txt").toFile(), true), StandardCharsets.UTF_8));
//
//                String line;
//                while ((line = reader.readLine()) != null) {
//                    logLine(line, logSetup);
//                    switch (currentStageIndex.get()) {
//                        case SETUP_INDEX -> {
//                            if (line.contains("Setting up virtual environment") || line.contains("Upgrading pip")) {
//                                stagePercent.addAndGet(100.0D / SETUP_STEPS);
//                            } else if (line.contains("Downloading dependencies")) {
//                                LOGGER().info("Downloading dependencies");
//                                currentStageIndex.set(DEPENDENCIES_INDEX);
//                                stagePercent.set(-100.0D / DEPENDENCIES_STEPS);
//                            }
//                        } case DEPENDENCIES_INDEX -> {
//                            if (line.contains("Downloading")) {
//                                stagePercent.addAndGet(100.0D / DEPENDENCIES_STEPS);
//                            } else if (line.contains("Installing dependencies")) {
//                                LOGGER().info("Installing packages");
//                                currentStageIndex.set(INSTALLING_INDEX);
//                                stagePercent.set(0);
//                            }
//                        } case INSTALLING_INDEX -> {
//                            if (line.contains("Successfully installed")) {
//                                stagePercent.addAndGet(100.0D / INSTALLING_STEPS);
//                            } else if (line.contains("Running test")) {
//                                LOGGER().info("Verifying install");
//                                currentStageIndex.set(VERIFYING_INDEX);
//                                stagePercent.set(0);
//                            }
//                        } case VERIFYING_INDEX -> {
//                            if (line.contains("AI packages were successfully set up")) {
//                                currentStageIndex.set(DONE_INDEX);
//                                stagePercent.set(100);
//                            }
//                        }
//                    }
//                }
//                logSetup.flush();
//                logSetup.close();
//                reader.close();
//
//                int exitCode = setupProcess.waitFor();
//                if (exitCode == 0) {
//                    if (awaitingStartup.get()) {
//                        outThread.start();
//                        awaitingStartup.set(false);
//                    }
//                    installing.set(false);
//                    if (FMLEnvironment.dist.isClient()) SetupToast.done();
//                    LOGGER().info("PyEnv install is complete! Log saved to {}.", pyPath.resolve("setup_log.txt"));
//                } else {
//                    installing.set(false);
//                    hadError.set(true);
//                    LOGGER().info("PyEnv install encountered an error and exited with code {}! Log saved to {}.", exitCode, pyPath.resolve("setup_log.txt"));
//                }
//            }
//        } catch (IOException | InterruptedException e) {
//            throw new RuntimeException(e);
//        }
//    }

    private static void logLine(String line, BufferedWriter logSetup) throws IOException {
        if (line == null) return;
        logSetup.write(line);
        logSetup.newLine();
        logSetup.flush();
    }
}
