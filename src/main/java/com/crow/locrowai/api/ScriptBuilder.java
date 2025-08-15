package com.crow.locrowai.api;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

public class ScriptBuilder {

    private final transient Gson gson = new Gson();

    private List<FunctionBuilder> functions = new ArrayList<>();

    private List<String> returns = new ArrayList<>();

    public ScriptBuilder then(FunctionBuilder functionBuilder) {
        functions.add(functionBuilder);
        return this;
    }

    public ScriptBuilder returns(String key) {
        returns.add(key);
        return this;
    }

    public ScriptBuilder returns(List<String> returns) {
        this.returns = returns;
        return this;
    }

    public Script build() {
        return new Script(gson.toJson(this));
    }
}