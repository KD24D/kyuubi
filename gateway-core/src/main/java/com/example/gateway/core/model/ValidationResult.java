package com.example.gateway.core.model;

import java.util.List;
import java.util.Objects;

public class ValidationResult {
    private boolean isValid;
    private List<String> validationErrors;

    public ValidationResult() {
    }

    public ValidationResult(boolean isValid, List<String> validationErrors) {
        this.isValid = isValid;
        this.validationErrors = validationErrors;
    }

    // Getters, Setters, equals, hashCode, toString
    public boolean isValid() {
        return isValid;
    }

    public void setValid(boolean valid) {
        isValid = valid;
    }

    public List<String> getValidationErrors() {
        return validationErrors;
    }

    public void setValidationErrors(List<String> validationErrors) {
        this.validationErrors = validationErrors;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ValidationResult that = (ValidationResult) o;
        return isValid == that.isValid &&
               Objects.equals(validationErrors, that.validationErrors);
    }

    @Override
    public int hashCode() {
        return Objects.hash(isValid, validationErrors);
    }

    @Override
    public String toString() {
        return "ValidationResult{" +
               "isValid=" + isValid +
               ", validationErrors=" + validationErrors +
               '}';
    }
}
