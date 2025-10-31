package com.crow.locrowai.loader;

import com.crow.locrowai.installer.SystemProbe;

import java.nio.file.Path;
import java.util.Locale;

public class ServerBuilder {

    public static ProcessBuilder builder(SystemProbe.ProbeResult si, Path dir) {
        ProcessBuilder processBuilder = new ProcessBuilder();
        Path pybat = dir.resolve("python.bat").normalize();
        if (si.os().name().toLowerCase(Locale.ROOT).contains("win")) {
            processBuilder.command("cmd.exe", "/c", pybat.toString(), "-m", "uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000");
        }
        processBuilder.directory(dir.toFile());

        return processBuilder;
    }
}
