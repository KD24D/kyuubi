package com.example.gateway.core.model;

import java.util.Map;
import java.util.Objects;

public class RoutedEvent {
    private String requestId;
    private SourceInfo sourceInfo; // Propagated for context
    private Object payloadToRoute; // Could be TransformedDataEvent.transformedPayload or the whole event
    private PayloadType payloadType; // Type of the payloadToRoute
    private RouteDestination destination;
    private Map<String, String> routingMetadata; // e.g., specific headers for HTTP egress

    public RoutedEvent() {
    }

    // Constructor, Getters, Setters, equals, hashCode, toString
    public RoutedEvent(String requestId, SourceInfo sourceInfo, Object payloadToRoute, PayloadType payloadType, RouteDestination destination, Map<String, String> routingMetadata) {
        this.requestId = requestId;
        this.sourceInfo = sourceInfo;
        this.payloadToRoute = payloadToRoute;
        this.payloadType = payloadType;
        this.destination = destination;
        this.routingMetadata = routingMetadata;
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

    public Object getPayloadToRoute() {
        return payloadToRoute;
    }

    public void setPayloadToRoute(Object payloadToRoute) {
        this.payloadToRoute = payloadToRoute;
    }

    public PayloadType getPayloadType() {
        return payloadType;
    }

    public void setPayloadType(PayloadType payloadType) {
        this.payloadType = payloadType;
    }

    public RouteDestination getDestination() {
        return destination;
    }

    public void setDestination(RouteDestination destination) {
        this.destination = destination;
    }

    public Map<String, String> getRoutingMetadata() {
        return routingMetadata;
    }

    public void setRoutingMetadata(Map<String, String> routingMetadata) {
        this.routingMetadata = routingMetadata;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RoutedEvent that = (RoutedEvent) o;
        return Objects.equals(requestId, that.requestId) &&
               Objects.equals(sourceInfo, that.sourceInfo) &&
               Objects.equals(payloadToRoute, that.payloadToRoute) &&
               payloadType == that.payloadType &&
               Objects.equals(destination, that.destination) &&
               Objects.equals(routingMetadata, that.routingMetadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestId, sourceInfo, payloadToRoute, payloadType, destination, routingMetadata);
    }

    @Override
    public String toString() {
        return "RoutedEvent{" +
               "requestId='" + requestId + '\'' +
               ", sourceInfo=" + sourceInfo +
               ", payloadType=" + payloadType +
               ", destination=" + destination +
               ", routingMetadata=" + routingMetadata +
               // payloadToRoute can be large
               '}';
    }
}
