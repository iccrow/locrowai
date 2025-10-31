package com.crow.locrowai.networking;

import com.crow.locrowai.LocrowAI;
import com.crow.locrowai.api.registration.AIRegistry;
import com.crow.locrowai.api.runtime.Script;
import net.minecraft.client.Minecraft;
import net.minecraftforge.network.PacketDistributor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

public class ExecuteChunkReceiver {

    private static final Map<UUID, Assembly> INFLIGHT = new ConcurrentHashMap<>();

    private static final class Assembly {
        final int total;
        final byte[][] chunks;
        int received;

        Assembly(int total) {
            this.total = total;
            this.chunks = new byte[total][];
            this.received = 0;
        }
    }

    public static void onChunk(String MODID, UUID id, int remaining, byte[] data) {
        Assembly asm = INFLIGHT.compute(id, (k, v) -> v == null ? new Assembly(remaining) : v);

        if (asm.chunks[asm.total - remaining] == null) {
            asm.chunks[asm.total - remaining] = data;
            asm.received++;
        }

        if (asm.received == asm.total) {
            INFLIGHT.remove(id);

            int totalBytes = Arrays.stream(asm.chunks).mapToInt(p -> p.length).sum();
            byte[] joined = new byte[totalBytes];
            int pos = 0;
            for (byte[] p : asm.chunks) {
                System.arraycopy(p, 0, joined, pos, p.length);
                pos += p.length;
            }

            byte[] payload = joined;

            try (GZIPInputStream gin = new GZIPInputStream(new ByteArrayInputStream(joined))) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                gin.transferTo(out);
                payload = out.toByteArray();
            } catch (Exception e) {
                LocrowAI.LOGGER().error("GUnzip failed for {}", id, e);
                return;
            }

            String script = new String(payload, StandardCharsets.UTF_8);

            if (!LocrowAI.isRunning() && !LocrowAI.isSettingUp()) {
                LocrowAI.startAIProcess(() -> AIRegistry.getContext(MODID).execute(new Script(null, script))
                                .thenAccept(results ->
                                        ChunkSender.sendResult(MODID, id, results.getAsString()))
                                .exceptionally(err -> {
                                    ModNetwork.CHANNEL.send(
                                            PacketDistributor.SERVER.noArg(),
                                            new OffloadErrorPacket(MODID, id, err.getClass().getSimpleName(), err.getMessage())
                                    );
                                    return null;
                                }));
            } else {
                AIRegistry.getContext(MODID).execute(new Script(null, script))
                        .thenAccept(results ->
                                ChunkSender.sendResult(MODID, id, results.getAsString()))
                        .exceptionally(err -> {
                            ModNetwork.CHANNEL.send(
                                    PacketDistributor.SERVER.noArg(),
                                    new OffloadErrorPacket(MODID, id, err.getClass().getSimpleName(), err.getMessage())
                            );
                            return null;
                        });
            }
        }
    }
}
