package com.example.gateway.core.model;

import java.util.Map;
import java.util.Objects;

public class NormalizedDataEvent {
    private String requestId;
    private SourceInfo sourceInfo;
    private Object parsedPayload; // e.g., Map<String, Object>, Protobuf Message, List<List<String>> for CSV
    private PayloadType originalPayloadType;
    private PayloadType processedPayloadType; // Could be same as original or new (e.g., PARQUET)
    private byte[] convertedPayload; // Optional: if a format conversion like CSV to Parquet happened
    private ValidationResult validationResult;
    private Map<String, String> processingMetadata;

    public NormalizedDataEvent() {
    }

    // Constructor, Getters, Setters, equals, hashCode, toString
    public NormalizedDataEvent(String requestId, SourceInfo sourceInfo, Object parsedPayload, PayloadType originalPayloadType, PayloadType processedPayloadType, byte[] convertedPayload, ValidationResult validationResult, Map<String, String> processingMetadata) {
        this.requestId = requestId;
        this.sourceInfo = sourceInfo;
        this.parsedPayload = parsedPayload;
        this.originalPayloadType = originalPayloadType;
        this.processedPayloadType = processedPayloadType;
        this.convertedPayload = convertedPayload;
        this.validationResult = validationResult;
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

    public Object getParsedPayload() {
        return parsedPayload;
    }

    public void setParsedPayload(Object parsedPayload) {
        this.parsedPayload = parsedPayload;
    }

    public PayloadType getOriginalPayloadType() {
        return originalPayloadType;
    }

    public void setOriginalPayloadType(PayloadType originalPayloadType) {
        this.originalPayloadType = originalPayloadType;
    }

    public PayloadType getProcessedPayloadType() {
        return processedPayloadType;
    }

    public void setProcessedPayloadType(PayloadType processedPayloadType) {
        this.processedPayloadType = processedPayloadType;
    }

    public byte[] getConvertedPayload() {
        return convertedPayload;
    }

    public void setConvertedPayload(byte[] convertedPayload) {
        this.convertedPayload = convertedPayload;
    }

    public ValidationResult getValidationResult() {
        return validationResult;
    }

    public void setValidationResult(ValidationResult validationResult) {
        this.validationResult = validationResult;
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
        NormalizedDataEvent that = (NormalizedDataEvent) o;
        return Objects.equals(requestId, that.requestId) &&
               Objects.equals(sourceInfo, that.sourceInfo) &&
               Objects.equals(parsedPayload, that.parsedPayload) &&
               originalPayloadType == that.originalPayloadType &&
               processedPayloadType == that.processedPayloadType &&
               java.util.Arrays.equals(convertedPayload, that.convertedPayload) &&
               Objects.equals(validationResult, that.validationResult) &&
               Objects.equals(processingMetadata, that.processingMetadata);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(requestId, sourceInfo, parsedPayload, originalPayloadType, processedPayloadType, validationResult, processingMetadata);
        result = 31 * result + java.util.Arrays.hashCode(convertedPayload);
        return result;
    }

    @Override
    public String toString() {
        return "NormalizedDataEvent{" +
               "requestId='" + requestId + '\'' +
               ", sourceInfo=" + sourceInfo +
               ", originalPayloadType=" + originalPayloadType +
               ", processedPayloadType=" + processedPayloadType +
               ", validationResult=" + validationResult +
               ", processingMetadata=" + processingMetadata +
               // parsedPayload and convertedPayload can be large
               '}';
    }
}
