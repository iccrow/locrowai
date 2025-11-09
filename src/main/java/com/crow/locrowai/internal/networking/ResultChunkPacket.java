package com.crow.locrowai.internal.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

class ResultChunkPacket {
    private final String MODID;
    private final UUID uuid;
    private final int remaining;
    private final byte[] data;

    ResultChunkPacket(String MODID, UUID uuid, int remaining, byte[] data) {
        this.MODID = MODID;
        this.uuid = uuid;
        this.remaining = remaining;
        this.data = data;
    }

    ResultChunkPacket(FriendlyByteBuf buf) {
        this.MODID = buf.readUtf();
        this.uuid = buf.readUUID();
        this.remaining = buf.readVarInt();
        this.data = buf.readByteArray();
    }

    void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(MODID);
        buf.writeUUID(uuid);
        buf.writeVarInt(remaining);
        buf.writeByteArray(data);
    }

    void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ResultChunkReceiver.onChunk(MODID, uuid, remaining, data);
        });
        ctx.get().setPacketHandled(true);
    }
}
