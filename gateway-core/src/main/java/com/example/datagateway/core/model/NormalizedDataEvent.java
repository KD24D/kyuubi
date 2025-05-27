package com.example.datagateway.core.model;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

/**
 * Represents data that has been parsed, validated, and potentially undergone initial normalization
 * or format conversion (e.g., CSV to an internal representation like Parquet bytes).
 * This event is the output of the Format Normalization and Conversion Layer.
 */
@Value
@Builder
public class NormalizedDataEvent {
    /**
     * The unique request ID, propagated from the {@link UnifiedInternalRequest}.
     */
    String requestId;

    /**
     * Information about the original source of the data, propagated from the {@link UnifiedInternalRequest}.
     */
    SourceInfo sourceInfo;

    /**
     * The parsed payload. The type of this object depends on the original format:
     * - For JSON: Typically a {@code Map<String, Object>} or a specific domain object if deserialized to POJO.
     * - For Protobuf: The generated Protobuf message object.
     * - For CSV: Typically a {@code List<List<String>>}, {@code List<Map<String, String>>},
     *            or a list of specific domain objects if mapped.
     * - For XML: A DOM object, a JAXB object, or a {@code Map<String, Object>}.
     */
    Object parsedPayload;

    /**
     * The type of the payload as it was received and parsed (e.g., JSON, CSV).
     * Propagated from {@link UnifiedInternalRequest#getPayloadType()}.
     */
    PayloadType originalPayloadType;

    /**
     * The type of the payload after processing by the normalization layer.
     * This could be the same as {@code originalPayloadType} or a new type if a conversion
     * occurred (e.g., CSV input converted to PARQUET output).
     */
    PayloadType processedPayloadType;

    /**
     * The payload after a format conversion, if one occurred (e.g., bytes of a Parquet file
     * if CSV was converted to Parquet). This field may be null if no such conversion took place
     * or if the {@code parsedPayload} itself is the result of the conversion.
     */
    byte[] convertedPayload;

    /**
     * The result of the validation process performed on the data.
     */
    ValidationResult validationResult;

    /**
     * Metadata related to the processing within the normalization layer.
     * Examples: Schema version used for validation, detected encoding.
     */
    Map<String, String> processingMetadata;
}
