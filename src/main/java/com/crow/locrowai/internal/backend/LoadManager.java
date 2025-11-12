package com.crow.locrowai.internal.backend;

import com.crow.locrowai.internal.AIBackendManagerScreen;
import com.crow.locrowai.internal.LocrowAI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.ApiStatus;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApiStatus.Internal
public class LoadManager {

    private static final Pattern pattern = Pattern.compile("https?://[^:/]+:(\\d+)");

    private static boolean hooked = false;
    private static Process process;
    private static Thread stdoutThread;
    private static int port = 8000;

    private static BufferedWriter logger;

    static synchronized void logMessage(String line) {
        try {
            if (line == null) return;

            LocrowAI.LOGGER().info(line);

            if (logger == null) return;

            logger.write("[" + LocalTime.now() + "]: " + line);
            logger.newLine();
            logger.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static void load() {
        try {

            logger = null;

            Path root = InstallationManager.getRootPath();
            Path runtimeLogs = root.resolve("logs").resolve("runtime");

            Files.createDirectories(runtimeLogs);

            logger = new BufferedWriter(
                    new OutputStreamWriter(
                            new FileOutputStream(
                                    runtimeLogs.resolve(
                                            LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES).toString().replace(":", "-") + ".log"
                                    ).toFile(), true
                            ), StandardCharsets.UTF_8
                    )
            );

            logMessage("Runtime log file successfully created!");
        } catch (IOException e) {
            e.printStackTrace();
            logMessage("Could not create a log file! Runtime output will not be logged!");
        }

        try {

            ProcessBuilder builder = SystemProbe.buildScriptProcess(InstallationManager.getBackendPath(), "run");

            builder.redirectErrorStream(true);

            process = builder.start();

            stdoutThread = new Thread(() -> {
                try {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(process.getInputStream()));

                    String line;
                    while ((line = reader.readLine()) != null) {
                        logMessage(line);
                        if (line.contains("Uvicorn running on")) {
                            Matcher matcher = pattern.matcher(line);
                            matcher.find();
                            port = Integer.parseInt(matcher.group(1));

                            DistExecutor.safeRunWhenOn(Dist.CLIENT, LoadManager::warmToast);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }, "Locrow-AI-Backend-Output");

            stdoutThread.start();

            DistExecutor.safeRunWhenOn(Dist.CLIENT, LoadManager::coldToast);

            if (!hooked) {
                Runtime.getRuntime().addShutdownHook(new Thread(LoadManager::kill));
                hooked = true;
            }

        } catch (IOException e) {
            e.printStackTrace();
            logMessage("Could not load the AI backend.");
            kill();
        }
    }

    static void kill() {
        if (process != null) {
            process.descendants().forEach(ProcessHandle::destroy);
            process.destroy();
        }

        if (stdoutThread != null) {
            stdoutThread.interrupt();
        }
        try {
            logger.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static CompletableFuture<Boolean> warmup() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + LoadManager.getPort() + "/warmup"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();

        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        client.sendAsync(
                request,
                HttpResponse.BodyHandlers.ofString()
        ).thenAccept(response -> {
            future.complete(response.statusCode() == 200);
        }).exceptionally(ex -> {
            future.complete(false);
            return null;
        });

        return future;
    }

    static CompletableFuture<Boolean> freeze() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + LoadManager.getPort() + "/freeze"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();

        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        client.sendAsync(
                request,
                HttpResponse.BodyHandlers.ofString()
        ).thenAccept(response -> {
            future.complete(response.statusCode() == 200);
        }).exceptionally(ex -> {
            future.complete(false);
            return null;
        });

        return future;
    }

    static DistExecutor.SafeRunnable showTamperWarning() {
        return new DistExecutor.SafeRunnable() {
            @Override
            public void run() {
                if (!EventManager.screenReady.get()) {
                    EventManager.awaitingTamperWarning.set(true);
                    return;
                }

                Minecraft mc = Minecraft.getInstance();
                mc.execute(() -> {
                    Screen prevScreen = mc.screen;

                    String titleText = "AI Backend Verification Failed!";
                    int titleColor = 0xFF5555;

                    Component message = Component.empty()
                            .append(Component.literal("The AI backend could not be verified. Files may have been tampered with.\n\n")
                                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))))
                            .append(Component.literal("You can manage the backend, proceed anyway, or go back.")
                                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xAAAAAA))));

                    mc.setScreen(
                            new ConfirmScreen(
                                    result -> {
                                        EventManager.awaitingTamperWarning.set(false);
                                        if (result) {
                                            InstallationManager.installing.set(false);
                                            InstallationManager.hadError.set(false);
                                            Minecraft.getInstance().setScreen(prevScreen);
                                            LoadManager.load();
                                        } else {
                                            // Manage Backend
                                            Minecraft.getInstance().setScreen(new AIBackendManagerScreen(prevScreen));
                                        }
                                    },
                                    Component.literal(titleText).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(titleColor))),
                                    message,
                                    Component.literal("Proceed Anyway"),
                                    Component.literal("Manage Backend")
                            )
                    );
                });

            }
        };
    }

    static DistExecutor.SafeRunnable coldToast() {
        return new DistExecutor.SafeRunnable() {
            @Override
            public void run() {
                var toasts = Minecraft.getInstance().getToasts();
                SystemToast.add(
                        toasts,
                        SystemToast.SystemToastIds.PERIODIC_NOTIFICATION,
                        Component.literal("AI packages are warming up"),
                        Component.literal("Local AI features will be available soon.")
                );
            }
        };
    }

    static DistExecutor.SafeRunnable warmToast() {
        return new DistExecutor.SafeRunnable() {
            @Override
            public void run() {
                var toasts = Minecraft.getInstance().getToasts();
                SystemToast.add(
                        toasts,
                        SystemToast.SystemToastIds.PERIODIC_NOTIFICATION,
                        Component.literal("AI packages are warmed up"),
                        Component.literal("Local AI features are now available.")
                );
            }
        };
    }

    public static int getPort() {
        return port;
    }

    public static boolean isRunning() {
        return process.isAlive();
    }
}
