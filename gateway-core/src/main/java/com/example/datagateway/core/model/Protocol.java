package com.example.datagateway.core.model;

/**
 * Represents the communication protocol used for an incoming request or outgoing message.
 */
public enum Protocol {
    /**
     * Hypertext Transfer Protocol.
     */
    HTTP,
    /**
     * gRPC Remote Procedure Call.
     */
    GRPC,
    /**
     * Apache Kafka messaging protocol.
     */
    KAFKA,
    /**
     * MQ Telemetry Transport protocol.
     */
    MQTT
}
