package com.crow.locrowai.api.registration.exceptions;

public class MissingSecurityKeyException extends AIRegistrationException {
    public MissingSecurityKeyException(String s) {
        super("Cannot register extension '" + s + "'. " +
                "The security key is missing or was not provided.");
    }
}
