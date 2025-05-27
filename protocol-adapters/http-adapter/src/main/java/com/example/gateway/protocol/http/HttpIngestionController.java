package com.example.gateway.protocol.http;

import com.example.gateway.core.model.PayloadType;
import com.example.gateway.core.model.Protocol;
import com.example.gateway.core.model.SourceInfo;
import com.example.gateway.core.model.UnifiedInternalRequest;
// Import the orchestrator service. This will be a placeholder for now,
// actual interaction will be wired up when the orchestrator is built.
// For this subtask, you can assume a mock/placeholder for the orchestrator.
// import com.example.gateway.core.orchestration.GatewayPipelineOrchestrator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/ingest") // Base path for ingestion
public class HttpIngestionController {

    private static final Logger logger = LoggerFactory.getLogger(HttpIngestionController.class);

    // Placeholder for the pipeline orchestrator.
    // In a real setup, this would be @Autowired, and its processRequest method would be called.
    // For now, we'll just log the UnifiedInternalRequest.
    /*
    private final GatewayPipelineOrchestrator pipelineOrchestrator;

    @Autowired
    public HttpIngestionController(GatewayPipelineOrchestrator pipelineOrchestrator) {
        this.pipelineOrchestrator = pipelineOrchestrator;
    }
    */

    @PostMapping("/{sourceIdentifier}")
    public Mono<ResponseEntity<String>> handlePostRequest(
            @PathVariable String sourceIdentifier,
            @RequestHeader HttpHeaders headers,
            @RequestBody Mono<byte[]> requestBodyMono,
            ServerWebExchange exchange) {

        return requestBodyMono.flatMap(requestBodyBytes -> {
            String requestId = headers.getFirst("X-Request-ID");
            if (requestId == null || requestId.isEmpty()) {
                requestId = UUID.randomUUID().toString();
            }

            long receivedTimestamp = System.currentTimeMillis();

            String clientIp = "unknown";
            if (exchange.getRequest().getRemoteAddress() != null && exchange.getRequest().getRemoteAddress().getAddress() != null) {
                clientIp = exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
            }

            SourceInfo sourceInfo = new SourceInfo(
                    Protocol.HTTP,
                    clientIp, // Client IP address
                    exchange.getRequest().getURI().getPath() // Full path like /ingest/someSource
            );

            Map<String, String> metadata = headers.toSingleValueMap();

            // Determine PayloadType from Content-Type header
            String contentType = headers.getFirst(HttpHeaders.CONTENT_TYPE);
            PayloadType payloadType = determinePayloadType(contentType);
            String originalEncoding = determineOriginalEncoding(contentType);

            UnifiedInternalRequest uir = new UnifiedInternalRequest(
                    requestId,
                    requestBodyBytes, // Payload as byte array
                    payloadType,
                    sourceInfo,
                    metadata,
                    receivedTimestamp,
                    originalEncoding
            );

            // TODO: Later, pass the UIR to the GatewayPipelineOrchestrator
            // For now, just log its creation for verification
            logger.info("Received HTTP request, created UnifiedInternalRequest: {}", uir.getRequestId());
            logger.debug("UIR Details: {}", uir);


            // In a full implementation, the response would come from the pipeline.
            // return pipelineOrchestrator.processRequest(Mono.just(uir))
            //        .then(Mono.just(ResponseEntity.accepted().body("Request " + uir.getRequestId() + " received")));

            // Placeholder response for now:
            String responseBody = "Request " + uir.getRequestId() + " received by HTTP Adapter. Source: " + sourceIdentifier;
            return Mono.just(ResponseEntity.status(HttpStatus.ACCEPTED).body(responseBody));

        }).onErrorResume(e -> {
            logger.error("Error processing HTTP request: {}", e.getMessage(), e);
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body("Error processing request: " + e.getMessage()));
        });
    }

    private PayloadType determinePayloadType(String contentTypeHeader) {
        if (contentTypeHeader == null) {
            return PayloadType.UNKNOWN;
        }
        if (contentTypeHeader.toLowerCase().contains("application/json")) {
            return PayloadType.JSON;
        } else if (contentTypeHeader.toLowerCase().contains("application/x-protobuf") ||
                   contentTypeHeader.toLowerCase().contains("application/octet-stream+protobuf")) { // Common for protobuf
            return PayloadType.PROTOBUF;
        } else if (contentTypeHeader.toLowerCase().contains("text/csv")) {
            return PayloadType.CSV;
        } else if (contentTypeHeader.toLowerCase().contains("application/xml")) {
            return PayloadType.XML;
        } else if (contentTypeHeader.toLowerCase().startsWith("text/")) {
            return PayloadType.TEXT;
        } else {
            return PayloadType.BINARY; // Default for other octet-streams or unknown
        }
    }

    private String determineOriginalEncoding(String contentTypeHeader) {
        if (contentTypeHeader != null) {
            String[] parts = contentTypeHeader.split(";");
            for (String part : parts) {
                part = part.trim();
                if (part.toLowerCase().startsWith("charset=")) {
                    return part.substring("charset=".length()).toUpperCase();
                }
            }
        }
        // Default if not specified, common for text-based content types if not explicitly set.
        // For binary, this might be null or less relevant.
        if (contentTypeHeader != null && contentTypeHeader.toLowerCase().startsWith("text/")) {
             return "UTF-8";
        }
        return null;
    }

    // Health check endpoint (optional, but good practice)
    @GetMapping("/health")
    public Mono<ResponseEntity<String>> healthCheck() {
        return Mono.just(ResponseEntity.ok("HTTP Adapter is healthy"));
    }
}
