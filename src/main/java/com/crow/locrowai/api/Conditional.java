package com.crow.locrowai.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.List;

public class Conditional  {
    public enum Operation {
        EQUALS("=="),
        GREATER_THAN(">"),
        LESS_THAN("<"),
        GREATER_THAN_EQUAL_TO(">="),
        LESS_THAN_EQUAL_TO("<="),
        IN("in");

        private final String operation;

        Operation(String operation) {
            this.operation = operation;
        }

        public String getOperation() {
            return operation;
        }
    }
    private List<Conditional> AND;
    private List<Conditional> OR;
    private Conditional NOT;
    private JsonElement left;
    private JsonElement right;
    private String operation;
    private transient Gson gson;

    public <L, R> Conditional(L left, Operation operation, R right) {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.left = this.gson.toJsonTree(left);
        this.right = this.gson.toJsonTree(right);
        if (operation != null)
            this.operation = operation.getOperation();
    }

    public <L> Conditional(L left) {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.left = this.gson.toJsonTree(left);
    }

    public Conditional() {}

    public static Conditional and(List<Conditional> AND) {
        Conditional wrapper = new Conditional();

        wrapper.AND = AND;
        return wrapper;
    }

    public static Conditional or(List<Conditional> OR) {
        Conditional wrapper = new Conditional();

        wrapper.OR = OR;
        return wrapper;
    }

    public static Conditional not(Conditional NOT) {
        Conditional wrapper = new Conditional();

        wrapper.NOT = NOT;
        return wrapper;
    }

    public Conditional and(Conditional AND) {
        if (this.AND == null) this.AND = new ArrayList<>();
        this.AND.add(AND);
        return this;
    }

    public Conditional or(Conditional OR) {
        if (this.OR == null) this.OR = new ArrayList<>();
        this.OR.add(OR);
        return this;
    }
}
