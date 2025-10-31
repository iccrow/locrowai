package com.crow.locrowai.api.runtime;

import java.util.Set;

public class Script {

    private final Set<String> callIDs;
    private final String blueprint;

    public Script(Set<String> callIDs, String blueprint) {
        if (callIDs == null) callIDs = Set.of();
        this.callIDs = Set.copyOf(callIDs);
        this.blueprint = blueprint;
    }

    public Set<String> getCallIDs() {
        return this.callIDs;
    }
    public String getJsonBlueprint() {
        return blueprint;
    }
}
