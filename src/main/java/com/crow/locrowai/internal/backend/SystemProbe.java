package com.crow.locrowai.internal.backend;

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

    static final ProbeResult result = new ProbeResult(osInfo(), GpuInfo());

    static OsInfo osInfo() {
        OperatingSystem os = si.getOperatingSystem();
        String arch = System.getProperty("os.arch", "unknown");
        return new OsInfo(os.getFamily(), arch, os.getBitness());
    }

    static List<GpuInfo> GpuInfo() {
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
