package com.crow.locrowai.loader;

import com.crow.locrowai.LocrowAI;
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
import java.util.Set;

import static com.crow.locrowai.LocrowAI.PY_VERSION;

public class PackageLoader {
    public static boolean installed() throws IOException {
        Path root = FMLPaths.GAMEDIR.get().resolve("locrowai");

        if (!Files.exists(root)) {
            Files.createDirectory(root);
            return false;
        }
        Path pyapp = root.resolve(PY_VERSION);

        if (!Files.exists(pyapp)) {
            Files.createDirectory(pyapp);
            return false;
        }

        return true;
    }


    public static void downloadAndUnzip(String url, Path gameDir) throws IOException {
        Path base = gameDir.resolve("locrowai").resolve(PY_VERSION);
        Path zip  = base.resolve("pyenv.zip");
        Files.createDirectories(base);

        LocrowAI.LOGGER().info("[Setup] Downloading PyEnv package from " + url + " to " + base);

        // Download with timeouts (ms): connect=10s, read=10min
        FileUtils.copyURLToFile(new URL(url), zip.toFile(), 10_000, 600_000);

        unzipZipFile(zip, base);

        // Optional: clean up
        Files.deleteIfExists(zip);
    }

    /** Uses Commons Compress ZipFile (handles ZIP64, good perf). */
    public static void unzipZipFile(Path zipPath, Path destDir) throws IOException {
        Files.createDirectories(destDir);
        LocrowAI.LOGGER().info("[Setup] Unzipping PyEnv package");
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
}
