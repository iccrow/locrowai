package com.crow.locrowai.api;

import com.crow.locrowai.LocrowAI;
import com.google.gson.Gson;
import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScriptBuilder {

    private final transient Gson gson = new Gson();

    private final String api_version = LocrowAI.PY_VERSION();

    private final Map<String, JsonElement> vars = new HashMap<>();

    private List<Call> script = new ArrayList<>();

    private Map<String, JsonElement> returns = new HashMap<>();

    public <T> ScriptBuilder var(String key, T value) {
        this.vars.put(key, this.gson.toJsonTree(value));
        return this;
    }

    public ScriptBuilder then(Call call) {
        script.add(call);
        return this;
    }

    public <T> ScriptBuilder returns(String key, T value) {
        returns.put(key, gson.toJsonTree(value));
        return this;
    }

    public Script build() {
        return new Script(gson.toJson(this));
    }
}