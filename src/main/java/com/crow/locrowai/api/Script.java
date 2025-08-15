package com.crow.locrowai.api;

import com.crow.locrowai.Config;
import com.crow.locrowai.LocrowAI;
import com.crow.locrowai.networking.ExecutePacket;
import com.crow.locrowai.networking.ModNetwork;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class Script {

    private static final Map<UUID, Consumer<JsonObject>> queue = new HashMap<>();

    public static void finish(JsonObject results, UUID uuid) {
        queue.remove(uuid).accept(results);
    }

    public static void execute(String script, Consumer<String> consumer) {
        if (LocrowAI.isSettingUp()) {
            LocrowAI.LOGGER().info("PyEnv has not finished setting up. AI features are currently unavailable.");
            return;
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:8000/run"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(script))
                .build();

        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        client.sendAsync(
                request,
                HttpResponse.BodyHandlers.ofString()
        ).thenAccept(response -> {
            if (response.statusCode() == 200) {
                consumer.accept(response.body());
            } else {
                System.err.println("Failed script execution request: HTTP " + response.statusCode());
            }
        }).exceptionally(ex -> {
            ex.printStackTrace();
            return null;
        });
    }

    private final String blueprint;

    public Script(String blueprint) {
        this.blueprint = blueprint;
    }

    public String getJsonBlueprint() {
        return blueprint;
    }

    public void execute(Consumer<JsonObject> consumer) { // Only run this from server-side.
        if (LocrowAI.isSettingUp()) {
            LocrowAI.LOGGER().info("PyEnv has not finished setting up. AI features are currently unavailable.");
            return;
        }

        if (Config.offloading) {
            UUID jobID = UUID.randomUUID();

            try {
                ModNetwork.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> {
                            PlayerList playerList = ServerLifecycleHooks.getCurrentServer().getPlayerList();
                            for (String name : Config.volunteerNames) {
                                ServerPlayer player = playerList.getPlayerByName(name);
                                if (player != null) {
                                    queue.put(jobID, consumer);
                                    return player;
                                }
                            }
                            LocrowAI.LOGGER().warn("No volunteer players online! AI feature request ignored! Disable offloading or add a new volunteer to fix this.");
                            return null;
                        }),
                        new ExecutePacket(this.blueprint, jobID)
                );
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            Script.execute(this.blueprint, results -> {
                JsonObject obj = JsonParser.parseString(results).getAsJsonObject();
                consumer.accept(obj);
            });
        }
    }
}
