package com.example.datagateway.core.service;

import com.example.datagateway.core.model.NormalizedDataEvent;
import com.example.datagateway.core.model.UnifiedInternalRequest;
import reactor.core.publisher.Mono;

/**
 * Defines the contract for the Format Normalization and Validation Layer.
 * This service is responsible for parsing, validating, and performing initial
 * normalization or conversions on incoming requests.
 */
public interface FormatNormalizationService {
    /**
     * Normalizes and validates the given unified internal request.
     * This involves parsing the raw payload based on its type, validating it
     * against predefined schemas (if any), and potentially performing initial
     * format conversions (e.g., CSV to an internal representation).
     *
     * @param request The {@link UnifiedInternalRequest} containing the raw payload and metadata.
     * @return A {@link Mono} emitting a {@link NormalizedDataEvent} which contains the
     *         parsed and validated data, along with validation results and any processing metadata.
     *         If a critical error occurs during normalization (e.g., unparseable payload),
     *         the Mono should signal an error.
     */
    Mono<NormalizedDataEvent> normalize(UnifiedInternalRequest request);
}
