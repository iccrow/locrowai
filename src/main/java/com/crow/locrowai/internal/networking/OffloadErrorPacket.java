package com.crow.locrowai.internal.networking;

import com.crow.locrowai.api.registration.AIRegistry;
import com.crow.locrowai.api.runtime.exceptions.OffloadedRuntimeException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

class OffloadErrorPacket {
    private final String MODID;
    private final UUID uuid;
    private final String errorType;
    private final String err;

    OffloadErrorPacket(String MODID, UUID uuid, String errorType, String err) {
        this.MODID = MODID;
        this.uuid = uuid;
        this.errorType = errorType;
        this.err = err;
    }

    OffloadErrorPacket(FriendlyByteBuf buf) {
        this.MODID = buf.readUtf();
        this.uuid = buf.readUUID();
        this.errorType = buf.readUtf();
        this.err = buf.readUtf();
    }

    void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(MODID);
        buf.writeUUID(uuid);
        buf.writeUtf(errorType);
        buf.writeUtf(err);
    }

    void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            AIRegistry.getContext(MODID).error(new OffloadedRuntimeException(errorType, err), uuid);
        });
        ctx.get().setPacketHandled(true);
    }
}
