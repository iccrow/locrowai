package com.crow.locrowai.internal.backend;

import com.crow.locrowai.api.registration.AIRegistry;
import com.crow.locrowai.internal.Config;
import com.crow.locrowai.internal.LocrowAI;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

class EventManager {

    static final AtomicBoolean awaitingTamperWarning = new AtomicBoolean(false);
    static final AtomicBoolean awaitingThirdPartyWarning = new AtomicBoolean(false);
    static final AtomicBoolean screenReady = new AtomicBoolean(false);

    @Mod.EventBusSubscriber(modid = LocrowAI.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    static class ModEvents {

        @SubscribeEvent
        static void init(FMLCommonSetupEvent event) {

            AIRegistry.init();

            if (!InstallationManager.isFullyInstalled())
                InstallationManager.init();
            else {
                Thread thread = new Thread(() -> {
                    try {
                        InstallationManager.installing.set(true);
                        InstallationManager.currentStageIndex.set(4);
                        InstallationManager.stagePercent.set(0);
                        if (!EnvironmentInstaller.verify()) {
                            InstallationManager.hadError.set(true);
                            LocrowAI.LOGGER().error("Failed to verify backend core. Files may have been tampered with!");
                            DistExecutor.safeRunWhenOn(Dist.CLIENT, LoadManager::showTamperWarning);
                            return;
                        }
                        InstallationManager.currentStageIndex.set(5);
                        InstallationManager.stagePercent.set(0);
                        if (!ExtensionInstaller.verify()) {
                            InstallationManager.hadError.set(true);
                            LocrowAI.LOGGER().error("Failed to verify AI extensions. Files may have been tampered with!");
                            DistExecutor.safeRunWhenOn(Dist.CLIENT, LoadManager::showTamperWarning);
                            return;
                        }
                        InstallationManager.installing.set(false);
                        Minecraft mc = Minecraft.getInstance();
//                        mc.execute(() -> {
//                            mc.setScreen(new TitleOverlayScreen(mc.screen, Component.literal("WARNING: THIS IS A DRILL!!")));
//                        });
                        LoadManager.load();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }, "Locrow-AI-Load-Manager");

                thread.start();
            }
        }
    }

    @Mod.EventBusSubscriber(modid = LocrowAI.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    static class ForgeEvents
    {
        @SubscribeEvent
        static void onScreenStart(ScreenEvent.Init.Post event) {
            Minecraft mc = Minecraft.getInstance();
            screenReady.set(true);
            if (awaitingTamperWarning.get()) {
                mc.execute(() -> {
                    DistExecutor.safeRunWhenOn(Dist.CLIENT, LoadManager::showTamperWarning);
                });
            }
        }
        @SubscribeEvent
        public static void leaveServer(ClientPlayerNetworkEvent.LoggingOut event) {
            LoadManager.freeze().thenAccept(res -> LocrowAI.LOGGER().info("FREEZE TOOK: {}", res));
        }

        @SubscribeEvent
        public void onServerStarting(ServerStartingEvent event) {
            if (Config.offloading) return;

            if (!LoadManager.isRunning())
                LoadManager.load();

        }
    }
}
