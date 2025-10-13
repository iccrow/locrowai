package com.crow.locrowai.api;

public class Loop {
    private String index;
    private final Conditional condition;

    public Loop(String indexName, Conditional condition) {
        this.index = indexName;
        this.condition = condition;
    }

    public Loop(Conditional condition) {
        this.condition = condition;
    }
}
