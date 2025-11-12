package com.crow.locrowai.api.registration;

import com.crow.locrowai.api.registration.exceptions.MissingManifestException;
import com.crow.locrowai.api.registration.exceptions.MissingManifestSignatureException;
import com.crow.locrowai.api.registration.exceptions.MissingSecurityKeyException;
import com.crow.locrowai.internal.LocrowAI;
import com.crow.locrowai.internal.backend.SecurityManager;
import com.google.gson.Gson;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.util.*;

public class PackageManifest {
    public String id = "example";
    public String version = "0.0.0";
    public String description = "What a nice description you got there!";
    public String author = "The man himself";
    public List<String> tags = List.of("Very Cool!");
    public String source_code; // Raw url for source code. Source code must be structured the same to jar packing.

    public List<String> requirements = new ArrayList<>(); // Auto-generated extension Python requirements.
    public List<ModelCard> models = new ArrayList<>();
    public Map<String, String> hashes = new HashMap<>(); // Auto-generated file hashes (both for versioning and security).

    public static class ModelCard {
        public String repo;
        public String revision = "main";
        public String filename;
        public String rename;
        public String model_folder;
        public ExtractOptions extract_options = new ExtractOptions();

        public static class ExtractOptions {
            public boolean enabled = false;
            public Map<String, String> renames = new HashMap<>();
        }

        public static ModelCard of(String repo) {
            ModelCard card = new ModelCard();
            card.repo = repo;
            return card;
        }

        public ModelCard revision(String revision) {
            this.revision = revision;
            return this;
        }

        public ModelCard filename(String filename) {
            this.filename = filename;
            return this;
        }

        public ModelCard rename(String rename) {
            this.rename = rename;
            return this;
        }

        public ModelCard modelFolder(String model_folder) {
            this.model_folder = model_folder;
            return this;
        }

        public ModelCard extract() {
            this.extract_options.enabled = true;
            return this;
        }

        public ModelCard extract(Map<String, String> renames) {
            this.extract_options.enabled = true;
            this.extract_options.renames = renames;
            return this;
        }
    }

    private static final Gson gson = new Gson();

    public static PackageManifest fetch(ClassLoader loader, Path path) throws IOException {
        if (path == null)
            throw new IllegalArgumentException("Manifest path cannot be null.");

        if (path.isAbsolute())
            throw new IllegalArgumentException("Manifest path must be relative (resource path expected).");

        if (loader.getResource(path.toString()) == null)
            throw new MissingManifestException(path.toString());

        InputStream manStream = loader.getResourceAsStream(path.toString());
        if (manStream == null) throw new MissingManifestException(path.toString(), "none");
        BufferedReader manReader = new BufferedReader(new InputStreamReader(manStream));

        PackageManifest manifest = gson.fromJson(manReader, PackageManifest.class);
        manReader.close();
        manStream.close();

        return manifest;
    }

    public static PackageManifest fetch(Path path, PublicKey key) throws IOException {
        if (path == null)
            throw new IllegalArgumentException("Manifest path cannot be null.");

        if (!path.isAbsolute())
            throw new IllegalArgumentException("Manifest path must be absolute (disk path expected).");

        if (key == null)
            throw new IllegalArgumentException("Security key cannot be null.");

        Path sig = path.resolveSibling(path.getFileName() + ".sig.b64");

        if (!Files.exists(path))
            throw new MissingManifestException(path.toString());
        if (!Files.exists(sig))
            throw new MissingManifestSignatureException(path.toString());


        byte[] manBytes = Files.readAllBytes(path);
        InputStreamReader reader = new InputStreamReader(
                new ByteArrayInputStream(manBytes), StandardCharsets.UTF_8
        );

        PackageManifest manifest = gson.fromJson(reader, PackageManifest.class);
        reader.close();


        byte[] sigBytes = Files.readAllBytes(sig);
        String sigB64 = new String(sigBytes).replaceAll("\\s+", "");
        sigBytes = Base64.getDecoder().decode(sigB64);

        if (!SecurityManager.verifySignature(manBytes, sigBytes, key))
            throw new SecurityException("Manifest signature verification failed! '" + path + "' may have been tampered with.");

        return manifest;
    }
}
