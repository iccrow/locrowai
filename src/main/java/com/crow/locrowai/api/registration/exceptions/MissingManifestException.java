package com.crow.locrowai.api.registration.exceptions;

public class MissingManifestException extends AIRegistrationException {
    public MissingManifestException(String s) {
        super("Cannot register extension '" + s + "'. " +
                "An extension manifest is expected at '" + s + "/manifest.json'.");
    }

    public MissingManifestException(String s, String none) {
        super("Cannot install core backend." +
                "A manifest is expected at '" + s + "'." +
                "Your jar may be corrupt or have been tampered!");
    }
}
