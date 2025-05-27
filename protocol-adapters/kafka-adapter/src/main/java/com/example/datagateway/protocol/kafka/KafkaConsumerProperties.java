package com.example.datagateway.protocol.kafka;

import com.example.datagateway.core.model.PayloadType;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties for Kafka consumers within the Data Gateway.
 * This allows defining multiple consumer configurations, each potentially listening
 * to different topics or with different settings.
 * <p>
 * Example YAML structure:
 * <pre>
 * gateway:
 *   protocol-adapters:
 *     kafka:
 *       consumers:
 *         - name: "orders-consumer"
 *           enabled: true
 *           topics: "orders-json-topic,orders-protobuf-topic"
 *           groupId: "gateway-orders-group"
 *           bootstrapServers: "kafka1:9092,kafka2:9092" # Optional override of global
 *           properties: # Optional Kafka consumer properties
 *             fetch.min.bytes: "1024"
 *           payloadTypeMappings:
 *             - topic: "orders-json-topic"
 *               payloadType: "JSON"
 *             - topic: "orders-protobuf-topic"
 *               payloadType: "PROTOBUF"
 *               # Or by header:
 *             # - header: "X-Schema-Type" # If this header is present, its value is used as PayloadType
 *             # defaultPayloadType: "BINARY" # If no mapping matches
 *         - name: "logs-consumer"
 *           topics: "app-logs-topic"
 *           groupId: "gateway-logs-group"
 *           defaultPayloadType: "TEXT"
 * </pre>
 */
@ConfigurationProperties(prefix = "gateway.protocol-adapters.kafka")
@Component // To make it injectable for KafkaListener topic resolution
@Getter
@Setter
public class KafkaConsumerProperties {

    private List<ConsumerConfig> consumers = new ArrayList<>();

    @Data
    public static class ConsumerConfig {
        /**
         * A unique name for this consumer configuration instance.
         */
        private String name;
        /**
         * Whether this consumer configuration is enabled.
         */
        private boolean enabled = true;
        /**
         * Comma-separated list of topics for this consumer to subscribe to.
         */
        private List<String> topics;
        /**
         * The Kafka consumer group ID for this consumer.
         */
        private String groupId;
        /**
         * Optional: Bootstrap servers for this specific consumer, overrides global Kafka properties if set.
         */
        private String bootstrapServers; // Optional: can override global spring.kafka.bootstrap-servers
        /**
         * Optional: Default payload type to assume for topics consumed by this listener if not
         * determined by specific mappings or headers.
         */
        private String defaultPayloadType; // e.g., "JSON", "BINARY"

        /**
         * Specific mappings to determine payload type for consumed messages.
         * Evaluated in order. First match wins.
         */
        private List<PayloadTypeMapping> payloadTypeMappings = new ArrayList<>();

        /**
         * Additional Kafka consumer properties for this specific consumer.
         * These are passed directly to the Kafka consumer.
         * Example: spring.kafka.consumer.properties.fetch.min.bytes=1024
         */
        private Map<String, String> properties; // e.g. fetch.min.bytes, security.protocol

        public PayloadType getDefaultPayloadTypeEnum() {
            if (defaultPayloadType == null || defaultPayloadType.isBlank()) {
                return PayloadType.UNKNOWN;
            }
            try {
                return PayloadType.valueOf(defaultPayloadType.toUpperCase());
            } catch (IllegalArgumentException e) {
                // Log warning: Invalid defaultPayloadType value
                return PayloadType.UNKNOWN;
            }
        }
    }

    @Data
    public static class PayloadTypeMapping {
        /**
         * Specific topic name for this mapping.
         */
        private String topic;
        /**
         * If this header key is present in the Kafka message, its value (converted to uppercase)
         * will be used to determine the {@link PayloadType}. E.g., header "X-Payload-Type" with value "JSON".
         */
        private String header;
        /**
         * The {@link PayloadType} to assign if this mapping matches.
         * Value should be one of the enum names from {@link com.example.datagateway.core.model.PayloadType}.
         */
        private String payloadType;

        public PayloadType getPayloadTypeEnum() {
            if (payloadType == null || payloadType.isBlank()) {
                return null; // Or throw exception, or return UNKNOWN based on strictness
            }
            try {
                return PayloadType.valueOf(payloadType.toUpperCase());
            } catch (IllegalArgumentException e) {
                // Log warning or handle error for invalid payloadType string
                return null; // Or a default/UNKNOWN
            }
        }
    }

    // Helper methods to get properties for @KafkaListener SpEL expressions
    // These assume you have a "primary" or a uniquely identifiable consumer configuration
    // if you want to use SpEL directly in @KafkaListener for topics/groupId.
    // A more robust approach for multiple dynamic listeners involves KafkaListenerEndpointRegistrar.

    public String[] getPrimaryConsumerTopics() {
        return consumers.stream()
                .filter(c -> c.isEnabled() && "default-consumer".equals(c.getName())) // Example: find by a specific name
                .findFirst()
                .map(c -> c.getTopics().toArray(new String[0]))
                .orElse(new String[]{"default-topic-not-configured"}); // Fallback
    }

    public String getPrimaryConsumerGroupId() {
        return consumers.stream()
                .filter(c -> c.isEnabled() && "default-consumer".equals(c.getName()))
                .findFirst()
                .map(ConsumerConfig::getGroupId)
                .orElse("default-group-id-not-configured"); // Fallback
    }
}
