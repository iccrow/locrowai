package com.crow.locrowai.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

import java.util.HashMap;
import java.util.Map;

public class FunctionBuilder {
    private final String id;
    private final transient Gson gson;
    private final Map<String, JsonElement> feeds = new HashMap<>();
    private final Mapping mappings = new Mapping();

    public FunctionBuilder(String id) {
        this.id = id;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }
    public <T> FunctionBuilder feed(String key, T val) {
        feeds.put(key, gson.toJsonTree(val));
        return this;
    }

    public FunctionBuilder feedReturn(String source, String destination) {
        mappings.returns_to_params.put(source, destination);
        return this;
    }

    public FunctionBuilder feedPass(String source, String destination) {
        mappings.passes_to_params.put(source, destination);
        return this;
    }

    public FunctionBuilder passReturn(String source, String destination) {
        mappings.returns_to_passes.put(source, destination);
        return this;
    }

    public FunctionBuilder pass(String source, String destination) {
        mappings.passes.put(source, destination);
        return this;
    }

    public FunctionBuilder pass(String key) {
        mappings.passes.put(key, key);
        return this;
    }
}
