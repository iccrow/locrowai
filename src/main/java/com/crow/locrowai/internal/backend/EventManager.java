package com.crow.locrowai.internal.backend;

import com.crow.locrowai.api.registration.AIRegistry;
import com.crow.locrowai.internal.Config;
import com.crow.locrowai.internal.LocrowAI;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

class EventManager {

    @Mod.EventBusSubscriber(modid = LocrowAI.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    static class ModEvents {

        @SubscribeEvent
        static void init(FMLCommonSetupEvent event) {

            AIRegistry.init();

            InstallationManager.init();

//            LoadManager.load();
        }
    }

    @Mod.EventBusSubscriber(modid = LocrowAI.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    static class ForgeEvents
    {
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
