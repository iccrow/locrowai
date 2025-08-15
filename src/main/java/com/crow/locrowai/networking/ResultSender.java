package com.crow.locrowai.networking;

import net.minecraftforge.network.PacketDistributor;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

public class ResultSender {

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
