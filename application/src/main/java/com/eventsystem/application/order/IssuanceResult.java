package com.eventsystem.application.order;

public record IssuanceResult(boolean success, String issuanceConfirmationId, String errorMessage) {
    public static IssuanceResult successful(String confirmationId) {
        return new IssuanceResult(true, confirmationId, null);
    }
    public static IssuanceResult failed(String errorMessage) {
        return new IssuanceResult(false, null, errorMessage);
    }
}