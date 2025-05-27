package com.example.gateway.routinglimiting.ratelimiting;

public class RateLimitExceededException extends RuntimeException {
    private final String bucketId;

    public RateLimitExceededException(String bucketId, String message) {
        super(message);
        this.bucketId = bucketId;
    }

    public String getBucketId() {
        return bucketId;
    }
}
