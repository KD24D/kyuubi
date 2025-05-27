package com.example.datagateway.core.service;

import com.example.datagateway.core.model.NormalizedDataEvent;
import com.example.datagateway.core.model.TransformedDataEvent;
import reactor.core.publisher.Mono;

/**
 * Defines the contract for the Transformation Engine.
 * This service is responsible for applying complex data manipulations,
 * mapping, and enrichment logic to normalized data.
 */
public interface TransformationService {
    /**
     * Transforms the given normalized data event.
     * This involves applying configured transformation rules (e.g., scripts, mappings)
     * to the {@link NormalizedDataEvent#getParsedPayload()} or
     * {@link NormalizedDataEvent#getConvertedPayload()}.
     *
     * @param event The {@link NormalizedDataEvent} containing the data to be transformed.
     * @return A {@link Mono} emitting a {@link TransformedDataEvent} which contains the
     *         transformed payload and status. If a critical error occurs during transformation,
     *         the Mono should signal an error.
     */
    Mono<TransformedDataEvent> transform(NormalizedDataEvent event);
}
