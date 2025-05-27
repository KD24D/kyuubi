package com.example.gateway.core.model;

import java.util.Map;
import java.util.Objects;

public class UnifiedInternalRequest {
    private String requestId;
    private Object payload; // byte[] or String typically at this stage
    private PayloadType payloadType;
    private SourceInfo sourceInfo;
    private Map<String, String> metadata;
    private long receivedTimestamp;
    private String originalEncoding; // e.g., UTF-8

    public UnifiedInternalRequest() {
    }

    // Constructor, Getters, Setters, equals, hashCode, toString
    public UnifiedInternalRequest(String requestId, Object payload, PayloadType payloadType, SourceInfo sourceInfo, Map<String, String> metadata, long receivedTimestamp, String originalEncoding) {
        this.requestId = requestId;
        this.payload = payload;
        this.payloadType = payloadType;
        this.sourceInfo = sourceInfo;
        this.metadata = metadata;
        this.receivedTimestamp = receivedTimestamp;
        this.originalEncoding = originalEncoding;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }

    public PayloadType getPayloadType() {
        return payloadType;
    }

    public void setPayloadType(PayloadType payloadType) {
        this.payloadType = payloadType;
    }

    public SourceInfo getSourceInfo() {
        return sourceInfo;
    }

    public void setSourceInfo(SourceInfo sourceInfo) {
        this.sourceInfo = sourceInfo;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public long getReceivedTimestamp() {
        return receivedTimestamp;
    }

    public void setReceivedTimestamp(long receivedTimestamp) {
        this.receivedTimestamp = receivedTimestamp;
    }

    public String getOriginalEncoding() {
        return originalEncoding;
    }

    public void setOriginalEncoding(String originalEncoding) {
        this.originalEncoding = originalEncoding;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UnifiedInternalRequest that = (UnifiedInternalRequest) o;
        return receivedTimestamp == that.receivedTimestamp &&
               Objects.equals(requestId, that.requestId) &&
               Objects.equals(payload, that.payload) &&
               payloadType == that.payloadType &&
               Objects.equals(sourceInfo, that.sourceInfo) &&
               Objects.equals(metadata, that.metadata) &&
               Objects.equals(originalEncoding, that.originalEncoding);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestId, payload, payloadType, sourceInfo, metadata, receivedTimestamp, originalEncoding);
    }

    @Override
    public String toString() {
        return "UnifiedInternalRequest{" +
               "requestId='" + requestId + '\'' +
               ", payloadType=" + payloadType +
               ", sourceInfo=" + sourceInfo +
               ", metadata=" + metadata +
               ", receivedTimestamp=" + receivedTimestamp +
               ", originalEncoding='" + originalEncoding + '\'' +
               // Payload toString can be large, so handle carefully or omit
               '}';
    }
}
