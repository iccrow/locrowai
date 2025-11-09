package com.crow.locrowai.internal.networking;

import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

class ExecuteChunkPacket {
    private final String MODID;
    private final UUID uuid;
    private final int remaining;
    private final byte[] data;

    ExecuteChunkPacket(String MODID, UUID uuid, int remaining, byte[] data) {
        this.MODID = MODID;
        this.uuid = uuid;
        this.remaining = remaining;
        this.data = data;
    }

    ExecuteChunkPacket(net.minecraft.network.FriendlyByteBuf buf) {
        this.MODID = buf.readUtf();
        this.uuid = buf.readUUID();
        this.remaining = buf.readVarInt();
        this.data = buf.readByteArray();
    }

    void toBytes(net.minecraft.network.FriendlyByteBuf buf) {
        buf.writeUtf(MODID);
        buf.writeUUID(uuid);
        buf.writeVarInt(remaining);
        buf.writeByteArray(data);
    }

    void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ExecuteChunkReceiver.onChunk(MODID, uuid, remaining, data));
        ctx.get().setPacketHandled(true);
    }
}
