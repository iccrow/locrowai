package com.crow.locrowai.networking;

import com.crow.locrowai.LocrowAI;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public class ModNetwork {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(LocrowAI.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int id = 0;

    public static void register() {
        CHANNEL.registerMessage(id++, ExecuteChunkPacket.class,
                ExecuteChunkPacket::toBytes,
                ExecuteChunkPacket::new,
                ExecuteChunkPacket::handle
        );

        CHANNEL.registerMessage(id++, ResultChunkPacket.class,
                ResultChunkPacket::toBytes,
                ResultChunkPacket::new,
                ResultChunkPacket::handle,
                Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
        );
    }
}
