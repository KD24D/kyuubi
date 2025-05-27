package com.example.datagateway.core.model;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

/**
 * Specifies the destination for a routed event.
 * This is typically part of {@link RoutedEvent}.
 */
@Value
@Builder
public class RouteDestination {
    /**
     * The type of the destination (e.g., Kafka topic, HTTP endpoint).
     */
    DestinationType type;

    /**
     * The specific target for the destination.
     * Examples:
     * - For KAFKA_TOPIC: "orders-processed-topic"
     * - For HTTP_ENDPOINT: "http://downstream-service/api/data"
     * - For GRPC_SERVICE: "OrderService/ProcessOrder"
     * - For INTERNAL_QUEUE: "high-priority-internal-queue"
     */
    String target;

    /**
     * Additional properties specific to the destination type.
     * Examples:
     * - For KAFKA_TOPIC: Kafka producer properties (acks, retries, serializer class names), partition key.
     * - For HTTP_ENDPOINT: HTTP method (POST, PUT), specific headers to add.
     * - For GRPC_SERVICE: RPC call timeout, specific gRPC metadata.
     * This map provides flexibility to configure destination-specific parameters.
     */
    Map<String, String> properties;
}
