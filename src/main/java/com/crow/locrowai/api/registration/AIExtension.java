package com.crow.locrowai.api.registration;

import com.crow.locrowai.LocrowAI;
import com.crow.locrowai.api.registration.exceptions.MissingManifestException;
import com.crow.locrowai.installer.InstallationManager;
import com.google.gson.Gson;
import net.minecraft.resources.ResourceLocation;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public class AIExtension {

    private static final Gson gson = new Gson();

    private final String id;
    private final String version;
    private final String author;
    private final List<String> tags;
    private final String description;
    private final String MODID;
    private final List<String> requirements;
    private final List<PackageManifest.ModelCard> models;
    private final Set<String> files;
    private final ResourceLocation loc;
    private final ResourceLocation manifest;
    private final ResourceLocation sig;
    private final ResourceLocation key;

    public AIExtension(String MODID, ClassLoader loader,
                       ResourceLocation loc, ResourceLocation securityKey) throws IOException, MissingManifestException {
        this.MODID = MODID;
        this.loc = loc;
        this.key = securityKey;

        String path = loc.getPath().endsWith("/") ?
                "/" + LocrowAI.MODID + "/" + loc.getPath() :
                "/" + LocrowAI.MODID + "/" + loc.getPath() + "/";
        ResourceLocation manLoc = loc.withPath(path + "manifest.json");
        ResourceLocation sig = loc.withPath(path + "manifest.json.sig.b64");

        this.manifest = manLoc;
        this.sig = sig;

        InputStream stream = loader.getResourceAsStream(manLoc.getPath());
        if (stream == null) throw new MissingManifestException(manLoc.toString());

        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));

        PackageManifest manifest = gson.fromJson(reader, PackageManifest.class);
        stream.close();
        reader.close();

        this.id = manifest.id;
        this.version = manifest.version;
        this.author = manifest.author;
        this.tags = manifest.tags;
        this.description = manifest.description;
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

    public ResourceLocation getLoc() {
        return loc;
    }

    public ResourceLocation getManifest() {
        return manifest;
    }

    public ResourceLocation getSig() {
        return sig;
    }

    public ResourceLocation getKey() {
        return key;
    }

    public boolean installed() throws IOException {
        Path path = InstallationManager.getInstallPath();

        File manifest = path.resolve("extensions").resolve(this.id).resolve("manifest.json").toFile();

        if (!manifest.exists()) return false;

        BufferedReader reader = Files.newBufferedReader(manifest.toPath());

        return gson.fromJson(reader, PackageManifest.class).version.equals(this.version);
    }

    @Override
    public String toString() {
        return "AIExtension{" +
                "id='" + id + '\'' +
                ", MODID='" + MODID + '\'' +
                ", requirements=" + requirements +
                ", loc=" + loc +
                ", manifest=" + manifest +
                ", sig=" + sig +
                ", key=" + key +
                '}';
    }
}

