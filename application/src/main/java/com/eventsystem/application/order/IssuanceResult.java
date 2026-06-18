package com.eventsystem.application.order;

import java.util.List;

public record IssuanceResult(boolean success, List<String> issuedTicketCodes, String errorMessage) {
    public static IssuanceResult successful(String ticketCode) {
        return successful(List.of(ticketCode));
    }

    public static IssuanceResult successful(List<String> issuedTicketCodes) {
        if (issuedTicketCodes == null || issuedTicketCodes.isEmpty()) {
            throw new IllegalArgumentException("issuedTicketCodes must not be null or empty");
        }

        return new IssuanceResult(true, List.copyOf(issuedTicketCodes), null);
    }

    public static IssuanceResult failed(String errorMessage) {
        return new IssuanceResult(false, List.of(), errorMessage);
    }

    /**
     * Backward-compatible confirmation string for PurchaseRecord.
     * If WSEP issued multiple ticket codes, store them comma-separated.
     */
    public String issuanceConfirmationId() {
        return String.join(",", issuedTicketCodes);
    }
}