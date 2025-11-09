package com.crow.locrowai.internal.networking;

import com.crow.locrowai.api.runtime.exceptions.AIRuntimeException;
import com.crow.locrowai.internal.LocrowAI;
import com.crow.locrowai.api.registration.AIRegistry;
import com.crow.locrowai.api.runtime.Script;
import com.crow.locrowai.internal.backend.InstallationManager;
import com.crow.locrowai.internal.backend.LoadManager;
import net.minecraftforge.network.PacketDistributor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

class ExecuteChunkReceiver {

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

    static void onChunk(String MODID, UUID id, int remaining, byte[] data) {
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

            if (LoadManager.isRunning()) {
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
            } else {
                AIRuntimeException err = new AIRuntimeException("AI Backend is not running!");
                ModNetwork.CHANNEL.send(
                        PacketDistributor.SERVER.noArg(),
                        new OffloadErrorPacket(MODID, id, "AIRuntimeException", err.getMessage())
                );
            }
        }
    }
}
