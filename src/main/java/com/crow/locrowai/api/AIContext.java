package com.crow.locrowai.api;

import com.crow.locrowai.api.registration.AIRegistry;
import com.crow.locrowai.api.registration.PackageManifest;
import com.crow.locrowai.internal.LocrowAI;
import com.crow.locrowai.api.registration.AIExtension;
import com.crow.locrowai.api.registration.exceptions.*;
import com.crow.locrowai.api.runtime.Script;
import com.crow.locrowai.api.runtime.exceptions.AIBackendException;
import com.crow.locrowai.api.runtime.exceptions.MissingAIPackagesException;
import com.crow.locrowai.api.runtime.exceptions.UnauthorizedAICallException;
import com.crow.locrowai.internal.Config;
import com.crow.locrowai.internal.backend.InstallationManager;
import com.crow.locrowai.internal.backend.SecurityManager;
import com.crow.locrowai.internal.backend.LoadManager;
import com.crow.locrowai.internal.networking.ChunkSender;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class AIContext {

    public record RegistrationResults(List<AIExtension> registered, List<String> declared, List<PackageManifest.ModelCard> modelCards) {}

    private final Path LOCROW_AI_KEY = Path.of(LocrowAI.MODID, "official_key.pem");

    private final List<AIExtension> pendingRegistration = new ArrayList<>();
    private boolean registerChecked = false;
    private final List<String> declared = new ArrayList<>();
    private final List<PackageManifest.ModelCard> modelCards = new ArrayList<>();
    private boolean registrationComplete = false;
    private final Map<UUID, CompletableFuture<JsonObject>> queue = new HashMap<>();
    private final String MODID;
    private final ClassLoader loader;

    public AIContext(String MODID, ClassLoader loader) {
        this.MODID = MODID;
        this.loader = loader;
    }

    public void registerExtension(Path source) throws AIRegistrationException {
        this.registerExtension(source, LOCROW_AI_KEY);
    }

    public void registerExtension(Path source, Path public_key) throws AIRegistrationException {
        if (this.registrationComplete) throw new AIRegistrationClosedException(source.toString());

        if (source == null)
            throw new IllegalArgumentException("Extension source path cannot be null.");
        if (public_key == null)
            throw new IllegalArgumentException("Extension security key cannot be null.");

        if (source.isAbsolute())
            throw new IllegalArgumentException("Extension source path must be relative (resource path expected).");
        if (public_key.isAbsolute())
            throw new IllegalArgumentException("Extension source path must be relative (resource path expected).");


        Path manifest = source.resolve("manifest.json");
        Path sig = source.resolve("manifest.json.sig.b64");

        if (this.loader.getResource(public_key.toString()) == null)
            throw new MissingSecurityKeyException(public_key.toString());
        if (this.loader.getResource(manifest.toString()) == null)
            throw new MissingManifestException(source.toString());
        if (this.loader.getResource(sig.toString()) == null)
            throw new MissingManifestSignatureException(source.toString());

        try {
            this.pendingRegistration.add(new AIExtension(this.MODID, this.loader, source, SecurityManager.getKey(new SecurityManager.KeyMap(loader, public_key))));
        } catch (IOException ignored) {

        }
    }

    public void registerModel(PackageManifest.ModelCard modelCard) {
        if (this.registrationComplete) throw new AIRegistrationClosedException(modelCard.repo);

        this.modelCards.add(modelCard);
    }

    public void declareExtension(String id) {
        if (this.registrationComplete) throw new AIRegistrationClosedException(id);

        this.declared.add(id);
    }

    public RegistrationResults finishRegistration() {
        this.registrationComplete = true;

        return new RegistrationResults(this.pendingRegistration, this.declared, this.modelCards);
    }

    public boolean isCallProhibited(String callID) {
        if (!registerChecked) {
            declared.retainAll(AIRegistry.getDeclared());
            registerChecked = true;
        }

        return !declared.contains(callID.split("/")[1]);
    }

    public CompletableFuture<JsonObject> execute(Script script) {
        CompletableFuture<JsonObject> future = new CompletableFuture<>();

        boolean installed = InstallationManager.isFullyInstalled();

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
                    .uri(URI.create("http://127.0.0.1:" + LoadManager.getPort() + "/run"))
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
