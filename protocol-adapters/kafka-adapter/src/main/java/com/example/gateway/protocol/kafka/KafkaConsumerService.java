package com.example.gateway.protocol.kafka;

import com.example.gateway.core.model.PayloadType;
import com.example.gateway.core.model.Protocol;
import com.example.gateway.core.model.SourceInfo;
import com.example.gateway.core.model.UnifiedInternalRequest;
// Import the orchestrator service - placeholder for now.
// import com.example.gateway.core.orchestration.GatewayPipelineOrchestrator;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.HeaderAsMap; // For all headers
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono; // If orchestrator uses reactive flow

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class KafkaConsumerService {

    private static final Logger logger = LoggerFactory.getLogger(KafkaConsumerService.class);

    // Placeholder for the pipeline orchestrator.
    /*
    private final GatewayPipelineOrchestrator pipelineOrchestrator;

    @Autowired
    public KafkaConsumerService(GatewayPipelineOrchestrator pipelineOrchestrator) {
        this.pipelineOrchestrator = pipelineOrchestrator;
    }
    */

    // Example listener - topics and group ID would be configured in application.yml/properties
    // For multiple listeners or more complex configuration, use a KafkaListenerContainerFactory
    @KafkaListener(topics = "${gateway.protocol-adapters.kafka.consumer.topics:default-topic}",
                   groupId = "${gateway.protocol-adapters.kafka.consumer.groupId:default-group}",
                   clientIdPrefix = "kafka-consumer-service")
    public void listenToKafkaTopic(
            @Payload byte[] messagePayload, // Consume as byte array
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long kafkaTimestamp,
            @Header(KafkaHeaders.MESSAGE_KEY) String key, // Optional: message key as String
            @HeaderAsMap Map<String, Object> allHeaders // All headers as a map
            /* ConsumerRecord<String, byte[]> record - alternative to get everything */
            ) {

        String requestId = getHeaderAsString(allHeaders, "X-Request-ID");
        if (requestId == null || requestId.isEmpty()) {
            if (key != null && !key.isEmpty()) {
                requestId = key + "_" + UUID.randomUUID().toString().substring(0,8) ; // Use key if available for better tracing
            } else {
                requestId = UUID.randomUUID().toString();
            }
        }

        SourceInfo sourceInfo = new SourceInfo(
                Protocol.KAFKA,
                "kafka:" + topic + ":" + partition + ":" + offset, // Source Address
                topic // Request Target
        );

        Map<String, String> metadata = new HashMap<>();
        for (Map.Entry<String, Object> entry : allHeaders.entrySet()) {
            // Convert header values to String. Be mindful of potential non-String header values.
             if (entry.getValue() instanceof byte[]) {
                metadata.put(entry.getKey(), new String((byte[]) entry.getValue(), StandardCharsets.UTF_8));
            } else if (entry.getValue() != null) {
                metadata.put(entry.getKey(), entry.getValue().toString());
            }
        }
        // Add key standard metadata if not already present from headers
        metadata.putIfAbsent("kafka_topic", topic);
        metadata.putIfAbsent("kafka_partition", String.valueOf(partition));
        metadata.putIfAbsent("kafka_offset", String.valueOf(offset));
        metadata.putIfAbsent("kafka_timestamp", String.valueOf(kafkaTimestamp));
        if (key != null) {
             metadata.putIfAbsent("kafka_messageKey", key);
        }


        // Determine PayloadType from headers or configuration (e.g., topic naming convention)
        // This is a simplified example; more robust determination might be needed.
        PayloadType payloadType = determinePayloadTypeFromHeaders(allHeaders);
        String originalEncoding = getHeaderAsString(allHeaders, "X-Original-Encoding"); // Custom header example

        UnifiedInternalRequest uir = new UnifiedInternalRequest(
                requestId,
                messagePayload, // byte[]
                payloadType,
                sourceInfo,
                metadata,
                kafkaTimestamp, // Using Kafka message timestamp as receivedTimestamp
                originalEncoding
        );

        logger.info("Received Kafka message from topic [{}], offset [{}], created UnifiedInternalRequest: {}", topic, offset, uir.getRequestId());
        logger.debug("UIR Details: {}", uir);

        // TODO: Later, pass the UIR to the GatewayPipelineOrchestrator
        // pipelineOrchestrator.processRequest(Mono.just(uir)).subscribe(); // Example for reactive orchestrator

        // For now, processing ends here for this subtask.
        // Acknowledgment is handled automatically by Spring Kafka unless manual ack is configured.
    }

    private String getHeaderAsString(Map<String, Object> headers, String headerName) {
        Object value = headers.get(headerName);
        if (value instanceof byte[]) {
            return new String((byte[]) value, StandardCharsets.UTF_8);
        } else if (value != null) {
            return value.toString();
        }
        return null;
    }

    private PayloadType determinePayloadTypeFromHeaders(Map<String, Object> headers) {
        String contentType = getHeaderAsString(headers, "content-type"); // Common header for type
        if (contentType != null) {
            if (contentType.toLowerCase().contains("json")) {
                return PayloadType.JSON;
            } else if (contentType.toLowerCase().contains("protobuf")) {
                return PayloadType.PROTOBUF;
            } else if (contentType.toLowerCase().contains("csv")) {
                return PayloadType.CSV;
            } else if (contentType.toLowerCase().contains("xml")) {
                return PayloadType.XML;
            } else if (contentType.toLowerCase().startsWith("text")) {
                return PayloadType.TEXT;
            } else {
                 return PayloadType.BINARY; // Default for other specific content types
            }
        }
        // Fallback if no content-type header. Could also be based on topic configuration.
        logger.warn("No 'content-type' header found in Kafka message, defaulting PayloadType to BINARY for request ID if applicable.");
        return PayloadType.BINARY;
    }
}
