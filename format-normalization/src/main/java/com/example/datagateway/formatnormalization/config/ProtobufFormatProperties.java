package com.example.datagateway.formatnormalization.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for Protobuf processing.
 * Allows mapping source identifiers to specific Protobuf message class names for parsing
 * and default settings for Protobuf-JSON conversions.
 * <p>
 * Example YAML:
 * <pre>
 * gateway:
 *   format-normalization:
 *     protobuf:
 *       # Mapping from a source identifier (e.g., Kafka topic, HTTP path) to the
 *       # fully qualified class name of the specific Protobuf message type expected from that source.
 *       message-type-mappings:
 *         "kafka://orders-protobuf-topic": "com.example.generated.OrderProtos.Order"
 *         "/ingest/product-updates": "com.example.generated.ProductProtos.ProductUpdate"
 *       # Default settings for JsonFormat.Printer (Protobuf to JSON)
 *       json-printer:
 *         preserving-proto-field-names: true
 *         including-default-value-fields: false
 *         # omit-field-presence: false # For proto3 optional fields, if needed
 *       # Default settings for JsonFormat.Parser (JSON to Protobuf)
 *       json-parser:
 *         ignoring-unknown-fields: true
 * </pre>
 */
@ConfigurationProperties(prefix = "gateway.format-normalization.protobuf")
@Component
@Validated
@Data
public class ProtobufFormatProperties {

    /**
     * A map where the key is a source identifier (e.g., Kafka topic name, HTTP endpoint path pattern)
     * and the value is the fully qualified Java class name of the Protobuf message type.
     * This mapping is crucial for parsing incoming Protobuf messages into specific types.
     */
    private Map<String, String> messageTypeMappings = new HashMap<>();

    /**
     * Configuration for the {@link com.google.protobuf.util.JsonFormat.Printer}.
     */
    private JsonPrinterConfig jsonPrinter = new JsonPrinterConfig();

    /**
     * Configuration for the {@link com.google.protobuf.util.JsonFormat.Parser}.
     */
    private JsonParserConfig jsonParser = new JsonParserConfig();

    @Data
    public static class JsonPrinterConfig {
        private boolean preservingProtoFieldNames = true;
        private boolean includingDefaultValueFields = false;
        // Add other printer options as needed, e.g., for specific field types or formatting options
    }

    @Data
    public static class JsonParserConfig {
        private boolean ignoringUnknownFields = true;
        // Add other parser options as needed
    }

    /**
     * Retrieves the Protobuf message class name for a given source identifier.
     *
     * @param sourceIdentifier The identifier of the data source.
     * @return The fully qualified class name if a mapping exists, otherwise null.
     */
    public String getMessageClassNameForSource(String sourceIdentifier) {
        if (sourceIdentifier == null || sourceIdentifier.isEmpty()) {
            return null;
        }
        // Direct match first
        if (messageTypeMappings.containsKey(sourceIdentifier)) {
            return messageTypeMappings.get(sourceIdentifier);
        }
        // Then check for pattern matches (more complex, consider if regex needed or simple startsWith/endsWith)
        // For now, only direct match is shown for simplicity.
        // For more complex matching, iterate through keys and use Pattern.matches.
        return null;
    }
}
