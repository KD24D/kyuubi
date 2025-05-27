package com.example.gateway.core.model;

public enum DestinationType {
    KAFKA_TOPIC,
    HTTP_ENDPOINT,
    GRPC_SERVICE,
    INTERNAL_QUEUE, // For internal processing/batching
    DISCARD         // To explicitly drop messages
}
