package com.crow.locrowai.internal.backend;

import com.crow.locrowai.api.registration.AIRegistry;
import com.crow.locrowai.api.registration.PackageManifest;
import com.crow.locrowai.api.registration.exceptions.MissingManifestException;
import com.crow.locrowai.api.registration.exceptions.MissingManifestSignatureException;
import com.crow.locrowai.api.registration.exceptions.MissingSecurityKeyException;
import com.crow.locrowai.internal.LocrowAI;

import com.google.common.util.concurrent.AtomicDouble;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

class EnvironmentInstaller {

    private static PackageManifest man;

    static boolean installed() throws IOException {
        Path path = InstallationManager.getBackendPath();

        try {
            PackageManifest manifest = PackageManifest.fetch(path.resolve("core-manifest.json"), SecurityManager.OFFICIAL_KEY);
            return manifest.version.equals("0.4.0");
        } catch (MissingManifestException | MissingManifestSignatureException | MissingSecurityKeyException | SecurityException e) {
//            e.printStackTrace();
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

        ExtractUtils.untarGzFile(tar, backend);
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

    static boolean verify() throws IOException {
        AtomicBoolean success = new AtomicBoolean(true);
        AtomicDouble delta = new AtomicDouble(100);

        Path backend = InstallationManager.getBackendPath();
        Set<String> wheels = Set.copyOf(Files.readAllLines(backend.resolve("wheels.txt")));
        if (InstallationManager.libCount() != wheels.size()) {
            InstallationManager.logMessage("Expected lib count of " + InstallationManager.libCount() + " does not match cached wheel count of " + wheels.size() + "!");
            return false;
        }
        Path sitePackages;
        Path scriptOrBin;
        if (SystemProbe.osKey(SystemProbe.result.os()).equals("windows")) {
            sitePackages = backend.resolve("venv").resolve("Lib").resolve("site-packages");
            scriptOrBin = backend.resolve("venv").resolve("Scripts");
        } else {
            sitePackages = backend.resolve("venv").resolve("lib").resolve("python3.10").resolve("site-packages");
            scriptOrBin = backend.resolve("venv").resolve("bin");
        }


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
            for (String wheel : wheels) {
                Path wheelPath = Path.of(LocrowAI.MODID, "python", "lib", wheel + "-manifest.json");
                PackageManifest wheelManifest = PackageManifest.fetch(LocrowAI.class.getClassLoader(), wheelPath);

                for (String name : wheelManifest.hashes.keySet()) {
                    Path path;
                    String expectedHash = wheelManifest.hashes.get(name);

                    if (name.contains(".data/purelib") || name.contains(".data/platlib")) {
                        String relative = name.split("\\.data/(?:purelib|platlib)/")[1];
                        path = sitePackages.resolve(relative);
                    } else if (name.contains(".data/scripts")) {
                        String relative = name.split("\\.data/scripts/")[1];
                        path = scriptOrBin.resolve(relative);
                    } else {
                        path = sitePackages.resolve(name);
                    }

                    tasks.add(() -> {
                        if (!success.get()) return null;
                        if (!Files.exists(path) || (expectedHash != null && !SecurityManager.verifyHash(path, expectedHash))) {
                            InstallationManager.logMessage("Error verifying file: '" + path + "'. File may be missing or have been tampered with.");
                            success.set(false);
                        }
                        InstallationManager.stagePercent.addAndGet(delta.get());
                        return null;
                    });
                }
            }

            for (String name : manifest.hashes.keySet()) {
                Path path = backend.resolve(name);
                String expectedHash = manifest.hashes.get(name);

                tasks.add(() -> {
                    if (!success.get()) return null;
                    if (!Files.exists(path) || (expectedHash != null && !SecurityManager.verifyHash(path, expectedHash))) {
                        InstallationManager.logMessage("Error verifying file: 'core/" + name + "'. File may be missing or have been tampered with.");
                        success.set(false);
                    }
                    InstallationManager.stagePercent.addAndGet(delta.get());
                    return null;
                });
            }

            // Submit verification tasks for python manifest
            for (String name : pythonManifest.hashes.keySet()) {
                Path path = backend.resolve("python").resolve(name);
                String expectedHash = pythonManifest.hashes.get(name);

                tasks.add(() -> {
                    if (!success.get()) return null;
                    if (!Files.exists(path) || (expectedHash != null && !SecurityManager.verifyHash(path, expectedHash))) {
                        InstallationManager.logMessage("Error verifying file: 'python/" + name + "'. File may be missing or have been tampered with.");
                        success.set(false);
                    }
                    InstallationManager.stagePercent.addAndGet(delta.get());
                    return null;
                });
            }

            if (tasks.isEmpty()) {
                executor.shutdown();
                return false;
            }

            delta.set(100.0 / tasks.size());

            // Execute all tasks in parallel
            List<Future<Void>> futures = executor.invokeAll(tasks);

            // Optional: check for exceptions
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
}
