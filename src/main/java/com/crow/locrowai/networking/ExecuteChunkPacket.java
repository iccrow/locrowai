package com.crow.locrowai.networking;

import com.crow.locrowai.LocrowAI;
import com.crow.locrowai.api.Script;
import net.minecraftforge.network.NetworkEvent;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class ExecuteChunkPacket {
    private final UUID uuid;
    private final int remaining;
    private final byte[] data;

    public ExecuteChunkPacket(UUID uuid, int remaining, byte[] data) {
        this.uuid = uuid;
        this.remaining = remaining;
        this.data = data;
    }

    public ExecuteChunkPacket(net.minecraft.network.FriendlyByteBuf buf) {
        this.uuid = buf.readUUID();
        this.remaining = buf.readVarInt();
        this.data = buf.readByteArray();
    }

    public void toBytes(net.minecraft.network.FriendlyByteBuf buf) {
        buf.writeUUID(uuid);
        buf.writeVarInt(remaining);
        buf.writeByteArray(data);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ExecuteChunkReceiver.onChunk(uuid, remaining, data));
        ctx.get().setPacketHandled(true);
    }
}
