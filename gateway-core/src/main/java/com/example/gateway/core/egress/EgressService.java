package com.example.gateway.core.egress;

import com.example.gateway.core.model.*; // DestinationType, EgressProcessingResult, EgressStatus, RoutedEvent
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.timeout.TimeoutException; // Common timeout exception
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender; // Reactor Kafka Sender
import reactor.kafka.sender.SenderRecord;
import reactor.kafka.sender.SenderResult;


import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class EgressService {

    private static final Logger logger = LoggerFactory.getLogger(EgressService.class);

    private final WebClient webClient; // General purpose WebClient
    private final KafkaTemplate<String, byte[]> kafkaTemplate; // Spring Kafka Template
    private final KafkaSender<String, byte[]> reactorKafkaSender; // Project Reactor Kafka Sender
    private final ObjectMapper objectMapper; // For serializing payloads to JSON if needed


    // TODO: Inject specific WebClient beans or KafkaTemplate beans if multiple configurations are needed.
    @Autowired
    public EgressService(WebClient.Builder webClientBuilder,
                         KafkaTemplate<String, byte[]> kafkaTemplate,
                         KafkaSender<String, byte[]> reactorKafkaSender,
                         ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.build(); // Basic WebClient
        this.kafkaTemplate = kafkaTemplate;
        this.reactorKafkaSender = reactorKafkaSender;
        this.objectMapper = objectMapper;
    }

    public Mono<EgressProcessingResult> dispatch(RoutedEvent event) {
        if (event == null || event.getDestination() == null) {
            logger.warn("Cannot dispatch null event or event with null destination.");
            return Mono.just(new EgressProcessingResult(
                    event != null ? event.getRequestId() : "unknown",
                    null, EgressStatus.FAILURE, "Null event or destination", 1, null));
        }

        RouteDestination destination = event.getDestination();
        logger.info("Dispatching request ID: {} to destination type: {}, target: {}",
                event.getRequestId(), destination.getType(), destination.getTarget());

        switch (destination.getType()) {
            case KAFKA_TOPIC:
                return dispatchToKafkaReactive(event);
            case HTTP_ENDPOINT:
                return dispatchToHttp(event);
            // case GRPC_SERVICE: // TODO: Implement gRPC egress
            //    return dispatchToGrpc(event);
            case DISCARD:
                logger.info("Event {} discarded as per routing rule.", event.getRequestId());
                return Mono.just(new EgressProcessingResult(event.getRequestId(), destination, EgressStatus.SUCCESS, "Discarded", 1, null));
            default:
                logger.warn("Unsupported destination type: {} for request ID: {}", destination.getType(), event.getRequestId());
                return Mono.just(new EgressProcessingResult(event.getRequestId(), destination, EgressStatus.FAILURE, "Unsupported destination type", 1, null));
        }
    }

    private Mono<EgressProcessingResult> dispatchToKafka(RoutedEvent event) {
        String topic = event.getDestination().getTarget();
        byte[] payloadBytes = convertPayloadToBytes(event.getPayloadToRoute(), event.getPayloadType());
        if (payloadBytes == null) {
             return Mono.just(new EgressProcessingResult(event.getRequestId(), event.getDestination(), EgressStatus.FAILURE, "Payload conversion to bytes failed", 1, null));
        }

        CompletableFuture<SendResult<String, byte[]>> future = kafkaTemplate.send(topic, payloadBytes);

        return Mono.fromFuture(future)
                .map(sendResult -> {
                    logger.info("Successfully sent message to Kafka topic {} for request ID: {}", topic, event.getRequestId());
                    return new EgressProcessingResult(event.getRequestId(), event.getDestination(), EgressStatus.SUCCESS, null, 1, null);
                })
                .onErrorResume(ex -> {
                    logger.error("Failed to send message to Kafka topic {} for request ID: {}. Error: {}", topic, event.getRequestId(), ex.getMessage(), ex);
                    // Determine if retryable based on exception type if needed
                    return Mono.just(new EgressProcessingResult(event.getRequestId(), event.getDestination(), EgressStatus.FAILURE, ex.getMessage(), 1, null));
                });
    }

    private Mono<EgressProcessingResult> dispatchToKafkaReactive(RoutedEvent event) {
        String topic = event.getDestination().getTarget();
        byte[] payloadBytes = convertPayloadToBytes(event.getPayloadToRoute(), event.getPayloadType());
         if (payloadBytes == null) {
             return Mono.just(new EgressProcessingResult(event.getRequestId(), event.getDestination(), EgressStatus.FAILURE, "Payload conversion to bytes failed", 1, null));
        }

        // Assuming key is not critical for now, or could be derived from event.getRequestId() or metadata
        SenderRecord<String, byte[], String> record = SenderRecord.create(topic, null, null, event.getRequestId(), payloadBytes, event.getRequestId());

        return reactorKafkaSender.send(Mono.just(record))
            .single() // We are sending a single record flux, expect a single result
            .map(senderResult -> {
                if (senderResult.exception() != null) {
                    logger.error("Failed to send message to Kafka topic {} for request ID: {}. Error: {}", topic, event.getRequestId(), senderResult.exception().getMessage(), senderResult.exception());
                    return new EgressProcessingResult(event.getRequestId(), event.getDestination(), EgressStatus.FAILURE, senderResult.exception().getMessage(), 1, null);
                } else {
                    logger.info("Successfully sent message to Kafka topic {} for request ID: {} using reactive sender.", topic, event.getRequestId());
                    return new EgressProcessingResult(event.getRequestId(), event.getDestination(), EgressStatus.SUCCESS, null, 1, null);
                }
            })
            .onErrorResume(ex -> {
                logger.error("Error with reactive Kafka sender for topic {} request ID: {}. Error: {}", topic, event.getRequestId(), ex.getMessage(), ex);
                return Mono.just(new EgressProcessingResult(event.getRequestId(), event.getDestination(), EgressStatus.FAILURE, ex.getMessage(), 1, null));
            });
    }


    private Mono<EgressProcessingResult> dispatchToHttp(RoutedEvent event) {
        String url = event.getDestination().getTarget();
        RouteDestination destination = event.getDestination();
        Map<String, String> props = destination.getProperties() != null ? destination.getProperties() : Map.of();
        String httpMethod = props.getOrDefault("method", "POST").toUpperCase(); // Default to POST

        byte[] payloadBytes = convertPayloadToBytes(event.getPayloadToRoute(), event.getPayloadType());
        if (payloadBytes == null && ("POST".equals(httpMethod) || "PUT".equals(httpMethod) || "PATCH".equals(httpMethod))) {
             return Mono.just(new EgressProcessingResult(event.getRequestId(), destination, EgressStatus.FAILURE, "Payload conversion to bytes failed for HTTP body", 1, null));
        }

        WebClient.RequestBodySpec requestSpec = webClient
                .method(org.springframework.http.HttpMethod.valueOf(httpMethod))
                .uri(url);

        WebClient.RequestHeadersSpec<?> headersSpec = requestSpec;
        if (payloadBytes != null && ("POST".equals(httpMethod) || "PUT".equals(httpMethod) || "PATCH".equals(httpMethod))) {
             headersSpec = requestSpec.bodyValue(payloadBytes);
        }

        // TODO: Add headers from event.getRoutingMetadata() or destination.getProperties()
        // For example:
        // if (event.getRoutingMetadata() != null) {
        //    event.getRoutingMetadata().forEach(headersSpec::header);
        // }


        return ((WebClient.RequestHeadersSpec<?>) headersSpec)
                .exchangeToMono(clientResponse -> { // Use exchangeToMono for full response access
                    EgressStatus status = clientResponse.statusCode().isError() ? EgressStatus.FAILURE : EgressStatus.SUCCESS;
                    String failureReason = clientResponse.statusCode().isError() ? "HTTP Error: " + clientResponse.statusCode().value() : null;
                    
                    // Optionally, consume the body to prevent memory leaks, even if not used.
                    return clientResponse.bodyToMono(String.class).defaultIfEmpty("").map(body -> {
                         Map<String, String> responseMeta = new HashMap<>();
                         responseMeta.put("httpStatusCode", String.valueOf(clientResponse.statusCode().value()));
                         responseMeta.put("httpResponseHeaders", clientResponse.headers().asHttpHeaders().toString());
                         // responseMeta.put("httpResponseBodySample", body.substring(0, Math.min(body.length(), 256))); // Sample of body

                         logger.info("HTTP Egress to {} for request {} completed with status {}.", url, event.getRequestId(), clientResponse.statusCode());
                         return new EgressProcessingResult(event.getRequestId(), destination, status, failureReason, 1, responseMeta);
                    });
                })
                .timeout(Duration.ofSeconds(10)) // Example timeout
                .onErrorResume(ex -> {
                    logger.error("Failed to send HTTP request to {} for request ID: {}. Error: {}", url, event.getRequestId(), ex.getMessage(), ex);
                    String reason = ex.getMessage();
                    if (ex instanceof TimeoutException) {
                        reason = "Timeout during HTTP request";
                    }
                    return Mono.just(new EgressProcessingResult(event.getRequestId(), destination, EgressStatus.RETRYABLE_FAILURE, reason, 1, null));
                });
    }

    private byte[] convertPayloadToBytes(Object payload, PayloadType payloadType) {
        if (payload == null) return null;
        if (payload instanceof byte[]) {
            return (byte[]) payload;
        }
        if (payload instanceof String) {
            return ((String) payload).getBytes(StandardCharsets.UTF_8);
        }
        // If payload is a Map (e.g. parsed JSON) or other object, serialize to JSON bytes
        // This is a common fallback for egress if the destination expects a JSON string.
        try {
            logger.debug("Serializing egress payload of type {} to JSON byte array.", payload.getClass().getSimpleName());
            return objectMapper.writeValueAsBytes(payload);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize egress payload to JSON bytes: {}", e.getMessage());
            return null;
        }
    }
}
