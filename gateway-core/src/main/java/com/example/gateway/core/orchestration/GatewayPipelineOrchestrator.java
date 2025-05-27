package com.example.gateway.core.orchestration;

import com.example.gateway.core.model.*;
// Import services from other modules. These will be Autowired.
// For this subtask, actual implementations of these services are assumed to exist,
// but their full integration (e.g., error handling details, specific method signatures if they evolved)
// will be refined here and in later integration steps.

// Assuming services from previous steps. Actual classes might be in different packages
// depending on where they were placed (e.g., formatnormalization.json.JsonProcessorService).
// For this subtask, we'll use placeholder interfaces/classes if direct imports are problematic
// or rely on Spring's DI to wire them up correctly by type.

    import com.example.gateway.core.egress.EgressException; // Import EgressException
    import com.example.gateway.core.egress.EgressService; // Import EgressService
import com.example.gateway.formatnormalization.csv.CsvProcessorService;
import com.example.gateway.formatnormalization.json.JsonProcessorService;
import com.example.gateway.formatnormalization.protobuf.ProtobufProcessorService; // Mocked version
import com.example.gateway.routinglimiting.ratelimiting.RateLimitingService;
import com.example.gateway.routinglimiting.ratelimiting.RateLimitExceededException; // Custom exception
// Using the existing RoutingException from the routing package
import com.example.gateway.routinglimiting.routing.RoutingException;
import com.example.gateway.routinglimiting.routing.RoutingService;
import com.example.gateway.transformation.GroovyTransformationService;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Collections; // For emptyList
import java.util.List;      // For List<String> scriptIds
import java.util.Optional;  // For Optional from routingService

@Service
public class GatewayPipelineOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(GatewayPipelineOrchestrator.class);

    // Format Normalization Services
    private final JsonProcessorService jsonProcessorService;
    private final ProtobufProcessorService protobufProcessorService; // Mocked
    private final CsvProcessorService csvProcessorService;

    // Transformation Service
    private final GroovyTransformationService groovyTransformationService;

    // Rate Limiting and Routing Services
    private final RateLimitingService rateLimitingService;
    private final RoutingService routingService;

    // Egress Service (to be implemented in Step 8)
    // For now, the pipeline will end with routing.
    private final EgressService egressService; // Add this

    @Autowired
    public GatewayPipelineOrchestrator(
            JsonProcessorService jsonProcessorService,
            ProtobufProcessorService protobufProcessorService,
            CsvProcessorService csvProcessorService,
            GroovyTransformationService groovyTransformationService,
            RateLimitingService rateLimitingService,
            RoutingService routingService,
            EgressService egressService // Add EgressService to constructor
    ) {
        this.jsonProcessorService = jsonProcessorService;
        this.protobufProcessorService = protobufProcessorService;
        this.csvProcessorService = csvProcessorService;
        this.groovyTransformationService = groovyTransformationService;
        this.rateLimitingService = rateLimitingService;
        this.routingService = routingService;
        this.egressService = egressService; // Initialize
    }

    /**
     * Processes a UnifiedInternalRequest through the full pipeline.
     * This version is for asynchronous processing (e.g., from Kafka)
     * where the final result might be sending to another Kafka topic or an async HTTP call.
     */
    public Mono<Void> processRequestAsync(UnifiedInternalRequest uir) {
        return normalizeRequest(uir)
                .flatMap(this::transformRequest)
                .flatMap(this::rateLimitAndRouteRequest)
                .flatMap(this::dispatchRequest) // UNCOMMENT/ADD this line
                .doOnSuccess(egressResult -> { // Update to handle EgressProcessingResult
                    if (egressResult != null && egressResult.getStatus() == EgressStatus.SUCCESS) {
                         logger.info("Async pipeline and egress completed successfully for request ID: {}. Destination: {}", egressResult.getRequestId(), egressResult.getDestination().getTarget());
                    } else if (egressResult != null) {
                         logger.warn("Async pipeline completed but egress failed or was skipped for request ID: {}. Status: {}, Reason: {}", egressResult.getRequestId(), egressResult.getStatus(), egressResult.getFailureReason());
                    } else {
                         logger.info("Async pipeline completed for request ID: {}. No routing decision or event discarded before egress.", uir.getRequestId());
                    }
                })
                .doOnError(error -> logger.error("Async pipeline error for request ID {}: {}", uir.getRequestId(), error.getMessage(), error))
                .then();
    }

    /**
     * Processes a UnifiedInternalRequest and aims to return a ResponseEntity.
     * Suitable for synchronous interactions like HTTP.
     */
    public Mono<ResponseEntity<String>> processRequestSync(UnifiedInternalRequest uir) {
        return normalizeRequest(uir)
                .flatMap(this::transformRequest)
                .flatMap(this::rateLimitAndRouteRequest)
                .flatMap(this::dispatchRequestAndGetResponse) // MODIFY/REPLACE this line if egress gives response
                // .map(egressResult -> { ... }) // Original map is now part of dispatchRequestAndGetResponse or handled after it
                .onErrorResume(RateLimitExceededException.class, ex -> {
                    logger.warn("Rate limit exceeded for request {}: {}", uir.getRequestId(), ex.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("Rate limit exceeded: " + ex.getMessage()));
                })
                .onErrorResume(ValidationException.class, ex -> { // Custom exception for validation failures
                    logger.warn("Validation failed for request {}: {}", uir.getRequestId(), ex.getMessage());
                    return Mono.just(ResponseEntity.badRequest().body("Validation failed: " + String.join(", ", ex.getErrors())));
                })
                .onErrorResume(TransformationException.class, ex -> { // Custom exception for transformation failures
                    logger.error("Transformation error for request {}: {}", uir.getRequestId(), ex.getMessage(), ex);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Transformation error: " + String.join(", ", ex.getErrors())));
                })
                .onErrorResume(RoutingException.class, ex -> { // Custom exception for routing failures
                    logger.error("Routing error for request {}: {}", uir.getRequestId(), ex.getMessage(), ex);
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("Routing error: " + ex.getMessage()));
                })
                .onErrorResume(EgressException.class, ex -> { // Custom EgressException
                    logger.error("Egress error for request {}: {}", uir.getRequestId(), ex.getMessage(), ex);
                    return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Egress error: " + ex.getMessage()));
                })
                .onErrorResume(Exception.class, ex -> { // Catch-all for other unexpected errors
                    logger.error("Unexpected pipeline error for request {}: {}", uir.getRequestId(), ex.getMessage(), ex);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Unexpected error processing request. ID: " + uir.getRequestId()));
                });
    }


    // Stage 1: Normalization
    private Mono<NormalizedDataEvent> normalizeRequest(UnifiedInternalRequest uir) {
        return Mono.fromCallable(() -> {
            logger.debug("Stage 1: Normalizing request ID: {}", uir.getRequestId());
            NormalizedDataEvent nde;
            switch (uir.getPayloadType()) {
                case JSON:
                    nde = jsonProcessorService.processJson(uir);
                    break;
                case PROTOBUF:
                    nde = protobufProcessorService.processProtobuf(uir); // Mocked
                    break;
                case CSV:
                    nde = csvProcessorService.processCsv(uir);
                    break;
                // case XML, BINARY, TEXT, UNKNOWN: // Add cases as needed
                default:
                    logger.warn("Unsupported payload type {} for normalization. Request ID: {}", uir.getPayloadType(), uir.getRequestId());
                    // Create a NormalizedDataEvent indicating failure or pass-through
                    nde = new NormalizedDataEvent();
                    nde.setRequestId(uir.getRequestId());
                    nde.setSourceInfo(uir.getSourceInfo());
                    nde.setOriginalPayloadType(uir.getPayloadType());
                    nde.setProcessedPayloadType(PayloadType.UNKNOWN); // Or pass through original
                    nde.setValidationResult(new ValidationResult(false, List.of("Unsupported payload type for normalization: " + uir.getPayloadType())));
                    nde.setParsedPayload(uir.getPayload()); // Pass original payload
                    break;
            }

            if (nde.getValidationResult() != null && !nde.getValidationResult().isValid()) {
                logger.warn("Validation failed during normalization for request ID: {}. Errors: {}", uir.getRequestId(), nde.getValidationResult().getValidationErrors());
                throw new ValidationException("Normalization/Validation failed", nde.getValidationResult().getValidationErrors());
            }
            logger.info("Stage 1: Normalization complete for request ID: {}. Output payload type: {}", uir.getRequestId(), nde.getProcessedPayloadType());
            return nde;
        });
    }

    // Stage 2: Transformation
    private Mono<TransformedDataEvent> transformRequest(NormalizedDataEvent nde) {
        return Mono.fromCallable(() -> {
            logger.debug("Stage 2: Transforming request ID: {}", nde.getRequestId());
            List<String> scriptIdsToApply = Collections.emptyList();
            // Example of how script selection could be done (needs actual config mechanism - Step 9)
            // if (nde.getSourceInfo() != null && "HTTP:/ingest/jsonOrders".equals(nde.getSourceInfo().getRequestTarget())) {
            //     scriptIdsToApply = List.of("map_fields.groovy"); // Ensure 'map_fields.groovy' exists and is appropriate
            // }

            TransformedDataEvent tde = groovyTransformationService.transform(nde, scriptIdsToApply);

            if (tde.getStatus() == TransformationStatus.FAILURE) {
                logger.error("Transformation failed for request ID: {}. Errors: {}", nde.getRequestId(), tde.getTransformationErrors());
                throw new TransformationException("Transformation failed", tde.getTransformationErrors());
            }
            logger.info("Stage 2: Transformation complete for request ID: {}. Output payload type: {}", nde.getRequestId(), tde.getPayloadType());
            return tde;
        });
    }

    // Stage 3: Rate Limiting & Routing
    private Mono<RoutedEvent> rateLimitAndRouteRequest(TransformedDataEvent tde) {
        return Mono.fromCallable(() -> {
            logger.debug("Stage 3: Rate Limiting and Routing request ID: {}", tde.getRequestId());

            // 3a. Rate Limiting
            if (!rateLimitingService.tryConsume(tde)) {
                throw new RateLimitExceededException(tde.getRequestId(), "Rate limit exceeded for request ID: " + tde.getRequestId());
            }
            logger.debug("Rate limiting check passed for request ID: {}", tde.getRequestId());

            // 3b. Routing
            Optional<RoutedEvent> routedEventOpt = routingService.evaluateRoutes(tde);
            if (routedEventOpt.isEmpty()) {
                logger.error("Routing failed to produce a decision for request ID: {}", tde.getRequestId());
                throw new RoutingException("Routing failed to produce a decision for request ID: " + tde.getRequestId());
            }
            RoutedEvent routedEvent = routedEventOpt.get();
            logger.info("Stage 3: Routing complete for request ID: {}. Destination: {}", tde.getRequestId(), routedEvent.getDestination().getTarget());
            return routedEvent;
        });
    }

    // Stage 4: Egress/Dispatch (To be implemented in Step 8)
    private Mono<EgressProcessingResult> dispatchRequest(RoutedEvent re) {
        if (re.getDestination().getType() == DestinationType.DISCARD) {
            logger.info("Request {} to be discarded at egress stage.", re.getRequestId());
            return Mono.just(new EgressProcessingResult(re.getRequestId(), re.getDestination(), EgressStatus.SUCCESS, "Discarded as per routing", 1, null));
        }
        logger.debug("Stage 4: Dispatching request ID: {}", re.getRequestId());
        return egressService.dispatch(re);
    }

    // Add dispatchRequestAndGetResponse method for sync flow
    private Mono<ResponseEntity<String>> dispatchRequestAndGetResponse(RoutedEvent re) {
        if (re.getDestination().getType() == DestinationType.DISCARD) {
            logger.info("Request {} processed and discarded as per routing rules (sync flow).", re.getRequestId());
            return Mono.just(ResponseEntity.ok().body("Request " + re.getRequestId() + " processed and discarded."));
        }
        logger.debug("Stage 4: Dispatching request ID {} for sync response", re.getRequestId());
        return egressService.dispatch(re)
            .map(egressResult -> {
                if (egressResult.getStatus() == EgressStatus.SUCCESS) {
                    // For HTTP egress, response might be in egressResult.getResponseMetadata()
                    // For Kafka egress, usually just ack.
                    String successBody = "Request " + egressResult.getRequestId() + " processed and dispatched to " + egressResult.getDestination().getTarget();
                    if (egressResult.getResponseMetadata() != null && egressResult.getResponseMetadata().containsKey("httpStatusCode")) {
                        // If it was an HTTP call, could try to proxy status/body if needed, or just confirm dispatch
                        successBody += " (Downstream Status: " + egressResult.getResponseMetadata().get("httpStatusCode") + ")";
                    }
                    return ResponseEntity.status(HttpStatus.OK).body(successBody);
                } else {
                    throw new EgressException("Egress failed for " + egressResult.getDestination().getTarget() + ": " + egressResult.getFailureReason(), egressResult.getFailureReason());
                }
            });
    }

}

// Define custom exceptions used in the pipeline for specific error handling
class ValidationException extends RuntimeException {
    private final List<String> errors;
    public ValidationException(String message, List<String> errors) {
        super(message);
        this.errors = errors != null ? errors : Collections.emptyList();
    }
    public List<String> getErrors() { return errors; }
}

class TransformationException extends RuntimeException {
     private final List<String> errors;
    public TransformationException(String message, List<String> errors) {
        super(message);
        this.errors = errors != null ? errors : Collections.emptyList();
    }
     public List<String> getErrors() { return errors; }
}

// RoutingException is already defined in com.example.gateway.routinglimiting.routing.RoutingException
// No need to redefine it here if that one is accessible and suitable.
// If it needs to be specific to the orchestrator or carry more/different info, then define a new one here.
// For now, assuming the existing one is sufficient.
// class RoutingException extends RuntimeException {
//     public RoutingException(String message) { super(message); }
// }

// EgressException class definition is expected in its own file or a shared exceptions package.
// If it's defined here for simplicity, ensure it's correctly placed or moved later.
// For this subtask, we'll assume it will be created in com.example.gateway.core.egress.EgressException
