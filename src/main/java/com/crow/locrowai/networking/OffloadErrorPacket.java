package com.crow.locrowai.networking;

import com.crow.locrowai.api.registration.AIRegistry;
import com.crow.locrowai.api.runtime.exceptions.OffloadedRuntimeException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class OffloadErrorPacket {
    private final String MODID;
    private final UUID uuid;
    private final String errorType;
    private final String err;

    public OffloadErrorPacket(String MODID, UUID uuid, String errorType, String err) {
        this.MODID = MODID;
        this.uuid = uuid;
        this.errorType = errorType;
        this.err = err;
    }

    public OffloadErrorPacket(FriendlyByteBuf buf) {
        this.MODID = buf.readUtf();
        this.uuid = buf.readUUID();
        this.errorType = buf.readUtf();
        this.err = buf.readUtf();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(MODID);
        buf.writeUUID(uuid);
        buf.writeUtf(errorType);
        buf.writeUtf(err);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            AIRegistry.getContext(MODID).error(new OffloadedRuntimeException(errorType, err), uuid);
        });
        ctx.get().setPacketHandled(true);
    }
}
