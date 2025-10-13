package com.crow.locrowai;

import com.crow.locrowai.api.APIUsageExample;
import com.crow.locrowai.config.AIPackageManagerScreen;
import com.crow.locrowai.config.Config;
import com.crow.locrowai.loader.*;
import com.crow.locrowai.networking.ModNetwork;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
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
import java.util.concurrent.atomic.AtomicBoolean;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(LocrowAI.MODID)
public class LocrowAI
{
    // Define mod id in a common place for everything to reference
    public static final String MODID = "locrowai";
    public static final String PY_TEMPLATE = "0.3.x";
    private static String PY_VERSION = PY_TEMPLATE;

    private static final String base = "https://huggingface.co/iccrow/minecraft-locrowai/resolve/main/builds/";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();
    private static Process process;
    public static Thread outThread;
    private static Thread setupThread;
    public static final AtomicBoolean awaitingStartup = new AtomicBoolean(false);

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

        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        if (FMLEnvironment.dist.isClient()) {
            context.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new ConfigScreenHandler.ConfigScreenFactory((mc, prevScreen) -> new AIPackageManagerScreen(prevScreen, base, probeResult)));
        }
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
        try {
            PY_VERSION = URIBuilder.fetchLatestVersion(base, PY_VERSION, probeResult);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        setupThread = new Thread(() -> {
            PackageLoader.install(base, probeResult);
        }, "Locrow-AI-Python-Installer");

        setupThread.start();

        Runtime.getRuntime().addShutdownHook(new Thread(LocrowAI::stopAIProcess));
    }

    public static void startAIProcess(Runnable runnable) {
        ProcessBuilder builder = ServerBuilder.builder(probeResult, FMLPaths.GAMEDIR.get().resolve(MODID).resolve(PY_VERSION));

        builder.redirectErrorStream(true);

        outThread = new Thread(() -> {
            try {
                process = builder.start();
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()));

                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("Application startup complete.")) {
                        SetupToast.warmedUp();
                        if (runnable != null)
                            runnable.run();
                    }
                    LOGGER.info(line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }, "Locrow-AI-Python-Output");

        outThread.setDaemon(true);

        if (isSettingUp()) {
            awaitingStartup.set(true);
            LOGGER.info("PyEnv not fully setup yet. AI features will be temporarily unavailable.");
        } else {
            outThread.start();
            awaitingStartup.set(false);
        }
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        if (Config.offloading) return;

        startAIProcess(null);
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        stopAIProcess();
        awaitingStartup.set(false);
    }
    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            APIUsageExample.run();
        }
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class ClientForgeEvents
    {
        @SubscribeEvent
        public static void leaveServer(ClientPlayerNetworkEvent.LoggingOut event) {
            stopAIProcess();
            awaitingStartup.set(false);
        }
    }

    public static Logger LOGGER() {
        return LOGGER;
    }
    public static String PY_VERSION() {
        return PY_VERSION;
    }
    public static void stopAIProcess() {
        if (isRunning()) {
            process.descendants().forEach(ProcessHandle::destroy);
            process.destroy();
            outThread.interrupt();
        }
    }
}
