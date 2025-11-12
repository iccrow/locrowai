package com.crow.locrowai.internal.backend;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.ApiStatus;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Set;

class ExtractUtils {
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

    static void unzipFile(Path zipPath, Path destDir) throws IOException {
        Files.createDirectories(destDir);
        InstallationManager.logMessage("Extracting .zip package: " + zipPath);

        try (InputStream fis = Files.newInputStream(zipPath);
             ZipArchiveInputStream zis = new ZipArchiveInputStream(fis)) {

            ZipArchiveEntry entry;
            while ((entry = zis.getNextZipEntry()) != null) {
                String entryName = entry.getName();
                Path out = safeResolve(destDir, entryName);

                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                    continue;
                }

                // Handle symbolic links
                if (entry.isUnixSymlink()) {
                    // The symlink target is stored as the entry content
                    byte[] targetBytes = zis.readAllBytes();
                    String linkTargetStr = new String(targetBytes);
                    try {
                        Path linkTarget = Path.of(linkTargetStr);
                        if (!linkTarget.isAbsolute()) {
                            linkTarget = out.getParent().resolve(linkTarget).normalize();
                        }
                        if (!linkTarget.startsWith(destDir)) {
                            InstallationManager.logMessage("Skipping symlink that would escape destination: " + entryName + " -> " + linkTargetStr);
                        } else {
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
                    IOUtils.copy(zis, os);
                }

                // Restore POSIX permissions if available
                int mode = entry.getUnixMode(); // -1 if not set
                if (mode > 0) {
                    try {
                        Set<PosixFilePermission> perms = modeToPosix(mode);
                        if (!perms.isEmpty()) {
                            Files.setPosixFilePermissions(out, perms);
                        }
                    } catch (UnsupportedOperationException ignored) {
                        // Filesystem does not support POSIX (Windows)
                    }
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
