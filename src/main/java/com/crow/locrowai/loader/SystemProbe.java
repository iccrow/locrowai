package com.crow.locrowai.loader;

import oshi.SystemInfo;
import oshi.hardware.GraphicsCard;
import oshi.software.os.OperatingSystem;

import java.util.ArrayList;
import java.util.List;

public class SystemProbe {

    public record OsInfo(String name, String arch, int bits) {}
    public record GpuInfo(String vendor, String model, String deviceId, long VRam) {}
    public record ProbeResult(OsInfo os, List<GpuInfo> gpus) {}

    private static SystemInfo si = new SystemInfo();

    public static ProbeResult probe() {
        return new ProbeResult(osInfo(), GpuInfo());
    }

    public static OsInfo osInfo() {
        OperatingSystem os = si.getOperatingSystem();
        String arch = System.getProperty("os.arch", "unknown");
        return new OsInfo(os.getFamily(), arch, os.getBitness());
    }

    public static List<GpuInfo> GpuInfo() {
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

    public static String orUnknown(String s) {
        return s == null ? "unknown" : s;
    }
}
