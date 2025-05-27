package com.example.datagateway.protocol.kafka;

import com.example.datagateway.core.model.PayloadType;
import com.example.datagateway.core.model.Protocol;
import com.example.datagateway.core.model.SourceInfo;
import com.example.datagateway.core.model.UnifiedInternalRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service responsible for consuming messages from Kafka topics,
 * converting them into {@link UnifiedInternalRequest}s, and forwarding them
 * to the core processing pipeline (placeholder logging for now).
 */
@Service
@Slf4j
@EnableConfigurationProperties(KafkaConsumerProperties.class)
public class KafkaIngestionService {

    private final KafkaConsumerProperties kafkaConsumerProperties;

    /**
     * Constructs the service with Kafka consumer properties.
     * In a real application, this would also inject a GatewayPipelineOrchestrator.
     * @param kafkaConsumerProperties Properties for configuring Kafka consumers.
     */
    @Autowired
    public KafkaIngestionService(KafkaConsumerProperties kafkaConsumerProperties) {
        this.kafkaConsumerProperties = kafkaConsumerProperties;
    }

    // Example Listener - more listeners can be configured through KafkaConsumerProperties
    // The topics and groupId are placeholders here and would typically be driven by properties
    // like "${gateway.protocol-adapters.kafka.consumers[0].topics}"
    // For dynamic listener registration based on properties, a more complex setup using
    // KafkaListenerEndpointRegistrar would be needed. For this example, we assume one
    // primary listener configuration is sufficient for demonstration or it's configured directly.
    // We'll use a fixed topic for the example, but application.yaml will define it.
    @KafkaListener(
            topics = "#{__listener.kafkaConsumerProperties.getPrimaryConsumerTopics()}",
            groupId = "#{__listener.kafkaConsumerProperties.getPrimaryConsumerGroupId()}",
            containerFactory = "byteArrayKafkaListenerContainerFactory" // Assuming a factory for byte[] is configured
    )
    public void receiveMessage(
            ConsumerRecord<String, byte[]> record,
            Acknowledgment acknowledgment) {

        log.info("Received Kafka message: Topic={}, Partition={}, Offset={}, Key={}",
                record.topic(), record.partition(), record.offset(), record.key());

        try {
            // 1. Generate or extract requestId
            String requestId = record.key(); // Use Kafka message key as requestId if present
            if (requestId == null || requestId.isEmpty()) {
                requestId = UUID.randomUUID().toString();
            }

            // 2. receivedTimestamp from Kafka record
            long receivedTimestamp = record.timestamp();

            // 3. Payload is directly available
            byte[] payloadBytes = record.value();

            // 4. Determine PayloadType
            PayloadType payloadType = determinePayloadType(record);

            // 5. Extract Kafka message headers
            Map<String, String> metadata = new HashMap<>();
            for (Header header : record.headers()) {
                metadata.put(header.key(), new String(header.value(), StandardCharsets.UTF_8));
            }
            // Add Kafka specific info to metadata as well for routing or transformation if needed
            metadata.put("kafka_topic", record.topic());
            metadata.put("kafka_partition", String.valueOf(record.partition()));
            metadata.put("kafka_offset", String.valueOf(record.offset()));
            if (record.key() != null) {
                metadata.put("kafka_key", record.key());
            }


            // 6. Populate SourceInfo
            // GroupId is not directly available here without more complex setup or passing from listener config.
            // For now, using the configured primary group ID.
            SourceInfo sourceInfo = SourceInfo.builder()
                    .protocol(Protocol.KAFKA)
                    .sourceAddress("kafka-consumer:" + kafkaConsumerProperties.getPrimaryConsumerGroupId()) // Consumer group as source address
                    .requestTarget(record.topic()) // Topic as the request target
                    .build();

            // 7. Create UnifiedInternalRequest
            UnifiedInternalRequest unifiedRequest = UnifiedInternalRequest.builder()
                    .requestId(requestId)
                    .payload(payloadBytes)
                    .payloadType(payloadType)
                    .sourceInfo(sourceInfo)
                    .metadata(metadata)
                    .receivedTimestamp(receivedTimestamp)
                    .originalEncoding(payloadType == PayloadType.JSON || payloadType == PayloadType.XML || payloadType == PayloadType.TEXT || payloadType == PayloadType.CSV ? StandardCharsets.UTF_8.name() : null)
                    .build();

            // 8. Placeholder for Orchestrator Integration
            log.info("Kafka Adapter: Created UnifiedInternalRequest: RequestId={}, Source={}, PayloadType={}, Size={}",
                    unifiedRequest.getRequestId(),
                    unifiedRequest.getSourceInfo().getRequestTarget(),
                    unifiedRequest.getPayloadType(),
                    (payloadBytes != null ? payloadBytes.length : 0));
            log.debug("Kafka Adapter: UnifiedInternalRequest details: {}", unifiedRequest);

            // pipelineOrchestrator.processRequest(Mono.just(unifiedRequest)).subscribe();

            // 9. Acknowledge the message
            acknowledgment.acknowledge();
            log.debug("Kafka message acknowledged: Topic={}, Partition={}, Offset={}",
                    record.topic(), record.partition(), record.offset());

        } catch (Exception e) {
            log.error("Error processing Kafka message: Topic={}, Partition={}, Offset={}, Error={}",
                    record.topic(), record.partition(), record.offset(), e.getMessage(), e);
            // For manual ack mode, nack-ing or not acknowledging will depend on retry strategy.
            // For now, we log and it won't be acknowledged, leading to redelivery based on Kafka config.
            // If a DLQ is configured at Kafka level (e.g. via ErrorHandlingDeserializer or an ErrorHandler),
            // that would take precedence for certain errors.
            // acknowledgment.nack(duration) could be an option for controlled redelivery.
        }
    }

    /**
     * Determines the {@link PayloadType} based on Kafka record information.
     * This might involve checking topic names against configured mappings or inspecting headers.
     *
     * @param record The Kafka {@link ConsumerRecord}.
     * @return The determined {@link PayloadType}.
     */
    private PayloadType determinePayloadType(ConsumerRecord<String, byte[]> record) {
        // Strategy 1: Check for a specific header
        Header payloadTypeHeader = record.headers().lastHeader("X-Payload-Type");
        if (payloadTypeHeader != null) {
            String typeString = new String(payloadTypeHeader.value(), StandardCharsets.UTF_8).toUpperCase();
            try {
                return PayloadType.valueOf(typeString);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid X-Payload-Type header value: {}. Falling back.", typeString);
            }
        }

        // Strategy 2: Check topic name against configured mappings
        if (kafkaConsumerProperties != null && kafkaConsumerProperties.getConsumers() != null) {
            for (KafkaConsumerProperties.ConsumerConfig consumerConfig : kafkaConsumerProperties.getConsumers()) {
                if (consumerConfig.getTopics() != null && consumerConfig.getTopics().contains(record.topic())) {
                    if (consumerConfig.getPayloadTypeMappings() != null) {
                        for (KafkaConsumerProperties.PayloadTypeMapping mapping : consumerConfig.getPayloadTypeMappings()) {
                            if (mapping.getTopic() != null && mapping.getTopic().equals(record.topic()) && mapping.getPayloadType() != null) {
                                return mapping.getPayloadTypeEnum();
                            }
                            if (mapping.getHeader() != null) { // Check header again if specified in this specific config
                                Header specificHeader = record.headers().lastHeader(mapping.getHeader());
                                if (specificHeader != null) {
                                     String typeString = new String(specificHeader.value(), StandardCharsets.UTF_8).toUpperCase();
                                     try {
                                        return PayloadType.valueOf(typeString);
                                     } catch (IllegalArgumentException e) {
                                        log.warn("Invalid {} header value: {}. Falling back.", mapping.getHeader(), typeString);
                                     }
                                }
                            }
                        }
                    }
                    // If consumer config matches topic but no specific mapping, use its default if any
                    if(consumerConfig.getDefaultPayloadType() != null) {
                        return consumerConfig.getDefaultPayloadTypeEnum();
                    }
                }
            }
        }
        // Default if no other rule applies
        log.debug("PayloadType for topic {} not determined by header or specific mapping, defaulting to UNKNOWN.", record.topic());
        return PayloadType.UNKNOWN;
    }
}
