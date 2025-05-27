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

// --- Added imports ---
import com.example.gateway.core.model.RouteDestination; // Ensure this is the one being used
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.beans.factory.annotation.Autowired; // Add this
import org.springframework.core.io.ResourceLoader; // To load external rule files
import org.springframework.core.io.Resource; // To load external rule files
import java.io.InputStream; // To read external rule files
// --- End added imports ---

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

    // Default route if no other rules match - will be updated from properties
    private RouteDestination defaultRouteDestination = new RouteDestination(
            DestinationType.KAFKA_TOPIC, "internal-default-unrouted-dlq", Map.of("reason", "no_matching_route_rule_or_config")
    );
    
    // --- Added fields ---
    private final RoutingProperties routingProperties; // Inject config properties
    private final ResourceLoader resourceLoader; // For loading external files
    private final ObjectMapper yamlObjectMapper = new ObjectMapper(new YAMLFactory()); // For parsing YAML rule files
    // --- End added fields ---

    @Autowired // Add Autowired constructor
    public RoutingService(RoutingProperties routingProperties, ResourceLoader resourceLoader) {
        this.routingProperties = routingProperties;
        this.resourceLoader = resourceLoader;
        // Default route destination will now be set from properties in initializeRulesFromConfig
    }

    // @Override // If RoutingService implements an interface, or just for clarity
    @PostConstruct
    public void initializeRulesFromConfig() { // Renamed from initializeDefaultRules
        this.rules.clear(); // Clear any previously hardcoded rules

        // Set default route from properties
        RoutingProperties.DefaultDestinationProperties defaultDestProps = routingProperties.getDefaultDestination();
        if (defaultDestProps != null && defaultDestProps.getTarget() != null && defaultDestProps.getType() != null) {
            this.defaultRouteDestination = new RouteDestination(
                    defaultDestProps.getType(),
                    defaultDestProps.getTarget(),
                    defaultDestProps.getProperties()
            );
            logger.info("Default route destination configured from properties: {}", this.defaultRouteDestination);
        } else {
            logger.warn("No default route destination configured in properties (gateway.routing.default-destination). Using internal default: {}", this.defaultRouteDestination);
            // Keep the existing internal default if not specified in properties
        }

        // Load rules embedded in application.yaml (via routingProperties.getRules())
        if (routingProperties.getRules() != null && !routingProperties.getRules().isEmpty()) {
            logger.info("Loading routing rules embedded in application configuration (gateway.routing.rules)...");
            for (RoutingProperties.RuleConfig ruleConfig : routingProperties.getRules()) {
                if (ruleConfig.getId() == null || ruleConfig.getConditionSpel() == null || ruleConfig.getDestination() == null || ruleConfig.getPriority() == null) {
                    logger.error("Skipping invalid embedded rule due to missing fields: {}", ruleConfig);
                    continue;
                }
                RouteDestination destination = new RouteDestination(
                        ruleConfig.getDestination().getType(),
                        ruleConfig.getDestination().getTarget(),
                        ruleConfig.getDestination().getProperties()
                );
                try {
                    addSpelRule(ruleConfig.getId(), ruleConfig.getDescription(), ruleConfig.getPriority(),
                                ruleConfig.getConditionSpel(), destination, ruleConfig.isTargetIsSpel());
                } catch (Exception e) {
                    logger.error("Error creating rule from embedded config (ID: {}): {}", ruleConfig.getId(), e.getMessage(), e);
                }
            }
        }

        // Load rules from external file path if specified
        if (routingProperties.getRulesConfigPath() != null && !routingProperties.getRulesConfigPath().isEmpty()) {
            logger.info("Loading routing rules from external configuration file: {}", routingProperties.getRulesConfigPath());
            try {
                Resource ruleResource = resourceLoader.getResource(routingProperties.getRulesConfigPath());
                if (ruleResource.exists()) {
                    try (InputStream inputStream = ruleResource.getInputStream()) {
                        // Expecting the YAML to be a map with a top-level "rules" key,
                        Map<String, List<RoutingProperties.RuleConfig>> externalConfig = yamlObjectMapper.readValue(inputStream, new TypeReference<>() {});
                        List<RoutingProperties.RuleConfig> externalRules = externalConfig.get("rules");

                        if (externalRules != null && !externalRules.isEmpty()) {
                            for (RoutingProperties.RuleConfig ruleConfig : externalRules) {
                                if (ruleConfig.getId() == null || ruleConfig.getConditionSpel() == null || ruleConfig.getDestination() == null || ruleConfig.getPriority() == null) {
                                    logger.error("Skipping invalid rule from external file {} due to missing fields: {}", routingProperties.getRulesConfigPath(), ruleConfig);
                                    continue;
                                }
                                RouteDestination destination = new RouteDestination(
                                        ruleConfig.getDestination().getType(),
                                        ruleConfig.getDestination().getTarget(),
                                        ruleConfig.getDestination().getProperties()
                                );
                                addSpelRule(ruleConfig.getId(), ruleConfig.getDescription(), ruleConfig.getPriority(),
                                            ruleConfig.getConditionSpel(), destination, ruleConfig.isTargetIsSpel());
                            }
                        } else {
                             logger.warn("External rules file {} was found, but contained no rules under 'rules:' key or was empty.", routingProperties.getRulesConfigPath());
                        }
                    }
                } else {
                    logger.warn("Routing rules configuration file not found at path: {}", routingProperties.getRulesConfigPath());
                }
            } catch (Exception e) {
                logger.error("Error loading or parsing routing rules from {}: {}", routingProperties.getRulesConfigPath(), e.getMessage(), e);
            }
        }

        if (this.rules.isEmpty()) {
            logger.warn("No routing rules loaded from configuration. Routing service may rely entirely on default route.");
        } else {
            // Log count only, full list might be too verbose for startup
            logger.info("Total routing rules loaded and configured: {}", this.rules.size());
            if (logger.isDebugEnabled()) {
                 logger.debug("Loaded rules (sorted by priority): {}", this.rules);
            }
        }
    }
    
    // addRule is primarily for programmatic addition, config loading uses addSpelRule directly
    public void addRule(RoutingRule rule) { 
        // Remove existing rule with same ID if present, to allow updates
        rules.removeIf(r -> r.getId().equals(rule.getId()));
        rules.add(rule);
        rules.sort(Comparator.comparingInt(RoutingRule::getPriority)); // Sort by priority
        logger.info("Programmatically added/updated routing rule: {}", rule); // Clarify source
    }

    public void addSpelRule(String id, String description, int priority, String conditionSpel, RouteDestination destination, boolean targetIsSpel) {
        // Check for duplicate rule ID before parsing/adding
        if (rules.stream().anyMatch(r -> r.getId().equals(id))) {
            logger.warn("Attempted to add rule with duplicate ID '{}'. Skipping.", id);
            return;
        }
        Expression compiledCondition = expressionParser.parseExpression(conditionSpel);
        Predicate<Object> conditionPredicate = event -> {
            try {
                EvaluationContext context = new StandardEvaluationContext(event);
                Boolean result = compiledCondition.getValue(context, Boolean.class);
                return Boolean.TRUE.equals(result);
            } catch (Exception e) {
                logger.error("Error evaluating SpEL condition for rule ID '{}': {}", id, e.getMessage());
                // To prevent logs flooding on broken SpEL, consider more advanced error handling here
                // e.g., disabling the rule after N failures.
                return false;
            }
        };

        RouteDestination finalDestination = destination;
        if (targetIsSpel) {
            // Ensure properties map exists if we need to add _targetIsSpel
            Map<String, String> props = destination.getProperties() == null ? new HashMap<>() : new HashMap<>(destination.getProperties());
            props.put("_targetIsSpel", "true"); // Internal flag for evaluation
            finalDestination = new RouteDestination(destination.getType(), destination.getTarget(), props);
        }
        
        // Using the internal addRule to manage the list and sorting
        RoutingRule newRule = new RoutingRule(id, description, priority, conditionPredicate, finalDestination);
        rules.add(newRule);
        rules.sort(Comparator.comparingInt(RoutingRule::getPriority)); // Sort by priority
        logger.info("Loaded rule from config: {}", newRule);
    }


    public Optional<RoutedEvent> evaluateRoutes(TransformedDataEvent event) {
        if (event == null) {
            logger.warn("TransformedDataEvent is null, cannot evaluate routes.");
            return Optional.empty();
        }

        logger.debug("Evaluating routing rules for request ID: {}", event.getRequestId());

        for (RoutingRule rule : rules) {
            try {
                if (rule.getCondition().test(event)) { // This now calls the SpEL predicate
                    logger.info("Rule '{}' matched for request ID: {}. Original Destination Target: {}", rule.getId(), event.getRequestId(), rule.getDestination().getTarget());

                    RouteDestination actualDestination = rule.getDestination();
                    // Check internal flag if target needs SpEL evaluation
                    if (actualDestination.getProperties() != null && "true".equals(actualDestination.getProperties().get("_targetIsSpel"))) {
                        try {
                            EvaluationContext context = new StandardEvaluationContext(event);
                            String resolvedTarget = expressionParser.parseExpression(actualDestination.getTarget()).getValue(context, String.class);
                            if (resolvedTarget == null || resolvedTarget.trim().isEmpty()) {
                                 logger.error("SpEL expression for rule '{}' target resolved to null or empty. Skipping rule.", rule.getId());
                                 continue; // Try next rule
                            }
                             // Create new RouteDestination with resolved target, but keep original type and other properties
                            Map<String, String> originalProps = new HashMap<>(actualDestination.getProperties());
                            originalProps.remove("_targetIsSpel"); // Clean up internal flag
                            // Add matched rule ID to properties
                            originalProps.put("_matchedRuleId", rule.getId());
                            actualDestination = new RouteDestination(actualDestination.getType(), resolvedTarget, originalProps.isEmpty() ? null : originalProps);
                            logger.debug("Rule '{}' target resolved via SpEL to: {}", rule.getId(), resolvedTarget);
                        } catch (Exception e) {
                            logger.error("Error evaluating SpEL target expression for rule '{}': {}. Skipping rule.", rule.getId(), e.getMessage(), e);
                            continue; // Try next rule
                        }
                    }

                    // If not a SpEL target, ensure properties are initialized for adding _matchedRuleId
                    if (!(actualDestination.getProperties() != null && actualDestination.getProperties().containsKey("_targetIsSpel"))) {
                        Map<String, String> newProps = actualDestination.getProperties() == null ? new HashMap<>() : new HashMap<>(actualDestination.getProperties());
                        newProps.put("_matchedRuleId", rule.getId());
                        actualDestination.setProperties(newProps);
                    }


                    RoutedEvent routedEvent = new RoutedEvent(
                            event.getRequestId(),
                            event.getSourceInfo(),
                            event.getTransformedPayload(),
                            event.getPayloadType(),
                            actualDestination,
                            event.getProcessingMetadata() // Carry over metadata
                    );
                    return Optional.of(routedEvent);
                }
            } catch (Exception e) { // Catch errors from rule.getCondition().test(event) or other issues
                logger.error("Error processing rule '{}' for request ID: {}. Error: {}", rule.getId(), event.getRequestId(), e.getMessage(), e);
                // Depending on policy, could continue to next rule or fail fast.
            }
        }

        logger.warn("No specific routing rule matched for request ID: {}. Using default route: {}", event.getRequestId(), defaultRouteDestination.getTarget());
        // Ensure defaultRouteDestination is never null
        if (defaultRouteDestination == null) {
            logger.error("CRITICAL: Default route destination is null for request ID {}. This should not happen.", event.getRequestId());
            throw new RoutingException("Default route destination is not configured.");
        }
        RoutedEvent defaultRoutedEvent = new RoutedEvent(
                event.getRequestId(),
                event.getSourceInfo(),
                event.getTransformedPayload(),
                event.getPayloadType(),
                defaultRouteDestination, // Use the (potentially configured) default
                Map.of("routing_info", "default_route_applied") // Add some metadata
        );
        return Optional.of(defaultRoutedEvent);
    }

    // setDefaultRouteDestination is mostly for testing or dynamic updates if needed,
    // primary config is via RoutingProperties
    public void setDefaultRouteDestination(RouteDestination defaultRouteDestination) {
        if (defaultRouteDestination == null) {
            logger.warn("Attempted to set null default route destination. Ignoring.");
            return;
        }
        this.defaultRouteDestination = defaultRouteDestination;
        logger.info("Default route destination has been programmatically updated to: {}", defaultRouteDestination);
    }

    public List<RoutingRule> getRules() { // For admin/monitoring purposes
        return new ArrayList<>(rules); // Return a copy
    }
}
