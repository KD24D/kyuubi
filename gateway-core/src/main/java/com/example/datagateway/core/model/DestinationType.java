package com.example.datagateway.core.model;

/**
 * Represents the type of destination for a routed event.
 * Typically part of {@link RouteDestination}.
 */
public enum DestinationType {
    /**
     * Target is an Apache Kafka topic.
     */
    KAFKA_TOPIC,
    /**
     * Target is an HTTP endpoint.
     */
    HTTP_ENDPOINT,
    /**
     * Target is a gRPC service.
     */
    GRPC_SERVICE,
    /**
     * Target is an internal queue within the Data Gateway for further asynchronous processing.
     */
    INTERNAL_QUEUE,
    /**
     * The event should be discarded and not routed further.
     */
    DISCARD
}
