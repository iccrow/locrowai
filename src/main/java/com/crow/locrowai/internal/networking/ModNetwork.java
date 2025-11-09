package com.crow.locrowai.internal.networking;

import com.crow.locrowai.internal.LocrowAI;
import com.crow.locrowai.internal.networking.ExecuteChunkPacket;
import com.crow.locrowai.internal.networking.OffloadErrorPacket;
import com.crow.locrowai.internal.networking.ResultChunkPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

@Mod.EventBusSubscriber(modid = LocrowAI.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
class ModNetwork {
    private static final String PROTOCOL_VERSION = "1";
    static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(LocrowAI.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int id = 0;

    static void register() {
        CHANNEL.registerMessage(id++, ExecuteChunkPacket.class,
                ExecuteChunkPacket::toBytes,
                ExecuteChunkPacket::new,
                ExecuteChunkPacket::handle
        );

        CHANNEL.registerMessage(id++, ResultChunkPacket.class,
                ResultChunkPacket::toBytes,
                ResultChunkPacket::new,
                ResultChunkPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        CHANNEL.registerMessage(id++, OffloadErrorPacket.class,
                OffloadErrorPacket::toBytes,
                OffloadErrorPacket::new,
                OffloadErrorPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
    }

    @SubscribeEvent
    static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
    }
}
