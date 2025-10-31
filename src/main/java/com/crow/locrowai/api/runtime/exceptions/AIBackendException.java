package com.crow.locrowai.api.runtime.exceptions;

public class AIBackendException extends AIRuntimeException {
    public AIBackendException(String s) {
        super("The AI backend encountered an error when executing a script. " + s);
    }
}
