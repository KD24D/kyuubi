package com.example.gateway.routinglimiting.routing;

import com.example.gateway.core.model.*; // All core models
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class) // For using @Mock annotation
public class RoutingServiceTest {

    private RoutingService routingService;

    @Mock
    private RoutingProperties routingProperties; // Mock RoutingProperties

    private ResourceLoader resourceLoader = new DefaultResourceLoader();

    @BeforeEach
    void setUp() {
        // Mock default destination behavior from properties
        RoutingProperties.DefaultDestinationProperties mockDefaultDestProps = new RoutingProperties.DefaultDestinationProperties();
        mockDefaultDestProps.setType(DestinationType.KAFKA_TOPIC);
        mockDefaultDestProps.setTarget("test-default-unrouted-topic");
        when(routingProperties.getDefaultDestination()).thenReturn(mockDefaultDestProps);
        when(routingProperties.getRules()).thenReturn(Collections.emptyList()); // Default to no embedded rules
        when(routingProperties.getRulesConfigPath()).thenReturn(null); // Default to no external rules file

        routingService = new RoutingService(routingProperties, resourceLoader); // Pass mocked properties
        routingService.initializeRulesFromConfig(); // Initialize with mocked properties
    }

    @Test
    void testSimpleSpelRuleEvaluation_Match() {
        // Setup a simple SpEL rule
        RouteDestination dest = new RouteDestination(DestinationType.KAFKA_TOPIC, "matched-topic", null);
        routingService.addSpelRule("rule1", "Test Rule 1", 10, "transformedPayload['value'] == 'match_me'", dest, false);

        TransformedDataEvent event = new TransformedDataEvent();
        event.setRequestId("test-req-1");
        Map<String, Object> payload = new HashMap<>();
        payload.put("value", "match_me");
        event.setTransformedPayload(payload);
        event.setPayloadType(PayloadType.JSON); // Assuming JSON map
        event.setSourceInfo(new SourceInfo(Protocol.HTTP, "client1", "/ingest"));

        Optional<RoutedEvent> routedEventOpt = routingService.evaluateRoutes(event);

        assertTrue(routedEventOpt.isPresent(), "Should have found a route");
        RoutedEvent routedEvent = routedEventOpt.get();
        assertEquals("matched-topic", routedEvent.getDestination().getTarget());
        assertEquals("rule1", routedEvent.getDestination().getProperties().get("_matchedRuleId")); // Assuming service adds this info
    }

    @Test
    void testSimpleSpelRuleEvaluation_NoMatch_UsesDefault() {
        RouteDestination dest = new RouteDestination(DestinationType.KAFKA_TOPIC, "matched-topic", null);
        routingService.addSpelRule("rule1", "Test Rule 1", 10, "transformedPayload['value'] == 'match_me'", dest, false);

        TransformedDataEvent event = new TransformedDataEvent();
        event.setRequestId("test-req-2");
        Map<String, Object> payload = new HashMap<>();
        payload.put("value", "no_match_here");
        event.setTransformedPayload(payload);
        event.setPayloadType(PayloadType.JSON);
        event.setSourceInfo(new SourceInfo(Protocol.HTTP, "client1", "/ingest"));

        Optional<RoutedEvent> routedEventOpt = routingService.evaluateRoutes(event);

        assertTrue(routedEventOpt.isPresent(), "Should have defaulted to a route");
        RoutedEvent routedEvent = routedEventOpt.get();
        assertEquals("test-default-unrouted-topic", routedEvent.getDestination().getTarget());
    }
    
    @Test
    void testDynamicTargetSpelRuleEvaluation_Match() {
        RouteDestination destTemplate = new RouteDestination(DestinationType.KAFKA_TOPIC, "'user-' + transformedPayload['userId'] + '-events'", null);
        routingService.addSpelRule("rule_dynamic", "Test Dynamic Target", 5, "transformedPayload['eventType'] == 'USER_LOGIN'", destTemplate, true);

        TransformedDataEvent event = new TransformedDataEvent();
        event.setRequestId("test-req-dynamic");
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", "USER_LOGIN");
        payload.put("userId", "user123");
        event.setTransformedPayload(payload);
        event.setPayloadType(PayloadType.JSON);
        event.setSourceInfo(new SourceInfo(Protocol.HTTP, "client1", "/ingest"));

        Optional<RoutedEvent> routedEventOpt = routingService.evaluateRoutes(event);

        assertTrue(routedEventOpt.isPresent(), "Should have found a dynamic route");
        RoutedEvent routedEvent = routedEventOpt.get();
        assertEquals("user-user123-events", routedEvent.getDestination().getTarget());
        assertEquals("rule_dynamic", routedEvent.getDestination().getProperties().get("_matchedRuleId"));
    }

    // Add more tests: rule priorities, multiple matching rules, SpEL errors, etc.
}
