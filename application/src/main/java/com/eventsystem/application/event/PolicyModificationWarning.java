package com.eventsystem.application.event;

/**
 * PolicyModificationWarning Value Object
 * Represents warning information when policies are modified with active orders
 */
public class PolicyModificationWarning {
    private final boolean hasWarning;
    private final String message;

    private PolicyModificationWarning(boolean hasWarning, String message) {
        this.hasWarning = hasWarning;
        this.message = message;
    }

    public static PolicyModificationWarning noWarning() {
        return new PolicyModificationWarning(false, "");
    }

    public static PolicyModificationWarning warning(String message) {
        return new PolicyModificationWarning(true, message);
    }

    public boolean hasWarning() {
        return hasWarning;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return hasWarning ? message : "No warning";
    }
}
