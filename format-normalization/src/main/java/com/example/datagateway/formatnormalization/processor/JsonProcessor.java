package com.example.datagateway.formatnormalization.processor;

import com.example.datagateway.core.model.ValidationResult;
import com.example.datagateway.formatnormalization.config.JsonValidationProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Processor for handling JSON data, including parsing and schema validation.
 */
@Component
@Slf4j
public class JsonProcessor {

    private final ObjectMapper objectMapper;
    private final JsonValidationProperties jsonValidationProperties;
    private final ResourceLoader resourceLoader = new DefaultResourceLoader();
    private final Map<String, JsonSchema> schemaCache = new ConcurrentHashMap<>();
    private final JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V201909); // Or V7, etc.

    /**
     * Constructs a JsonProcessor.
     *
     * @param objectMapper           Jackson ObjectMapper for JSON operations.
     * @param jsonValidationProperties Configuration properties for JSON validation.
     */
    @Autowired
    public JsonProcessor(ObjectMapper objectMapper, JsonValidationProperties jsonValidationProperties) {
        this.objectMapper = objectMapper;
        this.jsonValidationProperties = jsonValidationProperties;
    }

    /**
     * Parses a JSON payload from byte array into a Map.
     *
     * @param jsonBytes The byte array containing JSON data.
     * @param encoding  The character encoding of the byte array.
     * @return A Map representing the parsed JSON object.
     * @throws IOException If parsing fails.
     */
    public Map<String, Object> parse(byte[] jsonBytes, String encoding) throws IOException {
        String jsonString = new String(jsonBytes, encoding != null ? encoding : jsonValidationProperties.getDefaultEncoding());
        return objectMapper.readValue(jsonString, new TypeReference<>() {});
    }

    /**
     * Parses a JSON payload from a String into a Map.
     *
     * @param jsonString The String containing JSON data.
     * @return A Map representing the parsed JSON object.
     * @throws JsonProcessingException If parsing fails.
     */
    public Map<String, Object> parse(String jsonString) throws JsonProcessingException {
        return objectMapper.readValue(jsonString, new TypeReference<>() {});
    }

    /**
     * Parses a JSON payload from byte array into a JsonNode.
     *
     * @param jsonBytes The byte array containing JSON data.
     * @param encoding  The character encoding of the byte array.
     * @return A JsonNode representing the root of the parsed JSON.
     * @throws IOException If parsing fails.
     */
    public JsonNode parseToJsonNode(byte[] jsonBytes, String encoding) throws IOException {
        String jsonString = new String(jsonBytes, encoding != null ? encoding : jsonValidationProperties.getDefaultEncoding());
        return objectMapper.readTree(jsonString);
    }


    /**
     * Validates a JsonNode against a JSON schema identified by a source key.
     * The schema location is retrieved from configuration.
     *
     * @param jsonNode         The JsonNode to validate.
     * @param sourceIdentifier A key to look up the schema location in configuration.
     * @return A {@link ValidationResult} indicating success or failure with error messages.
     */
    public ValidationResult validate(JsonNode jsonNode, String sourceIdentifier) {
        if (!jsonValidationProperties.isSchemaValidationEnabled()) {
            return ValidationResult.builder().isValid(true).validationErrors(Collections.emptyList()).build();
        }

        String schemaLocation = jsonValidationProperties.getSchemaMappings().get(sourceIdentifier);
        if (schemaLocation == null || schemaLocation.isBlank()) {
            log.debug("No JSON schema mapping found for sourceIdentifier: {}. Skipping validation.", sourceIdentifier);
            return ValidationResult.builder().isValid(true).validationErrors(Collections.emptyList()).build();
        }

        try {
            JsonSchema schema = getSchema(schemaLocation);
            Set<ValidationMessage> errors = schema.validate(jsonNode);
            if (errors.isEmpty()) {
                return ValidationResult.builder().isValid(true).build();
            } else {
                return ValidationResult.builder()
                        .isValid(false)
                        .validationErrors(errors.stream().map(ValidationMessage::getMessage).collect(Collectors.toList()))
                        .build();
            }
        } catch (Exception e) {
            log.error("Error during JSON schema validation for source '{}' with schema '{}': {}",
                    sourceIdentifier, schemaLocation, e.getMessage(), e);
            return ValidationResult.builder()
                    .isValid(false)
                    .validationError("Schema validation failed: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Loads a JSON schema from the given location (classpath, file, URL).
     * Caches schemas if caching is enabled in properties.
     *
     * @param schemaLocation The location of the schema.
     * @return The loaded {@link JsonSchema}.
     * @throws IOException If the schema cannot be loaded.
     */
    protected JsonSchema getSchema(String schemaLocation) throws IOException {
        if (jsonValidationProperties.isCacheSchemas() && schemaCache.containsKey(schemaLocation)) {
            return schemaCache.get(schemaLocation);
        }

        InputStream schemaStream = null;
        try {
            if (schemaLocation.startsWith("http://") || schemaLocation.startsWith("https://")) {
                // For URL based schemas, JsonSchemaFactory can load them directly if it supports the protocol.
                // Or use a HTTP client to fetch it. For simplicity, assume factory handles it or use ResourceLoader.
                schemaStream = URI.create(schemaLocation).toURL().openStream();
            } else {
                Resource schemaResource = resourceLoader.getResource(schemaLocation);
                if (!schemaResource.exists()) {
                    throw new IOException("Schema resource not found: " + schemaLocation);
                }
                schemaStream = schemaResource.getInputStream();
            }

            // The SchemaValidatorsConfig can be used to customize validation behavior, e.g., format handling
            SchemaValidatorsConfig config = new SchemaValidatorsConfig();
            // config.setFailFast(true); // Example customization

            JsonSchema schema = schemaFactory.getSchema(schemaStream, config);

            if (jsonValidationProperties.isCacheSchemas()) {
                schemaCache.put(schemaLocation, schema);
            }
            return schema;
        } finally {
            if (schemaStream != null) {
                try {
                    schemaStream.close();
                } catch (IOException e) {
                    log.warn("Error closing schema stream for location {}: {}", schemaLocation, e.getMessage());
                }
            }
        }
    }
}
