package com.crow.locrowai;

import com.crow.locrowai.loader.*;
import com.crow.locrowai.networking.ModNetwork;
import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(LocrowAI.MODID)
public class LocrowAI
{
    // Define mod id in a common place for everything to reference
    public static final String MODID = "locrowai";
    public static final String PY_VERSION = "0.1.1";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();
    private static Process process;
    private static Thread outThread;
    private static Thread setupThread;
    private static final AtomicBoolean awaitingStartup = new AtomicBoolean(false);

    public static boolean isSettingUp() {
        return setupThread != null && setupThread.isAlive();
    }
    public static boolean isRunning() {
        return process != null && process.isAlive();
    }

    private static SystemProbe.ProbeResult probeResult;

    public LocrowAI(FMLJavaModLoadingContext context)
    {
        IEventBus modEventBus = context.getModEventBus();

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);

        context.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {
        event.enqueueWork(ModNetwork::register);
        probeResult = SystemProbe.probe();
//        System.out.println(result.os().name());
//        System.out.println(result.os().arch());
//        System.out.println(result.os().bits() + "\n\n");
//        for (SystemProbe.GpuInfo card : result.gpus()) {
//            System.out.println(card.vendor());
//            System.out.println(card.model());
//            System.out.println(card.deviceId() + "\n\n");
//        }

        setupThread = new Thread(() -> {
            try {
                if (!PackageLoader.installed()) {
                    if (FMLEnvironment.dist.isDedicatedServer() && Config.offloading) {
                        return;
                    }

                    String base = "https://huggingface.co/iccrow/minecraft-locrowai/resolve/main/builds/";
                    String uri = URIBuilder.pythonBuildUrl(base, PY_VERSION, probeResult);
                    LOGGER.info("[Setup] Package location: " + uri);
                    PackageLoader.downloadAndUnzip(uri, FMLPaths.GAMEDIR.get());
                    Path pyPath = FMLPaths.GAMEDIR.get().resolve("locrowai").resolve(PY_VERSION);

                    // Build the process: use "cmd /c" to run a .bat file on Windows
                    ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", pyPath.resolve("setup.bat").toString());

                    // Redirect stdout + stderr into the log file
                    pb.directory(pyPath.toFile());
                    pb.redirectErrorStream(true); // merge stderr into stdout
                    pb.redirectOutput(ProcessBuilder.Redirect.to(pyPath.resolve("setup_log.txt").toFile()));

                    // Start and wait for exit
                    LOGGER.info("[Setup] Installing Python dependencies");
                    Process p = pb.start();
                    int exitCode = p.waitFor();
                    if (awaitingStartup.get()) {
                        outThread.start();
                        awaitingStartup.set(false);
                    }

                    if (FMLEnvironment.dist.isClient()) SetupToast.done();
                    LOGGER.info("[Setup] PyEnv install is complete with exit code {}! Log saved to {}.", exitCode, pyPath.resolve("setup_log.txt"));
                }
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        setupThread.start();
    }

    public static void startAIProcess(Runnable runnable) {
        ProcessBuilder builder = ServerBuilder.builder(probeResult, FMLPaths.GAMEDIR.get().resolve("locrowai").resolve(PY_VERSION));

        builder.redirectErrorStream(true);

        outThread = new Thread(() -> {
            try {
                process = builder.start();
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()));

                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("Application startup complete.") && runnable != null) {
                        runnable.run();
                    }
                    LOGGER.info("[Python] " + line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }, "LocrowAI-Python-Output");

        outThread.setDaemon(true);

        if (isSettingUp()) {
            awaitingStartup.set(true);
            LOGGER.info("[Setup] PyEnv not fully setup yet. AI features will be temporarily unavailable.");
        } else {
            outThread.start();
            awaitingStartup.set(false);
        }
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) throws IOException {
        if (Config.offloading) return;

        startAIProcess(null);
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        if (isRunning()) {
            process.destroy();
            outThread.interrupt();
        }
    }
    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {

        }
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class ClientForgeEvents
    {
        @SubscribeEvent
        public static void onScreenStart(ScreenEvent.Init.Post event) {
            if (isSettingUp()) {
                SetupToast.warn();
            }
        }

        @SubscribeEvent
        public static void leaveServer(ClientPlayerNetworkEvent.LoggingOut event) {
            if (isRunning()) {
                process.destroy();
                outThread.interrupt();
            }
        }
    }

    public static Logger LOGGER() {
        return LOGGER;
    }
}
