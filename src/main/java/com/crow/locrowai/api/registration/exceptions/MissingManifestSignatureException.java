package com.crow.locrowai.api.registration.exceptions;

public class MissingManifestSignatureException extends AIRegistrationException {
    public MissingManifestSignatureException(String s) {
        super("Cannot register extension '" + s + "'. " +
                "An extension manifest signature is expected at '" + s + "/manifest.json.sig.b64'.");
    }
}
