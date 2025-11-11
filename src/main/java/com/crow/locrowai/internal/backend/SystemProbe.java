package com.crow.locrowai.internal.backend;

import org.lwjgl.opengl.GL11;
import oshi.SystemInfo;
import oshi.hardware.GraphicsCard;
import oshi.software.os.OperatingSystem;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

class SystemProbe {

    record OsInfo(String name, String arch, int bits) {}
    record GpuInfo(String vendor, String model, String deviceId, long VRam) {}
    record ProbeResult(OsInfo os, List<GpuInfo> gpus) {}

    static SystemInfo si = new SystemInfo();

    static final ProbeResult result = new ProbeResult(osInfo(), gpuInfo());

    static OsInfo osInfo() {
        OperatingSystem os = si.getOperatingSystem();
        String arch = System.getProperty("os.arch", "unknown");
        return new OsInfo(os.getFamily(), arch, os.getBitness());
    }

    static List<GpuInfo> gpuInfo() {
        List<GpuInfo> gpus = new ArrayList<>();
        for (GraphicsCard card : si.getHardware().getGraphicsCards()) {
            gpus.add(new GpuInfo(
                    orUnknown(card.getVendor()),
                    orUnknown(card.getName()),
                    orUnknown(card.getDeviceId()),
                    card.getVRam()
            ));
        }

        return gpus;
    }

    static String orUnknown(String s) {
        return s == null ? "unknown" : s;
    }

    static String osKey(SystemProbe.OsInfo os) {
        String fam = os.name().toLowerCase(Locale.ROOT);
        if (fam.contains("win")) return "windows";
        if (fam.contains("mac") || fam.contains("darwin")) return "macos";
        if (fam.contains("linux")) return "linux";
        return "unknown";
    }

    static String archKey(String archRaw) {
        String a = archRaw.toLowerCase(Locale.ROOT).replaceAll("[_\\-]", "");
        if (a.equals("amd64") || a.equals("x8664")) return "x86_64";
        if (a.equals("x86") || a.equals("i386") || a.equals("i686")) return "x86";
        if (a.equals("aarch64") || a.equals("arm64")) return "arm64";
        if (a.startsWith("arm")) return "arm";
        return "unknown";
    }

    /** Prefer NVIDIA if present; otherwise AMD/Intel/Apple; else unknown. */
    static String vendorKey(List<SystemProbe.GpuInfo> gpus) {
        var strs = gpus.stream()
                .map(g -> g.vendor().toLowerCase(Locale.ROOT))
                .toList();
        if (strs.stream().anyMatch(s -> s.startsWith("nvidia"))) return "nvidia";
        if (strs.stream().anyMatch(s -> s.startsWith("intel"))) return "intel";
        if (strs.stream().anyMatch(s -> s.startsWith("amd") || s.startsWith("advanced micro") || s.startsWith("ati"))) return "amd";
        if (strs.stream().anyMatch(s -> s.startsWith("apple"))) return "apple";
        return "unknown";
    }

    static String getGPUBackend() {
        String vendor = vendorKey(result.gpus);

        return switch (vendor) {
            case "nvidia" -> "cuda";
//            case "intel" -> "xpu";
//            case "amd" -> "rocm";
//            case "apple" -> "metal";
            default -> "cpu";
        };
    }

    static ProcessBuilder buildScriptProcess(Path dir, String scriptName, String... commands) {
        SystemProbe.ProbeResult si = SystemProbe.result;
        String os = si.os().name().toLowerCase(Locale.ROOT);
        Path script;
        List<String> comms = new ArrayList<>();
        if (os.contains("win")) {
            script = dir.resolve(scriptName + ".bat").normalize();
            comms.add("cmd.exe");
            comms.add("/c");
        } else if (os.contains("linux") || os.contains("unix") || os.contains("mac")) {
            script = dir.resolve(scriptName + ".sh").normalize();
            comms.add("bash");
        } else {
            script = dir.resolve(scriptName + ".sh").normalize();
            comms.add("bash");
        }

        ProcessBuilder processBuilder = new ProcessBuilder();

        comms.add(script.toString());
        comms.addAll(List.of(commands));
        processBuilder.command(comms);

        processBuilder.directory(dir.toFile());

        return processBuilder;
    }
}
