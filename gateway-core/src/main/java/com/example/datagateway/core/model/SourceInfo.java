package com.example.datagateway.core.model;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.Value;


/**
 * Contains information about the source of an incoming request.
 * This is typically part of {@link UnifiedInternalRequest}.
 */
@Value
@Builder
public class SourceInfo {
    /**
     * The communication protocol used by the source.
     */
    Protocol protocol;

    /**
     * Identifier for the source system or client.
     * Examples: Client IP address for HTTP, Kafka consumer group ID, MQTT client ID.
     */
    String sourceAddress;

    /**
     * Specific target within the protocol.
     * Examples: HTTP URI path, gRPC service/method name, Kafka topic, MQTT topic.
     */
    String requestTarget;

    // Consider adding other source-specific details if they become common,
    // or use a Map<String, String> for generic extensions.
}
