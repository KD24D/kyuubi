package com.example.datagateway.core.model;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

/**
 * Represents an event that has been processed by the routing layer and is ready
 * for dispatch to its determined destination.
 * This is the output of the Intelligent Rate Limiting and Routing Layer.
 */
@Value
@Builder
public class RoutedEvent {
    /**
     * The unique request ID, propagated from the {@link TransformedDataEvent}.
     */
    String requestId;

    /**
     * Information about the original source of the data, propagated from the {@link TransformedDataEvent}.
     */
    SourceInfo sourceInfo;

    /**
     * The payload to be routed. This is typically the {@code transformedPayload}
     * from the {@link TransformedDataEvent}.
     */
    Object payloadToRoute;

    /**
     * The {@link PayloadType} of the {@code payloadToRoute}.
     * Propagated from {@link TransformedDataEvent#getPayloadType()}.
     */
    PayloadType payloadType;

    /**
     * The destination where this event should be sent.
     */
    RouteDestination destination;

    /**
     * Additional metadata to be used during the egress process.
     * This could include specific headers for an HTTP request, Kafka message keys/headers, etc.
     * that are determined by the routing logic or need to be passed to the egress adapter.
     */
    Map<String, String> routingMetadata;
}
