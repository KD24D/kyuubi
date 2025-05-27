package com.example.datagateway.core.service;

import com.example.datagateway.core.model.EgressProcessingResult;
import com.example.datagateway.core.model.RoutedEvent;
import reactor.core.publisher.Mono;

/**
 * Defines the contract for the Egress/Dispatch stage.
 * This service is responsible for sending the routed event to its final destination.
 */
public interface EgressService {
    /**
     * Dispatches the given routed event to the destination specified within it.
     * This involves interacting with external systems (e.g., making an HTTP call,
     * sending a Kafka message, calling a gRPC service).
     *
     * @param event The {@link RoutedEvent} containing the payload and destination details.
     * @return A {@link Mono} emitting an {@link EgressProcessingResult} which indicates
     *         the outcome of the dispatch operation (success, failure, retryable failure),
     *         and may include metadata from the downstream system's response.
     *         If a critical, non-retryable error occurs during dispatch preparation
     *         (before an attempt is made), the Mono should signal an error.
     */
    Mono<EgressProcessingResult> dispatch(RoutedEvent event);
}
