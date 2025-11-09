package com.crow.locrowai.internal.networking;

import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.ApiStatus;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

@ApiStatus.Internal
public class ChunkSender {

    public static void sendResult(String MODID, UUID uuid, String json) {
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
                    new ResultChunkPacket(MODID, uuid, total - i, slice)
            );
        }
    }

    /**
     * Compress and send a large script as chunks to the server.
     */
    public static void sendExecute(String script, String MODID, UUID uuid) {
        byte[] bytes = script.getBytes(StandardCharsets.UTF_8);

        // Compress using GZIP
        try (ByteArrayOutputStream bout = new ByteArrayOutputStream();
             GZIPOutputStream gzOut = new GZIPOutputStream(bout)) {
            gzOut.write(bytes);
            gzOut.finish();
            bytes = bout.toByteArray();
        } catch (Exception ignored) {}

        // Split into chunks
        int chunkSize = 24_000; // same as ResultSender
        int total = (bytes.length + chunkSize - 1) / chunkSize;

        for (int i = 0; i < total; i++) {
            int off = i * chunkSize;
            int len = Math.min(chunkSize, bytes.length - off);
            byte[] slice = Arrays.copyOfRange(bytes, off, off + len);

            // Send each chunk
            ModNetwork.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor.SERVER.noArg(),
                    new ExecuteChunkPacket(MODID, uuid, total - i, slice)
            );
        }
    }
}
