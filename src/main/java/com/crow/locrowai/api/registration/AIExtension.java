package com.crow.locrowai.api.registration;

import com.crow.locrowai.api.registration.exceptions.MissingManifestSignatureException;
import com.crow.locrowai.api.registration.exceptions.MissingSecurityKeyException;
import com.crow.locrowai.internal.LocrowAI;
import com.crow.locrowai.api.registration.exceptions.MissingManifestException;
import com.crow.locrowai.internal.backend.InstallationManager;
import com.google.gson.Gson;
import net.minecraft.resources.ResourceLocation;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Set;

public class AIExtension {

    private static final Gson gson = new Gson();

    private final String id;
    private final String version;
    private final String author;
    private final List<String> tags;
    private final String description;
    private final String sourceCode;
    private final String MODID;
    private final List<String> requirements;
    private final List<PackageManifest.ModelCard> models;
    private final Set<String> files;
    private final Path source;
    private final Path manifest;
    private final Path sig;
    private final PublicKey key;

    public AIExtension(String MODID, ClassLoader loader,
                       Path source, PublicKey key) throws IOException {
        this.MODID = MODID;
        this.source = source;

        this.key = key;

        this.manifest = this.source.resolve("manifest.json");
        this.sig = this.source.resolve("manifest.json.sig.b64");

        PackageManifest manifest = PackageManifest.fetch(loader, this.manifest);

        this.id = manifest.id;
        this.version = manifest.version;
        this.author = manifest.author;
        this.tags = manifest.tags;
        this.description = manifest.description;
        this.sourceCode = manifest.source_code;
        this.requirements = manifest.requirements;
        this.models = manifest.models;
        this.files = manifest.hashes.keySet();
    }

    public String getId() {
        return id;
    }

    public String getMODID() {
        return MODID;
    }

    public List<String> getRequirements() {
        return requirements;
    }

    public List<PackageManifest.ModelCard> getModels() {
        return this.models;
    }

    public Set<String> getFiles() {
        return this.files;
    }

    public Path getSource() {
        return source;
    }

    public Path getManifest() {
        return manifest;
    }

    public Path getSig() {
        return sig;
    }

    public PublicKey getKey() {
        return key;
    }

    public boolean installed() throws IOException {
        Path backend = InstallationManager.getBackendPath();
        Path ext = backend.resolve("extensions").resolve(this.id);

        try {
            PackageManifest manifest = PackageManifest.fetch(ext.resolve("manifest.json"), this.getKey());
            return manifest.version.equals(this.version);
        } catch (MissingManifestException | MissingManifestSignatureException | MissingSecurityKeyException | SecurityException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public String toString() {
        return "AIExtension{" +
                "id='" + id + '\'' +
                ", MODID='" + MODID + '\'' +
                ", requirements=" + requirements +
                ", source=" + source +
                ", manifest=" + manifest +
                ", sig=" + sig +
                ", key=" + key +
                '}';
    }
}

