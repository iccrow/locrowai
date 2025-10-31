package com.crow.locrowai.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.zip.GZIPOutputStream;

public class ResultChunkPacket {
    private final String MODID;
    private final UUID uuid;
    private final int remaining;
    private final byte[] data;

    public ResultChunkPacket(String MODID, UUID uuid, int remaining, byte[] data) {
        this.MODID = MODID;
        this.uuid = uuid;
        this.remaining = remaining;
        this.data = data;
    }

    public ResultChunkPacket(FriendlyByteBuf buf) {
        this.MODID = buf.readUtf();
        this.uuid = buf.readUUID();
        this.remaining = buf.readVarInt();
        this.data = buf.readByteArray();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(MODID);
        buf.writeUUID(uuid);
        buf.writeVarInt(remaining);
        buf.writeByteArray(data);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ResultChunkReceiver.onChunk(MODID, uuid, remaining, data);
        });
        ctx.get().setPacketHandled(true);
    }
}
