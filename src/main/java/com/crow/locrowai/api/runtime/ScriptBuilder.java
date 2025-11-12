package com.crow.locrowai.api.runtime;

import com.crow.locrowai.internal.LocrowAI;
import com.google.gson.Gson;
import com.google.gson.JsonElement;

import java.util.*;

public class ScriptBuilder {

    private final transient Gson gson = new Gson();
    private final transient Set<String> callIDs = new HashSet<>();

    private final String api_version = "0.4.0";

    private final Map<String, JsonElement> vars = new HashMap<>();

    private List<Call> script = new ArrayList<>();

    private Map<String, JsonElement> returns = new HashMap<>();

    public <T> ScriptBuilder var(String key, T value) {
        this.vars.put(key, this.gson.toJsonTree(value));
        return this;
    }

    public ScriptBuilder then(Call call) {
        callIDs.addAll(call.iterCallIDs());
        script.add(call);
        return this;
    }

    public <T> ScriptBuilder returns(String key, T value) {
        returns.put(key, gson.toJsonTree(value));
        return this;
    }

    public Script build() {
        return new Script(callIDs, gson.toJson(this));
    }
}