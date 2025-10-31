package com.crow.locrowai.api.runtime.exceptions;

public class UnauthorizedAICallException extends AIRuntimeException {
    public UnauthorizedAICallException(String callId) {
        super("Unauthorized AI call: '" + callId + "'. ");
    }
}
