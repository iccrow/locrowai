package com.crow.locrowai.networking;

import com.crow.locrowai.api.Script;
import com.google.gson.JsonParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class ResultChunkPacket {
    private final UUID uuid;
    private final int remaining;
    private final byte[] data;

    public ResultChunkPacket(UUID uuid, int remaining, byte[] data) {
        this.uuid = uuid;
        this.remaining = remaining;
        this.data = data;
    }

    public ResultChunkPacket(FriendlyByteBuf buf) {
        this.uuid = buf.readUUID();
        this.remaining = buf.readVarInt();
        this.data = buf.readByteArray();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(uuid);
        buf.writeVarInt(remaining);
        buf.writeByteArray(data);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ResultChunkReceiver.onChunk(uuid, remaining, data);
        });
        ctx.get().setPacketHandled(true);
    }

    public static void send(UUID uuid, String json) {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        try (ByteArrayOutputStream bout = new ByteArrayOutputStream();
             GZIPOutputStream gzOut = new GZIPOutputStream(bout)) {
            gzOut.write(bytes);
            gzOut.finish();
            bytes = bout.toByteArray();
        } catch (Exception ignored) {

        }

        int chunkSize = 24_000;
        int total = (bytes.length + chunkSize - 1)/chunkSize;

        for (int i = 0; i < total; i++) {
            int off = i * chunkSize;
            int len = Math.min(chunkSize, bytes.length - off);
            byte[] slice = Arrays.copyOfRange(bytes, off, off + len);

            ModNetwork.CHANNEL.send(
                    PacketDistributor.SERVER.noArg(),
                    new ResultChunkPacket(uuid, total - i, slice)
            );
        }
    }
}
