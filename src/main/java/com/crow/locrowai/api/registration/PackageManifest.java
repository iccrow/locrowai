package com.crow.locrowai.api.registration;

import java.util.*;

public class PackageManifest {
    public String id = "example";
    public String version = "0.0.0";
    public String description = "What a nice description you got there!";
    public String author = "The man himself";
    public List<String> tags = List.of("Very Cool!");

    public List<String> requirements = new ArrayList<>();
    public List<ModelCard> models = new ArrayList<>();
    public Map<String, String> hashes = new HashMap<>();

    public static class ModelCard {
        public String repo;
        public String revision = "main";
        public String filename;
        public String model_folder;
    }
}
