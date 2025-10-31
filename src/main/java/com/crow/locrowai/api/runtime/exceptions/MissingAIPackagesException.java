package com.crow.locrowai.api.runtime.exceptions;

public class MissingAIPackagesException extends AIRuntimeException {
    public MissingAIPackagesException() {
        super("AI packages are missing or still installing. " +
                "Ensure the required AI modules are installed and initialized before running scripts.");
    }
}
