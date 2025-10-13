package com.crow.locrowai.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Call {
    private String id;

    private String call;
    private ArrayList<Call> calls;
    private Conditional condition;
    private Loop loop;
    private final transient Gson gson;
    private final Map<String, JsonElement> feeds = new HashMap<>();
    private final Map<String, JsonElement> initialize = new HashMap<>();

    public Call(String id) {
        this.id = id;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public Call() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public Call call(String call) {
        this.call = call;
        return this;
    }

    public Call call(Call call) {
        if (this.calls == null) this.calls = new ArrayList<>();
        this.calls.add(call);
        return this;
    }

    public <T> Call feed(String key, T val) {
        this.feeds.put(key, this.gson.toJsonTree(val));
        return this;
    }

    public <T> Call initialize(String key, T val) {
        this.initialize.put(key, this.gson.toJsonTree(val));
        return this;
    }

    public Call loop(Loop loop) {
        this.loop = loop;
        return this;
    }

    public Call condition(Conditional condition) {
        this.condition = condition;
        return this;
    }
}
