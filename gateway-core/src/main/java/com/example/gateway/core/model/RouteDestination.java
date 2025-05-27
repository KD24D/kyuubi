package com.example.gateway.core.model;

import java.util.Map;
import java.util.Objects;

public class RouteDestination {
    private DestinationType type;
    private String target; // e.g., "orders-processed-topic", "http://downstream-service/api/data"
    private Map<String, String> properties; // e.g., Kafka producer properties, HTTP method, headers

    public RouteDestination() {
    }

    public RouteDestination(DestinationType type, String target, Map<String, String> properties) {
        this.type = type;
        this.target = target;
        this.properties = properties;
    }

    // Getters, Setters, equals, hashCode, toString
    public DestinationType getType() {
        return type;
    }

    public void setType(DestinationType type) {
        this.type = type;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RouteDestination that = (RouteDestination) o;
        return type == that.type &&
               Objects.equals(target, that.target) &&
               Objects.equals(properties, that.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, target, properties);
    }

    @Override
    public String toString() {
        return "RouteDestination{" +
               "type=" + type +
               ", target='" + target + '\'' +
               ", properties=" + properties +
               '}';
    }
}
