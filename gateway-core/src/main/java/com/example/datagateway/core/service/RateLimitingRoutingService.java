package com.example.datagateway.core.service;

import com.example.datagateway.core.model.RoutedEvent;
import com.example.datagateway.core.model.TransformedDataEvent;
import reactor.core.publisher.Mono;

/**
 * Defines the contract for the Intelligent Rate Limiting and Routing Layer.
 * This service is responsible for applying rate limiting policies and then
 * routing the event to its next destination based on configured rules.
 */
public interface RateLimitingRoutingService {
    /**
     * Applies rate limiting checks to the transformed data event and, if permitted,
     * determines the routing destination(s).
     *
     * @param event The {@link TransformedDataEvent} containing the data that has been
     *              processed by the transformation engine.
     * @return A {@link Mono} emitting a {@link RoutedEvent} which specifies the chosen
     *         destination and payload if the event is permitted by rate limiting and a route is found.
     *         If rate limiting is exceeded, the Mono should signal an error (e.g., a specific
     *         RateLimitExceededException). If no route is found and no default route is configured
     *         to handle such cases, it might also signal an error or emit an empty Mono,
     *         depending on defined behavior.
     */
    Mono<RoutedEvent> checkAndRoute(TransformedDataEvent event);
}
