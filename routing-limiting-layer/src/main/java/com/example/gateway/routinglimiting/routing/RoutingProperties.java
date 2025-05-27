package com.example.gateway.routinglimiting.routing;

import com.example.gateway.core.model.DestinationType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration; // Or @Component
import org.springframework.validation.annotation.Validated; // For validation annotations

import jakarta.validation.constraints.NotBlank; // Or javax.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "gateway.routing")
@Validated // Enable validation of these properties
public class RoutingProperties {

    private DefaultDestinationProperties defaultDestination = new DefaultDestinationProperties();
    private String rulesConfigPath; // Path to external rules file.
    private List<RuleConfig> rules = new ArrayList<>(); // For direct embedding of rules

    // Getters and Setters

    public DefaultDestinationProperties getDefaultDestination() {
        return defaultDestination;
    }

    public void setDefaultDestination(DefaultDestinationProperties defaultDestination) {
        this.defaultDestination = defaultDestination;
    }

    public String getRulesConfigPath() {
        return rulesConfigPath;
    }

    public void setRulesConfigPath(String rulesConfigPath) {
        this.rulesConfigPath = rulesConfigPath;
    }
    
    public List<RuleConfig> getRules() {
        return rules;
    }

    public void setRules(List<RuleConfig> rules) {
        this.rules = rules;
    }


    public static class DefaultDestinationProperties {
        @NotNull
        private DestinationType type = DestinationType.KAFKA_TOPIC;
        @NotBlank
        private String target = "default-unrouted-events-topic";
        private Map<String, String> properties;

        // Getters and Setters
        public DestinationType getType() { return type; }
        public void setType(DestinationType type) { this.type = type; }
        public String getTarget() { return target; }
        public void setTarget(String target) { this.target = target; }
        public Map<String, String> getProperties() { return properties; }
        public void setProperties(Map<String, String> properties) { this.properties = properties; }
    }

    public static class RuleConfig {
        @NotBlank
        private String id;
        private String description;
        @NotNull
        private Integer priority; // Use Integer for @NotNull
        @NotBlank
        private String conditionSpel; // SpEL expression string
        @NotNull
        private DestinationConfig destination;
        private boolean targetIsSpel = false; // Whether destination.target is a SpEL expression

        // Getters and Setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Integer getPriority() { return priority; }
        public void setPriority(Integer priority) { this.priority = priority; }
        public String getConditionSpel() { return conditionSpel; }
        public void setConditionSpel(String conditionSpel) { this.conditionSpel = conditionSpel; }
        public DestinationConfig getDestination() { return destination; }
        public void setDestination(DestinationConfig destination) { this.destination = destination; }
        public boolean isTargetIsSpel() { return targetIsSpel; }
        public void setTargetIsSpel(boolean targetIsSpel) { this.targetIsSpel = targetIsSpel; }
    }

    public static class DestinationConfig {
        @NotNull
        private DestinationType type;
        @NotBlank
        private String target; // Can be a direct target or a SpEL expression
        private Map<String, String> properties;

        // Getters and Setters
        public DestinationType getType() { return type; }
        public void setType(DestinationType type) { this.type = type; }
        public String getTarget() { return target; }
        public void setTarget(String target) { this.target = target; }
        public Map<String, String> getProperties() { return properties; }
        public void setProperties(Map<String, String> properties) { this.properties = properties; }
    }
}
