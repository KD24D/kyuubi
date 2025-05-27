package com.example.datagateway.formatnormalization.service;

import com.example.datagateway.core.model.NormalizedDataEvent;
import com.example.datagateway.core.model.PayloadType;
import com.example.datagateway.core.model.UnifiedInternalRequest;
import com.example.datagateway.core.model.ValidationResult;
import com.example.datagateway.core.service.FormatNormalizationService;
import com.example.datagateway.formatnormalization.processor.CsvProcessor;
import com.example.datagateway.formatnormalization.processor.JsonProcessor;
import com.example.datagateway.formatnormalization.processor.ProtobufProcessor;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.protobuf.Message;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Default implementation of {@link FormatNormalizationService}.
 * This service dispatches requests to appropriate processors based on payload type,
 * handles parsing, validation, and initial format conversions.
 */
@Service
@Slf4j
public class DefaultFormatNormalizationService implements FormatNormalizationService {

    private final JsonProcessor jsonProcessor;
    private final ProtobufProcessor protobufProcessor;
    private final CsvProcessor csvProcessor;

    /**
     * Constructs the service with required processors.
     *
     * @param jsonProcessor     Processor for JSON data.
     * @param protobufProcessor Processor for Protobuf data.
     * @param csvProcessor      Processor for CSV data.
     */
    @Autowired
    public DefaultFormatNormalizationService(
            JsonProcessor jsonProcessor,
            ProtobufProcessor protobufProcessor,
            CsvProcessor csvProcessor) {
        this.jsonProcessor = jsonProcessor;
        this.protobufProcessor = protobufProcessor;
        this.csvProcessor = csvProcessor;
    }

    @Override
    public Mono<NormalizedDataEvent> normalize(UnifiedInternalRequest request) {
        return Mono.fromCallable(() -> {
            log.debug("Normalizing request: {}", request.getRequestId());
            PayloadType originalType = request.getPayloadType();
            Object parsedPayload = null;
            ValidationResult validationResult = ValidationResult.builder().isValid(true).build();
            byte[] convertedPayload = null;
            PayloadType processedPayloadType = originalType; // Initially same as original

            String sourceIdentifier = request.getSourceInfo().getRequestTarget(); // Or a more specific configured ID

            try {
                byte[] payloadBytes = getPayloadAsBytes(request.getPayload());
                String encoding = request.getOriginalEncoding();

                switch (originalType) {
                    case JSON:
                        JsonNode jsonNode = jsonProcessor.parseToJsonNode(payloadBytes, encoding);
                        parsedPayload = jsonNode; // Store JsonNode for validation, can be converted to Map later if needed
                        validationResult = jsonProcessor.validate(jsonNode, sourceIdentifier);
                        // If parsedPayload for downstream should be Map:
                        // parsedPayload = jsonProcessor.parse(payloadBytes, encoding); // Map<String, Object>
                        break;
                    case PROTOBUF:
                        Message protobufMessage = protobufProcessor.parse(payloadBytes, sourceIdentifier);
                        parsedPayload = protobufMessage;
                        // Protobuf has schema validation inherent in parsing to a specific type.
                        // Further business validation could be added via Generic Data Validation Framework.
                        break;
                    case CSV:
                        List<CSVRecord> csvRecords = csvProcessor.parse(payloadBytes, encoding, sourceIdentifier);
                        parsedPayload = csvRecords; // List of CSVRecords
                        // CSV validation can be structural (column count) or content-based (via generic validator)
                        // For now, basic parsing success is the validation.
                        // Example: Convert CSV to Parquet
                        // This is a common initial transformation/normalization.
                        try {
                            convertedPayload = csvProcessor.convertToParquet(csvRecords, sourceIdentifier);
                            processedPayloadType = PayloadType.PARQUET;
                            log.info("Successfully converted CSV to Parquet for request {}", request.getRequestId());
                        } catch (IOException e) {
                            log.error("Failed to convert CSV to Parquet for request {}: {}", request.getRequestId(), e.getMessage(), e);
                            validationResult = ValidationResult.builder().isValid(false)
                                    .validationError("CSV to Parquet conversion failed: " + e.getMessage()).build();
                            // Decide if this failure makes the whole event invalid or just logs and continues with CSV
                        }
                        break;
                    case XML:
                    case TEXT:
                    case BINARY:
                    case UNKNOWN:
                        log.warn("PayloadType {} for request {} is not fully processed by normalization layer (passthrough or basic handling).",
                                originalType, request.getRequestId());
                        parsedPayload = payloadBytes; // Keep as byte array for these types for now
                        // No specific validation applied here by default.
                        break;
                    default:
                        log.error("Unsupported PayloadType: {} for request {}", originalType, request.getRequestId());
                        throw new IllegalArgumentException("Unsupported PayloadType: " + originalType);
                }

                if (!validationResult.isValid()) {
                    log.warn("Validation failed for request {}: {}", request.getRequestId(), validationResult.getValidationErrors());
                }

            } catch (Exception e) {
                log.error("Error during format normalization for request {}: {}", request.getRequestId(), e.getMessage(), e);
                validationResult = ValidationResult.builder()
                        .isValid(false)
                        .validationError("Normalization failed: " + e.getMessage())
                        .build();
                // Depending on error, parsedPayload might be null or partially processed.
                // For critical parsing errors, parsedPayload should remain null or indicate failure.
                parsedPayload = request.getPayload(); // Fallback to original payload if parsing fails severely
            }

            return NormalizedDataEvent.builder()
                    .requestId(request.getRequestId())
                    .sourceInfo(request.getSourceInfo())
                    .parsedPayload(parsedPayload)
                    .originalPayloadType(originalType)
                    .processedPayloadType(processedPayloadType)
                    .convertedPayload(convertedPayload)
                    .validationResult(validationResult)
                    .processingMetadata(Collections.emptyMap()) // Populate with relevant metadata if any
                    .build();
        });
    }

    private byte[] getPayloadAsBytes(Object payload) {
        if (payload instanceof byte[]) {
            return (byte[]) payload;
        } else if (payload instanceof String) {
            return ((String) payload).getBytes(StandardCharsets.UTF_8); // Assuming UTF-8 for strings
        } else if (payload == null) {
            return new byte[0];
        }
        // Add other conversions if necessary, or throw exception for unsupported types
        throw new IllegalArgumentException("Unsupported payload object type: " + payload.getClass().getName());
    }
}
