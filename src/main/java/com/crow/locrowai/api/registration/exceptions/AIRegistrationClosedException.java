package com.crow.locrowai.api.registration.exceptions;

public class AIRegistrationClosedException extends AIRegistrationException {
    public AIRegistrationClosedException(String s) {
        super("Cannot register or declare model or extension '" + s + "'. " +
                "The registration period is over.");
    }
}
