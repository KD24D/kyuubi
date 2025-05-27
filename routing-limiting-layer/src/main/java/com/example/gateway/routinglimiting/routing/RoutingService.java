package com.example.gateway.routinglimiting.routing;

import com.example.gateway.core.model.*; // Import all core models
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

@Service
public class RoutingService {

    private static final Logger logger = LoggerFactory.getLogger(RoutingService.class);
    private final List<RoutingRule> rules = new CopyOnWriteArrayList<>(); // Thread-safe for dynamic updates
    private final ExpressionParser expressionParser = new SpelExpressionParser();

    // Default route if no other rules match
    private RouteDestination defaultRouteDestination = new RouteDestination(
            DestinationType.KAFKA_TOPIC, "unrouted-events-dlq", Map.of("reason", "no_matching_route_rule")
    );

    public RoutingService() {
        // Initialization of rules will be done in @PostConstruct or via config later
    }

    @PostConstruct
    public void initializeDefaultRules() {
        // TODO: Externalize rule configuration (Step 9)
        // For now, adding a few sample rules directly.
        // These examples assume TransformedDataEvent.transformedPayload is a Map.

        // Example Rule 1: Route based on a field in the payload
        // Condition: transformedPayload.category == 'orders' && transformedPayload.priority == 'HIGH'
        // Using Predicate for now, SpEL integration will be more robust.
        Predicate<Object> highPriorityOrderCondition = event -> {
            if (event instanceof TransformedDataEvent) {
                TransformedDataEvent tde = (TransformedDataEvent) event;
                if (tde.getTransformedPayload() instanceof Map) {
                    Map<?,?> mapPayload = (Map<?,?>) tde.getTransformedPayload();
                    return "orders".equals(mapPayload.get("category")) && "HIGH".equals(mapPayload.get("priority"));
                }
            }
            return false;
        };
        RouteDestination highPriorityDest = new RouteDestination(DestinationType.KAFKA_TOPIC, "high-priority-orders-topic", null);
        addRule(new RoutingRule("rule_high_prio_orders", "High Priority Orders", 10, highPriorityOrderCondition, highPriorityDest));

        // Example Rule 2: Route based on source information
        // Condition: sourceInfo.requestTarget contains 'partnerA'
         RoutingRule partnerARule = new RoutingRule("rule_partner_a", "Partner A Data", 20,
            event -> {
                if (event instanceof TransformedDataEvent) {
                    return ((TransformedDataEvent) event).getSourceInfo().getRequestTarget().contains("partnerA");
                }
                return false;
            },
            new RouteDestination(DestinationType.HTTP_ENDPOINT, "http://partnerA-processor/ingest", Map.of("method", "POST")));
        addRule(partnerARule);

        // Example Rule 3: SpEL based rule (more flexible)
        // Condition: "transformedPayload.type == 'user_log' && transformedPayload.user.region != null"
        // Target: "'user-logs-' + transformedPayload.user.region.toLowerCase() + '-topic'" (Dynamic target)
        try {
            String spelCondition = "transformedPayload['type'] == 'user_log' && transformedPayload['user']?.['region'] != null";
            String spelTargetExpression = "'user-logs-' + transformedPayload['user']['region'].toLowerCase() + '-topic'";
            RouteDestination userLogDest = new RouteDestination(DestinationType.KAFKA_TOPIC, spelTargetExpression, null);
            addSpelRule("rule_user_logs_region", "User Logs by Region", 30, spelCondition, userLogDest, true);
        } catch (Exception e) {
            logger.error("Failed to create sample SpEL rule during initialization.", e);
        }


        logger.info("Initialized default routing rules. Count: {}", rules.size());
    }

    public void addRule(RoutingRule rule) {
        // Remove existing rule with same ID if present, to allow updates
        rules.removeIf(r -> r.getId().equals(rule.getId()));
        rules.add(rule);
        rules.sort(Comparator.comparingInt(RoutingRule::getPriority)); // Sort by priority
        logger.info("Added/Updated routing rule: {}", rule);
    }

    public void addSpelRule(String id, String description, int priority, String conditionSpel, RouteDestination destination, boolean targetIsSpel) {
        Expression compiledCondition = expressionParser.parseExpression(conditionSpel);
        Predicate<Object> conditionPredicate = event -> {
            try {
                EvaluationContext context = new StandardEvaluationContext(event);
                Boolean result = compiledCondition.getValue(context, Boolean.class);
                return Boolean.TRUE.equals(result);
            } catch (Exception e) {
                logger.error("Error evaluating SpEL condition for rule '{}': {}", id, e.getMessage());
                return false;
            }
        };

        RouteDestination finalDestination = destination;
        if (targetIsSpel) {
            Map<String, String> props = new HashMap<>(destination.getProperties() == null ? Map.of() : destination.getProperties());
            props.put("_targetIsSpel", "true");
            finalDestination = new RouteDestination(destination.getType(), destination.getTarget(), props);
        }

        addRule(new RoutingRule(id, description, priority, conditionPredicate, finalDestination));
    }


    public Optional<RoutedEvent> evaluateRoutes(TransformedDataEvent event) {
        if (event == null) {
            logger.warn("TransformedDataEvent is null, cannot evaluate routes.");
            return Optional.empty();
        }

        logger.debug("Evaluating routing rules for request ID: {}", event.getRequestId());

        for (RoutingRule rule : rules) {
            try {
                if (rule.getCondition().test(event)) {
                    logger.info("Rule '{}' matched for request ID: {}. Destination: {}", rule.getId(), event.getRequestId(), rule.getDestination().getTarget());

                    RouteDestination actualDestination = rule.getDestination();
                    if (actualDestination.getProperties() != null && "true".equals(actualDestination.getProperties().get("_targetIsSpel"))) {
                        try {
                            EvaluationContext context = new StandardEvaluationContext(event);
                            String resolvedTarget = expressionParser.parseExpression(actualDestination.getTarget()).getValue(context, String.class);
                            if (resolvedTarget == null || resolvedTarget.trim().isEmpty()) {
                                 logger.error("SpEL expression for rule '{}' target resolved to null or empty. Skipping rule.", rule.getId());
                                 continue;
                            }
                            actualDestination = new RouteDestination(actualDestination.getType(), resolvedTarget, actualDestination.getProperties());
                        } catch (Exception e) {
                            logger.error("Error evaluating SpEL target expression for rule '{}': {}. Skipping rule.", rule.getId(), e.getMessage());
                            continue;
                        }
                    }

                    RoutedEvent routedEvent = new RoutedEvent(
                            event.getRequestId(),
                            event.getSourceInfo(),
                            event.getTransformedPayload(),
                            event.getPayloadType(),
                            actualDestination,
                            event.getProcessingMetadata()
                    );
                    return Optional.of(routedEvent);
                }
            } catch (Exception e) {
                logger.error("Error processing rule '{}' for request ID: {}. Error: {}", rule.getId(), event.getRequestId(), e.getMessage(), e);
            }
        }

        logger.warn("No specific routing rule matched for request ID: {}. Using default route: {}", event.getRequestId(), defaultRouteDestination.getTarget());
        RoutedEvent defaultRoutedEvent = new RoutedEvent(
                event.getRequestId(),
                event.getSourceInfo(),
                event.getTransformedPayload(),
                event.getPayloadType(),
                defaultRouteDestination,
                Map.of("routing_info", "default_route_applied")
        );
        return Optional.of(defaultRoutedEvent);
    }

    public void setDefaultRouteDestination(RouteDestination defaultRouteDestination) {
        this.defaultRouteDestination = defaultRouteDestination;
        logger.info("Default route destination updated to: {}", defaultRouteDestination);
    }

    public List<RoutingRule> getRules() {
        return new ArrayList<>(rules); // Return a copy
    }
}
