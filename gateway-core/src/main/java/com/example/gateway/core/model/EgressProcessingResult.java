package com.example.gateway.core.model;

import java.util.Map;
import java.util.Objects;

public class EgressProcessingResult {
    private String requestId;
    private RouteDestination destination; // The intended destination
    private EgressStatus status;
    private String failureReason; // If status is FAILURE or RETRYABLE_FAILURE
    private int attemptCount;
    private Map<String, String> responseMetadata; // e.g., HTTP response headers from downstream

    public EgressProcessingResult() {
    }

    // Constructor, Getters, Setters, equals, hashCode, toString
    public EgressProcessingResult(String requestId, RouteDestination destination, EgressStatus status, String failureReason, int attemptCount, Map<String, String> responseMetadata) {
        this.requestId = requestId;
        this.destination = destination;
        this.status = status;
        this.failureReason = failureReason;
        this.attemptCount = attemptCount;
        this.responseMetadata = responseMetadata;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public RouteDestination getDestination() {
        return destination;
    }

    public void setDestination(RouteDestination destination) {
        this.destination = destination;
    }

    public EgressStatus getStatus() {
        return status;
    }

    public void setStatus(EgressStatus status) {
        this.status = status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public Map<String, String> getResponseMetadata() {
        return responseMetadata;
    }

    public void setResponseMetadata(Map<String, String> responseMetadata) {
        this.responseMetadata = responseMetadata;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EgressProcessingResult that = (EgressProcessingResult) o;
        return attemptCount == that.attemptCount &&
               Objects.equals(requestId, that.requestId) &&
               Objects.equals(destination, that.destination) &&
               status == that.status &&
               Objects.equals(failureReason, that.failureReason) &&
               Objects.equals(responseMetadata, that.responseMetadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestId, destination, status, failureReason, attemptCount, responseMetadata);
    }

    @Override
    public String toString() {
        return "EgressProcessingResult{" +
               "requestId='" + requestId + '\'' +
               ", destination=" + destination +
               ", status=" + status +
               ", failureReason='" + failureReason + '\'' +
               ", attemptCount=" + attemptCount +
               ", responseMetadata=" + responseMetadata +
               '}';
    }
}
