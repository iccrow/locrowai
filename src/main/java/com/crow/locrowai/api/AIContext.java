package com.crow.locrowai.api;

import com.crow.locrowai.LocrowAI;
import com.crow.locrowai.api.registration.AIExtension;
import com.crow.locrowai.api.registration.exceptions.AIRegistrationClosedException;
import com.crow.locrowai.api.registration.exceptions.MissingManifestException;
import com.crow.locrowai.api.registration.exceptions.MissingManifestSignatureException;
import com.crow.locrowai.api.registration.exceptions.MissingSecurityKeyException;
import com.crow.locrowai.api.runtime.Script;
import com.crow.locrowai.api.runtime.exceptions.AIBackendException;
import com.crow.locrowai.api.runtime.exceptions.MissingAIPackagesException;
import com.crow.locrowai.api.runtime.exceptions.UnauthorizedAICallException;
import com.crow.locrowai.config.Config;
import com.crow.locrowai.installer.EnvironmentInstaller;
import com.crow.locrowai.networking.ChunkSender;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class AIContext {

    private final ResourceLocation LOCROW_AI_KEY = ResourceLocation.fromNamespaceAndPath(LocrowAI.MODID,
            LocrowAI.MODID + "/public_key.pem");

    private final List<AIExtension> pendingRegistration = new ArrayList<>();
    private final List<String> declared = new ArrayList<>();
    private boolean registrationComplete = false;
    private final Map<UUID, CompletableFuture<JsonObject>> queue = new HashMap<>();
    private final String MODID;
    private final ClassLoader loader;

    public AIContext(String MODID, ClassLoader loader) {
        this.MODID = MODID;
        this.loader = loader;
    }

    public void registerExtension(ResourceLocation extension) {
        this.registerExtension(extension, LOCROW_AI_KEY);
    }

    public void registerExtension(ResourceLocation extension, ResourceLocation public_key) {
        if (this.registrationComplete) throw new AIRegistrationClosedException(extension.toString());
        if (public_key == null || this.loader.getResource(public_key.getPath()) == null)
            throw new MissingSecurityKeyException(extension.toString());

        String path = extension.getPath().endsWith("/") ? extension.getPath() : "/" + extension.getPath();
        ResourceLocation manifest = extension.withPath(path + "manifest.json");
        ResourceLocation sig = extension.withPath(path + "manifest.json.sig.b64");

        if (this.loader.getResource(manifest.getPath()) == null)
            throw new MissingManifestException(extension.toString());
        if (this.loader.getResource(sig.getPath()) == null)
            throw new MissingManifestSignatureException(extension.toString());

        this.pendingRegistration.add(new AIExtension(extension, manifest, sig, public_key));
    }

    public void declareExtension(String id) {
        if (this.registrationComplete) throw new AIRegistrationClosedException(id);

        this.declared.add(id);
    }

    public List<AIExtension> finishRegistration() {
        this.registrationComplete = true;

        return this.pendingRegistration;
    }

    public boolean isCallProhibited(String callID) {
        return !declared.contains(callID.split("/")[1]);
    }

    public CompletableFuture<JsonObject> execute(Script script) {
        CompletableFuture<JsonObject> future = new CompletableFuture<>();

        boolean installed;
        try {
            installed = EnvironmentInstaller.installed();
        } catch (IOException e) {
            future.completeExceptionally(new MissingAIPackagesException());
            return future;
        }

        if (!installed) {
            future.completeExceptionally(new MissingAIPackagesException());
            return future;
        }

        if (script.getCallIDs() != null) {
            for (String callID : script.getCallIDs()) {
                if (isCallProhibited(callID)) {
                    future.completeExceptionally(new UnauthorizedAICallException(callID));
                    return future;
                }
            }
        }

        if (Config.offloading) {
            UUID jobID = UUID.randomUUID();

            queue.put(jobID, future);
            try {
                ChunkSender.sendExecute(script.getJsonBlueprint(), MODID, jobID);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        } else {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:8000/run"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(script.getJsonBlueprint()))
                    .build();

            HttpClient client = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .build();

            client.sendAsync(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            ).thenAccept(response -> {
                if (response.statusCode() == 200) {
                    JsonObject obj = JsonParser.parseString(response.body()).getAsJsonObject();
                    future.complete(obj);
                } else {
                    future.completeExceptionally(new AIBackendException("HTTP error code " + response.statusCode()));
                }
            }).exceptionally(ex -> {
                future.completeExceptionally(new AIBackendException(ex.getMessage()));
                return null;
            });
        }
        return future;
    }

    public void finish(JsonObject results, UUID jobID) {
        queue.remove(jobID).complete(results);
    }

    public void error(Throwable err, UUID jobID) {
        queue.remove(jobID).completeExceptionally(err);
    }
}
