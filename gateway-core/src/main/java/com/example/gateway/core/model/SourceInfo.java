package com.example.gateway.core.model;

import java.util.Objects;

public class SourceInfo {
    private Protocol protocol;
    private String sourceAddress; // e.g., Client IP, Kafka consumer group, MQTT client ID
    private String requestTarget; // e.g., HTTP URI path, gRPC service/method, Kafka topic, MQTT topic

    public SourceInfo() {
    }

    public SourceInfo(Protocol protocol, String sourceAddress, String requestTarget) {
        this.protocol = protocol;
        this.sourceAddress = sourceAddress;
        this.requestTarget = requestTarget;
    }

    // Getters and Setters
    public Protocol getProtocol() {
        return protocol;
    }

    public void setProtocol(Protocol protocol) {
        this.protocol = protocol;
    }

    public String getSourceAddress() {
        return sourceAddress;
    }

    public void setSourceAddress(String sourceAddress) {
        this.sourceAddress = sourceAddress;
    }

    public String getRequestTarget() {
        return requestTarget;
    }

    public void setRequestTarget(String requestTarget) {
        this.requestTarget = requestTarget;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SourceInfo that = (SourceInfo) o;
        return protocol == that.protocol &&
               Objects.equals(sourceAddress, that.sourceAddress) &&
               Objects.equals(requestTarget, that.requestTarget);
    }

    @Override
    public int hashCode() {
        return Objects.hash(protocol, sourceAddress, requestTarget);
    }

    @Override
    public String toString() {
        return "SourceInfo{" +
               "protocol=" + protocol +
               ", sourceAddress='" + sourceAddress + '\'' +
               ", requestTarget='" + requestTarget + '\'' +
               '}';
    }
}
