package com.example.datagateway.protocol.http;

import com.example.datagateway.core.model.PayloadType;
import com.example.datagateway.core.model.Protocol;
import com.example.datagateway.core.model.SourceInfo;
import com.example.datagateway.core.model.UnifiedInternalRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Spring WebFlux Controller for handling incoming HTTP data ingestion requests.
 */
@RestController
@Slf4j
public class HttpIngestionController {

    private final String ingestionPath;

    /**
     * Constructs the controller with a configured ingestion path.
     * @param ingestionPath The base path for HTTP ingestion, configured via application properties.
     */
    public HttpIngestionController(@Value("${gateway.protocol-adapters.http.ingestion-path:/ingest}") String ingestionPath) {
        this.ingestionPath = ingestionPath;
    }

    /**
     * Handles HTTP POST requests for data ingestion.
     *
     * @param sourceIdentifier Path variable identifying the source.
     * @param httpHeaders      Incoming HTTP headers.
     * @param requestBodyMono  The request body as a Mono of byte array.
     * @param request          The {@link ServerHttpRequest} object for accessing request details.
     * @return A {@link Mono} emitting a {@link ResponseEntity} indicating acceptance or error.
     */
    @PostMapping("${gateway.protocol-adapters.http.ingestion-path:/ingest}/{sourceIdentifier}")
    public Mono<ResponseEntity<String>> handlePostRequest(
            @PathVariable String sourceIdentifier,
            @RequestHeader HttpHeaders httpHeaders,
            @RequestBody Mono<byte[]> requestBodyMono,
            ServerHttpRequest request) {

        long receivedTimestamp = Instant.now().toEpochMilli();

        return requestBodyMono.flatMap(bodyBytes -> {
            // 1. Generate/Extract Request ID
            String requestId = httpHeaders.getFirst("X-Request-ID");
            if (requestId == null || requestId.isEmpty()) {
                requestId = UUID.randomUUID().toString();
            }

            // 2. Determine PayloadType from Content-Type header
            PayloadType payloadType = determinePayloadType(httpHeaders.getContentType());

            // 3. Extract client IP
            String clientIp = Objects.requireNonNullElse(request.getRemoteAddress(), "unknown_address").toString();
            if (clientIp.startsWith("/")) {
                clientIp = clientIp.substring(1);
            }
            clientIp = clientIp.split(":")[0]; // Get only the IP part, not port

            // 4. Populate SourceInfo
            SourceInfo sourceInfo = SourceInfo.builder()
                    .protocol(Protocol.HTTP)
                    .sourceAddress(clientIp)
                    .requestTarget(request.getURI().getPath()) // Full path including sourceIdentifier
                    .build();

            // 5. Extract relevant HTTP headers for metadata
            Map<String, String> metadata = new HashMap<>();
            httpHeaders.forEach((key, values) -> {
                // Store first value for simplicity, or join/handle multiple values as needed
                if (!values.isEmpty()) {
                    metadata.put(key, values.get(0));
                }
            });
            // Add sourceIdentifier to metadata for potential use in later stages
            metadata.put("httpSourceIdentifier", sourceIdentifier);


            // 6. Create UnifiedInternalRequest
            UnifiedInternalRequest.UnifiedInternalRequestBuilder uirBuilder = UnifiedInternalRequest.builder()
                    .requestId(requestId)
                    .payload(bodyBytes) // Payload is the byte array
                    .payloadType(payloadType)
                    .sourceInfo(sourceInfo)
                    .metadata(metadata)
                    .receivedTimestamp(receivedTimestamp);

            if (httpHeaders.getContentType() != null && httpHeaders.getContentType().getCharset() != null) {
                uirBuilder.originalEncoding(httpHeaders.getContentType().getCharset().name());
            } else if (payloadType == PayloadType.JSON || payloadType == PayloadType.XML || payloadType == PayloadType.TEXT || payloadType == PayloadType.CSV){
                uirBuilder.originalEncoding("UTF-8"); // Default for text-based if not specified
            }

            UnifiedInternalRequest unifiedRequest = uirBuilder.build();

            // 7. Placeholder for Orchestrator Integration
            log.info("Created UnifiedInternalRequest: RequestId={}, Source={}, PayloadType={}, Size={}",
                    unifiedRequest.getRequestId(),
                    unifiedRequest.getSourceInfo().getRequestTarget(),
                    unifiedRequest.getPayloadType(),
                    bodyBytes.length);
            log.debug("UnifiedInternalRequest details: {}", unifiedRequest);


            // For now, return a placeholder success response
            // This will be replaced by a call to GatewayPipelineOrchestrator.processHttpRequest(Mono.just(unifiedRequest))
            return Mono.just(ResponseEntity.accepted().body("Request received by HTTP adapter, UIR created: " + unifiedRequest.getRequestId()));

        }).switchIfEmpty(Mono.defer(() -> { // Handle empty body case
            // Similar logic as above but with empty payload handling
            String requestId = httpHeaders.getFirst("X-Request-ID");
            if (requestId == null || requestId.isEmpty()) {
                requestId = UUID.randomUUID().toString();
            }
            PayloadType payloadType = determinePayloadType(httpHeaders.getContentType());
            String clientIp = Objects.requireNonNullElse(request.getRemoteAddress(), "unknown_address").toString();
             if (clientIp.startsWith("/")) {
                clientIp = clientIp.substring(1);
            }
            clientIp = clientIp.split(":")[0];


            SourceInfo sourceInfo = SourceInfo.builder()
                    .protocol(Protocol.HTTP)
                    .sourceAddress(clientIp)
                    .requestTarget(request.getURI().getPath())
                    .build();
            Map<String, String> metadata = new HashMap<>();
            httpHeaders.forEach((key, values) -> {
                if (!values.isEmpty()) {
                    metadata.put(key, values.get(0));
                }
            });
            metadata.put("httpSourceIdentifier", sourceIdentifier);

            UnifiedInternalRequest.UnifiedInternalRequestBuilder uirBuilder = UnifiedInternalRequest.builder()
                    .requestId(requestId)
                    .payload(new byte[0]) // Empty payload
                    .payloadType(payloadType)
                    .sourceInfo(sourceInfo)
                    .metadata(metadata)
                    .receivedTimestamp(receivedTimestamp);
            
            if (httpHeaders.getContentType() != null && httpHeaders.getContentType().getCharset() != null) {
                uirBuilder.originalEncoding(httpHeaders.getContentType().getCharset().name());
            }

            UnifiedInternalRequest unifiedRequest = uirBuilder.build();
            
            log.info("Created UnifiedInternalRequest for empty body: RequestId={}, Source={}, PayloadType={}",
                    unifiedRequest.getRequestId(),
                    unifiedRequest.getSourceInfo().getRequestTarget(),
                    unifiedRequest.getPayloadType());
            return Mono.just(ResponseEntity.accepted().body("Empty body request received by HTTP adapter, UIR created: " + unifiedRequest.getRequestId()));
        }));
    }

    private PayloadType determinePayloadType(MediaType contentType) {
        if (contentType == null) {
            return PayloadType.UNKNOWN;
        }
        if (MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
            return PayloadType.JSON;
        } else if (MediaType.APPLICATION_XML.isCompatibleWith(contentType) || MediaType.TEXT_XML.isCompatibleWith(contentType)) {
            return PayloadType.XML;
        } else if (MediaType.valueOf("application/x-protobuf").isCompatibleWith(contentType) ||
                   MediaType.valueOf("application/protobuf").isCompatibleWith(contentType) ||
                   MediaType.valueOf("application/octet-stream").isCompatibleWith(contentType) && "protobuf".equalsIgnoreCase(contentType.getSubtype())) { // A common way to send protobuf
            return PayloadType.PROTOBUF;
        } else if (MediaType.valueOf("text/csv").isCompatibleWith(contentType)) {
            return PayloadType.CSV;
        } else if (MediaType.TEXT_PLAIN.isCompatibleWith(contentType)) {
            return PayloadType.TEXT;
        } else if (MediaType.APPLICATION_OCTET_STREAM.isCompatibleWith(contentType)) {
            // Could be generic binary, or could be something specific we try to infer later or by configuration
            return PayloadType.BINARY;
        }
        // Add more mappings as needed, e.g., application/avro, application/parquet (though Parquet usually via other protocols)
        log.warn("Received unmapped Content-Type: {}", contentType);
        return PayloadType.UNKNOWN; // Default if no specific mapping found
    }
}
