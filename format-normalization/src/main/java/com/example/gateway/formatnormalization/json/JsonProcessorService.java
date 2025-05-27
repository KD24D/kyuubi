package com.example.gateway.formatnormalization.json;

import com.example.gateway.core.model.NormalizedDataEvent;
import com.example.gateway.core.model.PayloadType;
import com.example.gateway.core.model.UnifiedInternalRequest;
import com.example.gateway.core.model.ValidationResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class JsonProcessorService {

    private static final Logger logger = LoggerFactory.getLogger(JsonProcessorService.class);
    private final ObjectMapper objectMapper; // Standard Jackson ObjectMapper
    private final JsonSchemaFactory schemaFactory;

    public JsonProcessorService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        // Using V7 of JSON Schema spec, adjust if needed
        this.schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
    }

    public NormalizedDataEvent processJson(UnifiedInternalRequest uir) {
        NormalizedDataEvent event = new NormalizedDataEvent();
        event.setRequestId(uir.getRequestId());
        event.setSourceInfo(uir.getSourceInfo());
        event.setOriginalPayloadType(PayloadType.JSON);
        event.setProcessingMetadata(uir.getMetadata()); // Carry over metadata

        ValidationResult validationResult = new ValidationResult(true, new ArrayList<>()); // Assume valid initially
        Object parsedPayload = null;
        String jsonString = null;

        try {
            if (uir.getPayload() instanceof byte[]) {
                String encoding = uir.getOriginalEncoding() != null ? uir.getOriginalEncoding() : StandardCharsets.UTF_8.name();
                jsonString = new String((byte[]) uir.getPayload(), encoding);
            } else if (uir.getPayload() instanceof String) {
                jsonString = (String) uir.getPayload();
            } else {
                throw new IllegalArgumentException("Unsupported JSON payload type: " + uir.getPayload().getClass().getName());
            }

            // Parse into a generic Map<String, Object> for now
            // Could also parse into JsonNode if preferred for schema validation
            parsedPayload = objectMapper.readValue(jsonString, new TypeReference<Map<String, Object>>() {});
            event.setParsedPayload(parsedPayload);
            event.setProcessedPayloadType(PayloadType.JSON); // Still JSON after parsing

            // Placeholder for schema loading and validation
            // JsonSchema schema = loadSchemaForRequest(uir); // Implement this logic
            // if (schema != null) {
            //    JsonNode jsonNode = objectMapper.readTree(jsonString); // Validator often needs JsonNode
            //    Set<ValidationMessage> errors = schema.validate(jsonNode);
            //    if (!errors.isEmpty()) {
            //        validationResult.setValid(false);
            //        validationResult.setValidationErrors(
            //                errors.stream().map(ValidationMessage::getMessage).collect(Collectors.toList())
            //        );
            //        logger.warn("JSON validation failed for request {}: {}", uir.getRequestId(), errors);
            //    } else {
            //        logger.debug("JSON validation successful for request {}", uir.getRequestId());
            //    }
            // } else {
            //    logger.debug("No JSON schema found/configured for request {}, skipping schema validation.", uir.getRequestId());
            // }

        } catch (JsonProcessingException e) {
            logger.error("Error parsing JSON for request {}: {}", uir.getRequestId(), e.getMessage());
            validationResult.setValid(false);
            validationResult.getValidationErrors().add("Invalid JSON format: " + e.getMessage());
        } catch (IOException e) { // Covers encoding issues as well
            logger.error("IOException processing JSON payload for request {}: {}", uir.getRequestId(), e.getMessage());
            validationResult.setValid(false);
            validationResult.getValidationErrors().add("Error reading JSON payload: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument for JSON processing, request {}: {}", uir.getRequestId(), e.getMessage());
            validationResult.setValid(false);
            validationResult.getValidationErrors().add(e.getMessage());
        }

        event.setValidationResult(validationResult);
        if (!validationResult.isValid() && parsedPayload == null) {
             // If parsing failed completely, there's no parsed payload to carry forward.
             // The original payload might be logged or sent to DLQ by orchestrator.
             event.setParsedPayload(null); // Ensure it's null if parsing failed
        }
        return event;
    }

    // Placeholder for schema loading logic.
    // In a real system, this would load a schema from a file, a configuration server,
    // or a cache, based on information in the UnifiedInternalRequest (e.g., source, metadata).
    private JsonSchema loadSchemaForRequest(UnifiedInternalRequest uir) {
        // Example: load schema "order.schema.json" if source is "kafka:orders-topic"
        String schemaName = null;
        if (uir.getSourceInfo() != null && "kafka:orders-topic".equals(uir.getSourceInfo().getRequestTarget())) {
            schemaName = "order.schema.json";
        }

        if (schemaName != null) {
            try (InputStream schemaStream = getClass().getClassLoader().getResourceAsStream("schemas/" + schemaName)) {
                if (schemaStream == null) {
                    logger.warn("Schema file not found: {}", schemaName);
                    return null;
                }
                // For networknt validator, it can take JsonNode or InputStream directly
                JsonNode schemaNode = objectMapper.readTree(schemaStream);
                return schemaFactory.getSchema(schemaNode);
            } catch (Exception e) {
                logger.error("Error loading or parsing JSON schema {}: {}", schemaName, e.getMessage(), e);
                return null;
            }
        }
        return null; // No schema applicable
    }
}
