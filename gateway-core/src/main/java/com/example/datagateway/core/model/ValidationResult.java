package com.example.datagateway.core.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

/**
 * Represents the outcome of a validation process.
 * Typically part of {@link NormalizedDataEvent}.
 */
@Value
@Builder
public class ValidationResult {
    /**
     * Indicates whether the validation was successful.
     */
    boolean isValid;

    /**
     * A list of validation error messages.
     * This list will be empty if {@code isValid} is true.
     */
    @Singular // For Lombok builder to allow adding individual errors
    List<String> validationErrors;
}
