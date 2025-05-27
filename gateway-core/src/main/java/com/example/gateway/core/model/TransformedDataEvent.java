package com.example.gateway.core.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class TransformedDataEvent {
    private String requestId;
    private SourceInfo sourceInfo;
    private Object transformedPayload; // e.g., Map<String, Object>, new Protobuf Message, byte[]
    private PayloadType payloadType; // Type of the transformedPayload
    private TransformationStatus status;
    private List<String> transformationErrors;
    private Map<String, String> processingMetadata;

    public TransformedDataEvent() {
    }

    // Constructor, Getters, Setters, equals, hashCode, toString
    public TransformedDataEvent(String requestId, SourceInfo sourceInfo, Object transformedPayload, PayloadType payloadType, TransformationStatus status, List<String> transformationErrors, Map<String, String> processingMetadata) {
        this.requestId = requestId;
        this.sourceInfo = sourceInfo;
        this.transformedPayload = transformedPayload;
        this.payloadType = payloadType;
        this.status = status;
        this.transformationErrors = transformationErrors;
        this.processingMetadata = processingMetadata;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public SourceInfo getSourceInfo() {
        return sourceInfo;
    }

    public void setSourceInfo(SourceInfo sourceInfo) {
        this.sourceInfo = sourceInfo;
    }

    public Object getTransformedPayload() {
        return transformedPayload;
    }

    public void setTransformedPayload(Object transformedPayload) {
        this.transformedPayload = transformedPayload;
    }

    public PayloadType getPayloadType() {
        return payloadType;
    }

    public void setPayloadType(PayloadType payloadType) {
        this.payloadType = payloadType;
    }

    public TransformationStatus getStatus() {
        return status;
    }

    public void setStatus(TransformationStatus status) {
        this.status = status;
    }

    public List<String> getTransformationErrors() {
        return transformationErrors;
    }

    public void setTransformationErrors(List<String> transformationErrors) {
        this.transformationErrors = transformationErrors;
    }

    public Map<String, String> getProcessingMetadata() {
        return processingMetadata;
    }

    public void setProcessingMetadata(Map<String, String> processingMetadata) {
        this.processingMetadata = processingMetadata;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TransformedDataEvent that = (TransformedDataEvent) o;
        return Objects.equals(requestId, that.requestId) &&
               Objects.equals(sourceInfo, that.sourceInfo) &&
               Objects.equals(transformedPayload, that.transformedPayload) &&
               payloadType == that.payloadType &&
               status == that.status &&
               Objects.equals(transformationErrors, that.transformationErrors) &&
               Objects.equals(processingMetadata, that.processingMetadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestId, sourceInfo, transformedPayload, payloadType, status, transformationErrors, processingMetadata);
    }

    @Override
    public String toString() {
        return "TransformedDataEvent{" +
               "requestId='" + requestId + '\'' +
               ", sourceInfo=" + sourceInfo +
               ", payloadType=" + payloadType +
               ", status=" + status +
               ", transformationErrors=" + transformationErrors +
               ", processingMetadata=" + processingMetadata +
               // transformedPayload can be large
               '}';
    }
}
