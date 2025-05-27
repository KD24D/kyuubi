package com.example.datagateway.core.service;

import com.example.datagateway.core.model.UnifiedInternalRequest;
import org.springframework.http.ResponseEntity; // This is why spring-web was added as compileOnly
import reactor.core.publisher.Mono;

/**
 * Defines the contract for the Core Processing Pipeline Orchestrator.
 * This service is responsible for managing the end-to-end flow of data
 * through the various processing stages of the Data Gateway.
 */
public interface GatewayPipelineOrchestrator {

    /**
     * Processes an asynchronous request through the gateway pipeline.
     * This typically involves passing the request through normalization, transformation,
     * rate limiting, routing, and finally egress to one or more destinations.
     * This method is suitable for fire-and-forget message processing (e.g., from Kafka, MQTT)
     * where a direct synchronous response to the original caller is not required.
     *
     * @param requestMono A {@link Mono} emitting the {@link UnifiedInternalRequest}
     *                    representing the incoming data.
     * @return A {@link Mono<Void>} that completes when the processing is finished
     *         for all paths triggered by the request, or signals an error if the pipeline
     *         encounters an unrecoverable issue early in the process. Specific errors
     *         related to individual egress paths might be handled internally (e.g., via DLQs)
     *         rather than failing the entire Mono.
     */
    Mono<Void> processRequest(Mono<UnifiedInternalRequest> requestMono);

    /**
     * Processes a synchronous request (e.g., from an HTTP or gRPC call) through
     * the gateway pipeline, expecting a response to be sent back to the client.
     * The pipeline execution may still involve asynchronous operations internally,
     * but the overall interaction is designed to produce a single, consolidated response.
     *
     * @param requestMono A {@link Mono} emitting the {@link UnifiedInternalRequest}
     *                    representing the incoming data.
     * @return A {@link Mono} emitting a {@link ResponseEntity} (typically for HTTP responses)
     *         encapsulating the outcome of the processing. The body of the ResponseEntity
     *         (e.g., {@code String}) and status code will depend on the success or failure
     *         of the pipeline stages and the nature of the egress operations.
     *         For example, a successful end-to-end processing might return HTTP 200 OK,
     *         a rate-limiting rejection HTTP 429, a validation failure HTTP 400, etc.
     */
    Mono<ResponseEntity<String>> processHttpRequest(Mono<UnifiedInternalRequest> requestMono);
}
