package com.crow.locrowai.api.registration.exceptions;

public class UnsupportedRequirementException extends AIRegistrationException {
    public UnsupportedRequirementException(String s) {
        super("Failed to install Python library '" + s + "'. " +
                "Library is not whitelisted!");
    }
}
