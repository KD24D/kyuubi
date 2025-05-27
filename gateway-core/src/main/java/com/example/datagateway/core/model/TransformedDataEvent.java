package com.example.datagateway.core.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * Represents data that has been processed by the Transformation Engine.
 * This event is the output of the Transformation Engine and input to the
 * Intelligent Rate Limiting and Routing Layer.
 */
@Value
@Builder
public class TransformedDataEvent {
    /**
     * The unique request ID, propagated from the {@link NormalizedDataEvent}.
     */
    String requestId;

    /**
     * Information about the original source of the data, propagated from the {@link NormalizedDataEvent}.
     */
    SourceInfo sourceInfo;

    /**
     * The payload after transformation. The structure of this object can vary greatly
     * depending on the transformation rules applied. It could be a {@code Map<String, Object>}
     * for JSON-like structures, a new Protobuf message, a String, or even raw {@code byte[]}.
     */
    Object transformedPayload;

    /**
     * The {@link PayloadType} of the {@code transformedPayload}. This might be the same as the
     * input to the transformation stage or a new type if the transformation involved
     * changing the fundamental data format.
     */
    PayloadType payloadType;

    /**
     * The status of the transformation process (e.g., SUCCESS, FAILURE).
     */
    TransformationStatus status;

    /**
     * A list of error messages if the transformation failed.
     * This list will be empty if {@code status} is {@link TransformationStatus#SUCCESS}.
     */
    @Singular
    List<String> transformationErrors;

    /**
     * Metadata related to the processing within the transformation engine.
     * Examples: Version of transformation rules applied, identifiers of specific
     * transformation steps executed.
     */
    Map<String, String> processingMetadata;
}
