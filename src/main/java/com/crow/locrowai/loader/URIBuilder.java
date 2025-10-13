package com.crow.locrowai.loader;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import static com.crow.locrowai.LocrowAI.PY_TEMPLATE;
import static com.crow.locrowai.LocrowAI.PY_VERSION;

public final class URIBuilder {
    public static String osKey(SystemProbe.OsInfo os) {
        String fam = os.name().toLowerCase(Locale.ROOT);
        if (fam.contains("win")) return "windows";
        if (fam.contains("mac") || fam.contains("darwin")) return "macos";
        if (fam.contains("linux")) return "linux";
        return "unknown";
    }

    public static String archKey(String archRaw) {
        String a = archRaw.toLowerCase(Locale.ROOT).replaceAll("[_\\-]", "");
        if (a.equals("amd64") || a.equals("x8664")) return "x86_64";
        if (a.equals("x86") || a.equals("i386") || a.equals("i686")) return "x86";
        if (a.equals("aarch64") || a.equals("arm64")) return "arm64";
        if (a.startsWith("arm")) return "arm";
        return "unknown";
    }

    /** Prefer NVIDIA if present; otherwise AMD/Intel/Apple; else unknown. */
    public static String vendorKey(List<SystemProbe.GpuInfo> gpus) {
        var strs = gpus.stream()
                .map(g -> (g.vendor() + " " + g.model()).toLowerCase(Locale.ROOT))
                .toList();
        if (strs.stream().anyMatch(s -> s.contains("nvidia"))) return "nvidia";
        if (strs.stream().anyMatch(s -> s.contains("amd") || s.contains("advanced micro") || s.contains("ati"))) return "amd";
        if (strs.stream().anyMatch(s -> s.contains("apple"))) return "apple";
        if (strs.stream().anyMatch(s -> s.contains("intel"))) return "intel";
        return "unknown";
    }

//    /** cuda12 (major only) if NVIDIA + nvidia-smi says CUDA present; else cpu */
//    public static String accelKey(String vendor, Optional<SystemProbe.NvidiaInfo> nv) {
//        if ("nvidia".equals(vendor) && nv.isPresent()) {
//            String cv = nv.get().cudaVersion();
//            if (cv != null && !cv.isBlank()) {
//                String num = cv.replaceAll("[^0-9.]", "");
//                String major = num.contains(".") ? num.substring(0, num.indexOf('.')) : num;
//                if (!major.isBlank()) return "cuda" + major; // e.g., cuda12
//            }
//        }
//        return "cpu";
//    }

    public static String fetchLatestVersion(String base, String template, SystemProbe.ProbeResult res) throws IOException {
        String os = osKey(res.os());
        String arch = archKey(res.os().arch());
        String vendor = vendorKey(res.gpus());
//        String accel = accelKey(vendor, res.nvidia());

        // Fallbacks to ensure a valid path every time:
        if (os.equals("unknown")) os = "linux";                 // safe default
        if (arch.equals("unknown")) arch = "x86_64";            // most common
//        if (accel.equals("cpu") && arch.equals("arm")) arch = "arm64"; // normalize odd ARM reports
        String url = String.format("%s/%s/%s/%s/%s/latest.txt",
                trimSlash(base), template, os, arch, vendor);
        return IOUtils.toString(new URL(url), StandardCharsets.UTF_8).strip();
    }

    /** Final URL: <base>/<ver>/<os>/<arch>/<accel>/x.y.z.zip */
    public static String pythonBuildUrl(String base, SystemProbe.ProbeResult res) throws IOException {
        String os = osKey(res.os());
        String arch = archKey(res.os().arch());
        String vendor = vendorKey(res.gpus());
//        String accel = accelKey(vendor, res.nvidia());

        // Fallbacks to ensure a valid path every time:
        if (os.equals("unknown")) os = "linux";                 // safe default
        if (arch.equals("unknown")) arch = "x86_64";            // most common
//        if (accel.equals("cpu") && arch.equals("arm")) arch = "arm64"; // normalize odd ARM reports
        // Example: https://huggingface.co/iccrow/minecraft-locrowai/resolve/main/builds/0.1.0/windows/x86_64/nvidia/pyenv.zip
        return String.format("%s/%s/%s/%s/%s/%s.zip",
                trimSlash(base), PY_TEMPLATE, os, arch, vendor, PY_VERSION());
    }

    private static String trimSlash(String s) {
        if (s.endsWith("/")) return s.substring(0, s.length() - 1);
        return s;
    }
}
