package com.crow.locrowai.internal.backend;

import com.crow.locrowai.api.registration.PackageManifest;
import com.crow.locrowai.internal.LocrowAI;
import com.crow.locrowai.api.registration.AIExtension;
import com.crow.locrowai.api.registration.AIRegistry;
import com.crow.locrowai.api.registration.exceptions.UnsupportedRequirementException;
import com.google.common.util.concurrent.AtomicDouble;
import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.loading.FMLPaths;
import org.jetbrains.annotations.ApiStatus;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.crow.locrowai.internal.LocrowAI.MODID;

@ApiStatus.Internal
public class InstallationManager {

    public record Stage(String name, double weight) {} // weight = relative portion of the bar

    // define your stages and their relative weights
    public static final List<Stage> STAGES = List.of(
            new Stage("Downloading AI Backend", 1),
            new Stage("Installing AI Backend", 1),
            new Stage("Installing AI Extensions", 1),
            new Stage("Verifying AI Backend", 1),
            new Stage("Verifying AI Extensions", 1),
            new Stage("Testing AI Backend", 1),
            new Stage("Done", 0) // zero weight for final "done" state (you can still display it)
    );

    private static Thread thread;

    private static BufferedWriter logger;

    public static final AtomicInteger currentStageIndex = new AtomicInteger(0); // index into STAGES
    public static final AtomicDouble stagePercent = new AtomicDouble(0); // 0..100 within current stage
    public static final AtomicBoolean installing = new AtomicBoolean(false);
    public static final AtomicBoolean hadError = new AtomicBoolean(false);
    public static final AtomicReference<String> subLabel = new AtomicReference<>("");

    private static final Gson gson = new Gson();

    private static final Map<String, String> libMap = new HashMap<>();
    private static final Set<String> handledLibs = new HashSet<>();

    private static final BlockingQueue<ProcessBuilder> libQueue = new LinkedBlockingQueue<>(4);

    public static final String CORE_INDEX = "https://pypi.fury.io/iccrow/";
    public static final Map<String, String> TORCH_INDICES = Map.of(
            "cuda", "https://download.pytorch.org/whl/cu121",
            "cpu", "https://download.pytorch.org/whl/cpu"
    );
    public static final String PYPI_INDEX = "https://pypi.org/simple/";

    public static final Map<String, String> GPU_VERSION_TAGS = Map.of(
            "cuda", "+cu121",
            "cpu", "+cpu"
    );

    public static final Set<String> DEFAULT_LIBS = Set.of("setuptools==80.9.0", "pip==23.0.1", "wheel==0.45.1");

    static void queueLib(ProcessBuilder builder) throws InterruptedException {
        libQueue.put(builder);
    }

    static void removeQueuedLib(ProcessBuilder builder) {
        libQueue.remove(builder);
    }

    public static String getLib(String module) throws IOException {
        if (libMap.isEmpty()) {
            InputStream libStream = LocrowAI.class.getClassLoader().getResourceAsStream(LocrowAI.MODID + "/lib_map.json");
            if (libStream == null) throw new MissingResourceException("Missing file: /" + LocrowAI.MODID + "/lib_map.json", "ExtensionInstaller", "lib_map.json");
            BufferedReader libReader = new BufferedReader(new InputStreamReader(libStream));

            libMap.putAll(gson.fromJson(libReader, Map.class));
            libReader.close();
            libStream.close();
            logMessage("Imported Python library whitelist.");
        }

        return libMap.get(module);
    }

    static boolean handleLib(String module) {
        return !handledLibs.add(module);
    }

    static int libCount() throws IOException {
        Set<String> libs = new HashSet<>();

        PackageManifest manifest = PackageManifest.fetch(
                InstallationManager.getBackendPath().resolve("core-manifest.json"),
                SecurityManager.OFFICIAL_KEY
        );

        for (String requirement : manifest.requirements) {
            libs.addAll(List.of(InstallationManager.getLib(requirement).split(" ")));
        }

        for (AIExtension extension : AIRegistry.getExtensions()) {
            for (String requirement : extension.getRequirements()) {
                libs.addAll(List.of(InstallationManager.getLib(requirement).split(" ")));
            }
        }
        LocrowAI.LOGGER().info(Arrays.toString(libs.toArray()));
        return libs.size();
    }

    public static boolean libInstalled(String lib) throws IOException, InterruptedException {
        Path dir = InstallationManager.getBackendPath();
        ProcessBuilder builder = SystemProbe.buildScriptProcess(
                dir, "python", "-c",
                "import importlib.util, sys; sys.exit(0 if importlib.util.find_spec('" + lib + "') else 1)"
        );

        assert builder != null;
        Process process = builder.start();

        int exitCode = process.waitFor();

        return exitCode == 0;
    }

    public static boolean isFullyInstalled() {
        try {
            for (AIExtension extension : AIRegistry.getExtensions()) {
                if (!extension.installed()) return false;
            }
            return EnvironmentInstaller.installed();
        } catch (IOException e) {
            return false;
        }
    }

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

    static void init() {
        try {
            Path root = getRootPath();
            Path setupLogs = root.resolve("logs").resolve("setup");
            Path backend = InstallationManager.getBackendPath();

            Files.createDirectories(setupLogs);
            Files.createDirectories(backend);

            logger = new BufferedWriter(
                    new OutputStreamWriter(
                            new FileOutputStream(
                                    setupLogs.resolve(
                                            LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES).toString().replace(":", "-") + ".log"
                                    ).toFile(), true
                            ), StandardCharsets.UTF_8
                    )
            );

            cacheWriter = new BufferedWriter(
                    new OutputStreamWriter(
                            new FileOutputStream(
                                    backend.resolve(
                                            "wheels.txt"
                                    ).toFile(), true
                            ), StandardCharsets.UTF_8
                    )
            );

            logMessage("Setup log file successfully created!");
        } catch (IOException e) {
            e.printStackTrace();
            LocrowAI.LOGGER().error("Could not create a log file! Setup will not be logged!");
        }

        thread = new Thread(() -> {
            try {


                startInstall();
                if (InstallationManager.hadError.get()) {
                    installing.set(false);
                    return;
                }
                LoadManager.load();
                DistExecutor.safeRunWhenOn(Dist.CLIENT, InstallationManager::toast);
            } catch (Exception e) {
                e.printStackTrace();
                logMessage(e.toString());
                logMessage("Abandoned backend install.");
                installing.set(false);
                hadError.set(true);
            } finally {
                try {
                    logger.close();
                    cacheWriter.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        });

        thread.start();
    }

    static void startInstall() throws IOException, InterruptedException {
        Path backend = getBackendPath();

        installing.set(true);
        hadError.set(false);
        currentStageIndex.set(0);
        stagePercent.set(0);
        if (!EnvironmentInstaller.installed()) {

            if (backend.toFile().exists()) {
                logMessage("Deleting existing backend files...");
//                FileUtils.deleteDirectory(backend.toFile());
                logMessage("Deleted existing backend files.");
            }

            logMessage("Downloading Python environment...");
            EnvironmentInstaller.download();
            if (hadError.get()) return;
            logMessage("Downloaded Python environment.");

            currentStageIndex.set(1);
            stagePercent.set(0);

            logMessage("Installing Python environment...");
            EnvironmentInstaller.install();
            if (hadError.get()) return;
            logMessage("Installed Python environment.");
        }

        currentStageIndex.set(2);
        stagePercent.set(0);

        Path ePath = backend.resolve("extensions");

        if (!Files.exists(ePath)) {
            Files.createDirectory(ePath);
            logMessage("Created missing extensions folder.");
        }

        int n = 0;
        for (AIExtension extension : AIRegistry.getExtensions())
            n += extension.getRequirements().size();

        logMessage("Installing " + AIRegistry.getExtensions().size() + " extensions with " + n + " total Python requirements.");
        for (AIExtension extension : AIRegistry.getExtensions()) {
            ExtensionInstaller.install(extension, AIRegistry.getLoader(extension.getMODID()), n);
            if (hadError.get()) return;
        }

        if (n > 0)
            ExtensionInstaller.waitForInstalls();
        if (hadError.get()) return;
        logMessage("Installed " + AIRegistry.getExtensions().size() + " extensions.");

        currentStageIndex.set(3);
        stagePercent.set(0);

        logMessage("Verifying AI backend files...");
        logMessage("Verifying core backend files...");
        if (!EnvironmentInstaller.verify()) {
            hadError.set(true);
            return;
        }
        logMessage("Verified core backend files.");
        currentStageIndex.set(4);
        stagePercent.set(0);

        logMessage("Verifying extension files...");
        if (!ExtensionInstaller.verify()) {
            hadError.set(true);
            return;
        }
        logMessage("Verified extension files.");
        currentStageIndex.set(5);
        stagePercent.set(0);

        logMessage("Running backend test...");
        ProcessBuilder builder = SystemProbe.buildScriptProcess(backend, "python", "app.py");
        builder.redirectErrorStream(true);
        Process process = builder.start();
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));

        String line;
        while ((line = reader.readLine()) != null) {
            logMessage(line);
        }
        logMessage("Finished backend test with exit code " + process.exitValue());
        if (process.exitValue() != 0) {
            hadError.set(true);
            return;
        }
        logMessage("Verified AI backend files.");

        currentStageIndex.set(6);
        stagePercent.set(0);

        installing.set(false);
        logMessage("AI backend install complete!");
    }

    // computed from STAGES weights
    private static double totalWeight() {
        return STAGES.stream().mapToDouble(Stage::weight).sum();
    }

    // returns 0..100 overall percent
    public static int getOverallPercent() {
        int idx = currentStageIndex.get();
        double total = totalWeight();
        if (total == 0) return 100;
        double completed = 0;
        for (int i = 0; i < idx; i++) completed += STAGES.get(i).weight;
        double currentWeight = STAGES.get(idx).weight;
        double curPct = (Math.max(0, stagePercent.get()) / 100.0) * currentWeight;
        double overall = (completed + curPct) / total * 100.0;
        if (overall > 100) overall = 100;
        return (int) Math.round(overall);
    }

    public static String getCurrentStageName() {
        int idx = currentStageIndex.get();
        if (idx >= 0 && idx < STAGES.size()) return STAGES.get(idx).name();
        return "";
    }

    static void cancelInstall() {
        installing.set(false);
        subLabel.set("Canceled");
    }

    public static Path getRootPath() {
        return FMLPaths.GAMEDIR.get().resolve(MODID);
    }

    public static Path getBackendPath() {
        Path root = FMLPaths.GAMEDIR.get().resolve(MODID);

        return root.resolve("backend");
    }

    public static Path getLatestLogPath() {
        Path path = getRootPath().resolve("logs").resolve("setup");
        File[] logs = path.toFile().listFiles((d, name) -> name.endsWith(".log"));

        if (logs == null || logs.length == 0) return null;

        Arrays.sort(logs, Comparator.comparing(File::getName).reversed());

        return logs[0].toPath();
    }

    static void copyFilesFromResources(ClassLoader loader, Path resourcePath, Path destination, List<String> files) throws IOException {
        if (!Files.exists(destination))
            Files.createDirectory(destination);

        for (String file : files) {
            InputStream stream = loader.getResourceAsStream( resourcePath.resolve(file).toString());
            if (stream == null) throw new MissingResourceException("Missing file: " + file, "ExtensionInstaller", file);

            Path out = destination.resolve(file);
            Files.createDirectories(out.getParent());
            Files.copy(stream, out, StandardCopyOption.REPLACE_EXISTING);
            stream.close();

            logMessage("Copied '" + file + "' to disk.");
        }
    }

    private static Pattern wheelPattern = Pattern.compile("([\\w\\-\\.\\+%]+\\.whl)");
    private static Set<String> wheelCache = new HashSet<>();
    private static BufferedWriter cacheWriter;
    static void cacheWheelName(String name) throws IOException {
        if (wheelCache.add(name) && cacheWriter != null) {
            cacheWriter.write(name);
            cacheWriter.newLine();
            cacheWriter.flush();
        }
    }

    static void installPythonLibraries(List<String> requirements, double stageDelta) throws InterruptedException, IOException {
        String gpuBackend = SystemProbe.getGPUBackend();
        String torchIndex = InstallationManager.TORCH_INDICES.get(gpuBackend);
        logMessage("Searching for PyTorch libraries at " + torchIndex);

        for (String requirement : requirements) {
            String pip = InstallationManager.getLib(requirement);
            if (pip == null) {
                throw new UnsupportedRequirementException(requirement);
            }
            pip = pip.replace("+xxxxx", GPU_VERSION_TAGS.get(gpuBackend));

            if (InstallationManager.handleLib(pip) ||
                    (InstallationManager.libInstalled(requirement) && !DEFAULT_LIBS.contains(pip))) {
                InstallationManager.stagePercent.addAndGet(stageDelta);
                continue;
            }


            List<String> pipInstall = List.of("-m", "pip", "install");
            List<String> args = new ArrayList<>(pipInstall);
            args.addAll(List.of(pip.split(" ")));
            args.addAll(List.of("--index-url", InstallationManager.CORE_INDEX,
                    "--extra-index-url", torchIndex,
                    "--extra-index-url", InstallationManager.PYPI_INDEX,
                    "--progress-bar", "off", "--no-deps", "--no-cache-dir", "--disable-pip-version-check"));

            ProcessBuilder builder = SystemProbe.buildScriptProcess(
                    InstallationManager.getBackendPath(),
                    "python", args.toArray(new String[0])
            );


            builder.redirectErrorStream(true);


            InstallationManager.queueLib(builder);

            Process downloader = builder.start();
            logMessage("Started pip subprocess for installing '" + requirement + "'.");

            CompletableFuture<Process> future = downloader.onExit();
            future.thenAccept(process -> {
                InstallationManager.removeQueuedLib(builder);
                InstallationManager.stagePercent.addAndGet(stageDelta);


                if (process.exitValue() != 0) {
                    InstallationManager.hadError.set(true);
                    logMessage("Encountered non-zero exit code " + process.exitValue() + " while installing '" + requirement + "'.");
                } else {
                    logMessage("Successfully installed '" + requirement + "'.");
                }

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream())
                )) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\n");

                        Matcher matcher = wheelPattern.matcher(line);
                        if (matcher.find()) {
                            String wheelName = matcher.group(1).replace("%2B", "+").replace(".whl", "");
                            InstallationManager.cacheWheelName(wheelName);
                        }
                    }

                    logMessage(sb.toString().trim());
                } catch (IOException e) {
                    logMessage("Error reading subprocess log!");
                    logMessage(e.toString());
                }
            });
        }
    }

    private static DistExecutor.SafeRunnable toast() {
        return new DistExecutor.SafeRunnable() {
            @Override
            public void run() {
                var toasts = Minecraft.getInstance().getToasts();
                SystemToast.add(
                        toasts,
                        SystemToast.SystemToastIds.PERIODIC_NOTIFICATION,
                        Component.literal("Finished installing AI packages"),
                        Component.literal("Local AI features will be available after warmup.")
                );
            }
        };
    }
}
