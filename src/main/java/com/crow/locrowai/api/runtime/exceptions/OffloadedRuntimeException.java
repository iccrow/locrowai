package com.crow.locrowai.api.runtime.exceptions;

public class OffloadedRuntimeException extends AIRuntimeException {
    private final String errorType;
    public OffloadedRuntimeException(String errorType, String s) {
        super(s);
        this.errorType = errorType;
    }

    public String getErrorType() {
        return errorType;
    }
}
