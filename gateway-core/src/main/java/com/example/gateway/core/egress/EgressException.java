package com.example.gateway.core.egress; // Or a common exceptions package

public class EgressException extends RuntimeException {
    private String details;

    public EgressException(String message) {
        super(message);
    }

    public EgressException(String message, String details) {
        super(message);
        this.details = details;
    }

    public EgressException(String message, Throwable cause) {
        super(message, cause);
    }

    public String getDetails() {
        return details;
    }
}
