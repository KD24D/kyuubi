package com.example.datagateway.formatnormalization.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for JSON validation.
 * Allows mapping source identifiers (e.g., Kafka topics, HTTP paths) to JSON schema locations.
 * <p>
 * Example YAML:
 * <pre>
 * gateway:
 *   format-normalization:
 *     json:
 *       default-encoding: "UTF-8"
 *       schema-validation-enabled: true # Global toggle
 *       schema-mappings:
 *         "kafka://orders-topic": "classpath:schemas/json/order-schema.json"
 *         "/ingest/user-profile": "file:/etc/gateway/schemas/user-profile-schema.json"
 *         "http://some.external/source": "http://example.com/schemas/external-event.json"
 *       # cache-schemas: true # For future optimization
 * </pre>
 */
@ConfigurationProperties(prefix = "gateway.format-normalization.json")
@Component
@Validated
@Data
public class JsonValidationProperties {

    /**
     * Default character encoding to use for converting byte[] to String for JSON processing
     * if not specified in the UnifiedInternalRequest.
     */
    @NotBlank
    private String defaultEncoding = "UTF-8";

    /**
     * Global flag to enable or disable JSON schema validation.
     * If false, no schema validation will be attempted even if mappings exist.
     */
    private boolean schemaValidationEnabled = true;

    /**
     * Whether to cache JSON schemas after loading them for the first time.
     * This can improve performance if schemas are fetched from URLs or classpath repeatedly.
     */
    private boolean cacheSchemas = true;

    /**
     * A map where the key is a source identifier (e.g., Kafka topic name, HTTP endpoint path pattern)
     * and the value is the location of the JSON schema file.
     * Locations can be classpath resources (e.g., "classpath:schemas/my-schema.json"),
     * file system paths (e.g., "file:/path/to/schema.json"), or URLs (e.g., "http://example.com/schema.json").
     */
    private Map<String, String> schemaMappings = new HashMap<>();
}
