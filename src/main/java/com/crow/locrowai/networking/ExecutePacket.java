package com.crow.locrowai.networking;

import com.crow.locrowai.LocrowAI;
import com.crow.locrowai.api.Script;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class ExecutePacket {
    private final String script;
    private final UUID uuid;

    public ExecutePacket(String script, UUID uuid) {
        this.script = script;
        this.uuid = uuid;
    }

    public ExecutePacket(FriendlyByteBuf buf) {
        this.script = buf.readUtf();
        this.uuid = buf.readUUID();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(script);
        buf.writeUUID(uuid);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (!LocrowAI.isRunning() && !LocrowAI.isSettingUp()) {
                LocrowAI.startAIProcess(() -> {
                    Script.execute(script, results -> {
                        net.minecraft.client.Minecraft.getInstance().execute(() -> {
                            ResultSender.send(uuid, results);
                        });
                    });
                });
            } else {
                Script.execute(script, results -> {
                    net.minecraft.client.Minecraft.getInstance().execute(() -> {
                        ResultSender.send(uuid, results);
                    });
                });
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
