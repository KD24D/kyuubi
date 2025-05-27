package com.example.gateway.routinglimiting.routing;

import com.example.gateway.core.model.RouteDestination; // Using the core model

import java.util.Objects;
import java.util.function.Predicate; // Using Predicate for condition

public class RoutingRule {
    private final String id;
    private final String description;
    private final int priority; // Lower numbers mean higher priority
    private final Predicate<Object> condition; // Condition to evaluate against TransformedDataEvent or its payload
    private final RouteDestination destination; // Using RouteDestination from core model

    public RoutingRule(String id, String description, int priority, Predicate<Object> condition, RouteDestination destination) {
        this.id = id;
        this.description = description;
        this.priority = priority;
        this.condition = condition;
        this.destination = destination;
    }

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public int getPriority() {
        return priority;
    }

    public Predicate<Object> getCondition() {
        return condition;
    }

    public RouteDestination getDestination() {
        return destination;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RoutingRule that = (RoutingRule) o;
        return priority == that.priority && Objects.equals(id, that.id); // ID should be unique
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "RoutingRule{" +
               "id='" + id + '\'' +
               ", description='" + description + '\'' +
               ", priority=" + priority +
               ", destination=" + destination +
               '}';
    }
}
