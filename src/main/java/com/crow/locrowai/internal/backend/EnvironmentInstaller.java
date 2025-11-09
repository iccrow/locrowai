package com.crow.locrowai.internal.backend;

import com.crow.locrowai.api.registration.PackageManifest;
import com.crow.locrowai.api.registration.exceptions.MissingManifestException;
import com.crow.locrowai.api.registration.exceptions.MissingManifestSignatureException;
import com.crow.locrowai.api.registration.exceptions.MissingSecurityKeyException;
import com.crow.locrowai.internal.LocrowAI;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;


import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.concurrent.*;

class EnvironmentInstaller {

    private static PackageManifest man;

    static boolean installed() throws IOException {
        Path path = InstallationManager.getBackendPath();

        try {
            PackageManifest manifest = PackageManifest.fetch(path.resolve("core-manifest.json"), SecurityManager.OFFICIAL_KEY);
            return manifest.version.equals("0.4.0.dev1");
        } catch (MissingManifestException | MissingManifestSignatureException | MissingSecurityKeyException | SecurityException e) {
            e.printStackTrace();
            return false;
        }
    }


    static void download() throws IOException {
        Path backend = InstallationManager.getBackendPath();
        Path tar  = backend.resolve("python.tar.gz");

        String url = URIBuilder.buildPythonUrl();
        Files.createDirectories(backend);

        InstallationManager.logMessage("Downloading Python package from " + url + " to " + backend);

        // Download with timeouts (ms): connect=10s, read=10min
        FileUtils.copyURLToFile(new URL(url), tar.toFile(), 10_000, 600_000);
        InstallationManager.stagePercent.set(50);

        InstallationManager.logMessage("Reading latest backend core manifest...");

        Path core = Path.of(LocrowAI.MODID).resolve("core");

        man = PackageManifest.fetch(LocrowAI.class.getClassLoader(), core.resolve("core-manifest.json"));

        InstallationManager.logMessage("Copying core files from JAR resources...");

        List<String> files = new ArrayList<>(man.hashes.keySet());

        files.add("core-manifest.json");
        files.add("core-manifest.json.sig.b64");

        InstallationManager.copyFilesFromResources(
                LocrowAI.class.getClassLoader(),
                core,
                backend,
                files
        );
    }

    static void install() throws IOException, InterruptedException {
        Path backend = InstallationManager.getBackendPath();
        Path tar  = backend.resolve("python.tar.gz");

        InstallationManager.logMessage("Unpacking Python package...");

        untarGzFile(tar, backend);
        Files.deleteIfExists(tar);
        InstallationManager.stagePercent.set(20);

        InstallationManager.logMessage("Setting up Python virtual environment...");

        Path exe = backend.resolve("python").resolve("python.exe");
        ProcessBuilder venvBuilder = new ProcessBuilder(exe.toString(), "-m", "venv", "venv")
                .directory(backend.toFile());

        Process venvProcess = venvBuilder.start();

        InstallationManager.logMessage("Set up Python virtual environment with exit code " + venvProcess.waitFor() + ".");
        InstallationManager.stagePercent.set(60);

        InstallationManager.logMessage("Installing Python libraries...");

        InstallationManager.installPythonLibraries(man.requirements, 40.0 / man.requirements.size());

        while (InstallationManager.stagePercent.get() + 1e-6 < 100) {
            Thread.sleep(50);
        }
    }

    static void verify() throws IOException {
        Path backend = InstallationManager.getBackendPath();
        PackageManifest manifest = PackageManifest.fetch(
                backend.resolve("core-manifest.json"),
                SecurityManager.OFFICIAL_KEY
        );

        PackageManifest pythonManifest = PackageManifest.fetch(
                LocrowAI.class.getClassLoader(),
                Path.of(
                        String.format(
                                "%s/python/cpython-%s-manifest.json",
                                LocrowAI.MODID,
                                URIBuilder.getPythonTags()
                        )
                )
        );

        // Use a fixed thread pool
        int threads = Math.min(Runtime.getRuntime().availableProcessors(), 4);
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        try {
            // Submit verification tasks for core manifest
            List<Callable<Void>> tasks = new ArrayList<>();
            for (String name : manifest.hashes.keySet()) {
                Path path = backend.resolve(name);
                String expectedHash = manifest.hashes.get(name);

                tasks.add(() -> {
                    if (InstallationManager.hadError.get()) return null;
                    if (!Files.exists(path) || (expectedHash != null && !SecurityManager.verifyHash(path, expectedHash))) {
                        InstallationManager.logMessage("Error verifying file: 'core/" + name + "'. File may be missing or have been tampered with.");
                        InstallationManager.hadError.set(true);
                    }
                    return null;
                });
            }

            // Submit verification tasks for python manifest
            for (String name : pythonManifest.hashes.keySet()) {
                Path path = backend.resolve("python").resolve(name);
                String expectedHash = pythonManifest.hashes.get(name);

                tasks.add(() -> {
                    if (InstallationManager.hadError.get()) return null;
                    if (!Files.exists(path) || (expectedHash != null && !SecurityManager.verifyHash(path, expectedHash))) {
                        InstallationManager.logMessage("Error verifying file: 'python/" + name + "'. File may be missing or have been tampered with.");
                        InstallationManager.hadError.set(true);
                    }
                    return null;
                });
            }

            // Execute all tasks in parallel
            List<Future<Void>> futures = executor.invokeAll(tasks);

            // Optional: check for exceptions
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

    /** Untar a .tar.gz archive safely (prevents tar-slip) and preserves executable bit when possible. */
    static void untarGzFile(Path tarGzPath, Path destDir) throws IOException {
        Files.createDirectories(destDir);
        InstallationManager.logMessage("Extracting .tar.gz package: " + tarGzPath);

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
                            InstallationManager.logMessage("Skipping symlink that would escape destination: " + entryName + " -> " + linkName);
                        } else {
                            // Ensure parent exists
                            Files.createDirectories(out.getParent());
                            try {
                                Files.createSymbolicLink(out, destDir.relativize(linkTarget));
                            } catch (UnsupportedOperationException | IOException ex) {
                                InstallationManager.logMessage("Could not create symlink for " + entryName + ": " + ex.getMessage());
                            }
                        }
                    } catch (Exception ex) {
                        InstallationManager.logMessage("Failed to process symlink " + entryName + ": " + ex.getMessage());
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
    static Set<PosixFilePermission> modeToPosix(int mode) {
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
    static Path safeResolve(Path destDir, String entryName) throws IOException {
        Path target = destDir.resolve(entryName).normalize();
        if (!target.startsWith(destDir.normalize())) {
            throw new IOException("Blocked archive entry escaping target dir: " + entryName);
        }
        return target;
    }
}
