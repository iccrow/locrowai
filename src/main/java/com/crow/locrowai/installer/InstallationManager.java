package com.crow.locrowai.installer;

import com.crow.locrowai.LocrowAI;
import com.crow.locrowai.api.registration.AIExtension;
import com.crow.locrowai.api.registration.AIRegistry;
import com.crow.locrowai.api.registration.exceptions.AIRegistrationException;
import com.google.common.util.concurrent.AtomicDouble;
import com.google.gson.Gson;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.commons.io.FileUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.crow.locrowai.LocrowAI.MODID;

public class InstallationManager {

    public record Stage(String name, double weight) {} // weight = relative portion of the bar

    // define your stages and their relative weights
    public static final List<Stage> STAGES = List.of(
            new Stage("Downloading AI Backend", 1),
            new Stage("Installing AI Backend", 1),
            new Stage("Installing AI Extensions", 2),
            new Stage("Verifying Install", 1),
            new Stage("Done", 0) // zero weight for final "done" state (you can still display it)
    );

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
            "cuda", "https://download.pytorch.org/whl/cu121"
    );
    public static final String PYPI_INDEX = "https://pypi.org/simple/";

    public static void queueLib(ProcessBuilder builder) throws InterruptedException {
        libQueue.put(builder);
    }

    public static void removeQueuedLib(ProcessBuilder builder) {
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
        }

        return libMap.get(module);
    }

    public static boolean handleLib(String module) {
        return !handledLibs.add(module);
    }

    public static boolean libInstalled(String lib) throws IOException, InterruptedException {
        Path dir = InstallationManager.getInstallPath();
        ProcessBuilder builder = InstallationManager.buildScriptProcess(
                dir, "python", "-c",
                "import importlib.util, sys; sys.exit(0 if importlib.util.find_spec('" + lib + "') else 1)"
        );

        assert builder != null;
        Process process = builder.start();

        int exitCode = process.waitFor();

        return exitCode == 0;
    }

    public static void init() {
        try {
            startInstall();
        } catch (Exception exception) {
            exception.printStackTrace();

            hadError.set(true);
        }
    }

    public static void startInstall() throws IOException, InterruptedException {
        Path backend = getInstallPath();

        installing.set(true);
        hadError.set(false);
        currentStageIndex.set(0);
        stagePercent.set(0);
        if (!EnvironmentInstaller.installed()) {

            if (backend.toFile().exists()) {
                FileUtils.deleteDirectory(backend.toFile());
            }

            EnvironmentInstaller.download();

            currentStageIndex.set(1);
            stagePercent.set(0);

            EnvironmentInstaller.install();
        }

        currentStageIndex.set(2);
        stagePercent.set(0);

        Path ePath = backend.resolve("extensions");

        if (!Files.exists(ePath)) {
            Files.createDirectory(ePath);
        }

        int n = 0;
        for (AIExtension extension : AIRegistry.getExtensions())
            n += extension.getRequirements().size();
        LocrowAI.LOGGER().info(String.valueOf(AIRegistry.getExtensions().size()));
        LocrowAI.LOGGER().info(String.valueOf(n));

        for (AIExtension extension : AIRegistry.getExtensions())
            ExtensionInstaller.install(extension, AIRegistry.getLoader(extension.getMODID()), n);

        ExtensionInstaller.waitForInstalls();

        currentStageIndex.set(3);
        stagePercent.set(0);

        ExtensionInstaller.verify();

        currentStageIndex.set(4);
        stagePercent.set(0);

        installing.set(false);
        LocrowAI.LOGGER().info("Install finished!");
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

    public static void cancelInstall() {
        installing.set(false);
        subLabel.set("Canceled");
    }

    public static Path getInstallPath() {
        Path root = FMLPaths.GAMEDIR.get().resolve(MODID);

        return root.resolve("backend");
    }

    public static ProcessBuilder buildScriptProcess(Path dir, String scriptName, String... commands) {
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
