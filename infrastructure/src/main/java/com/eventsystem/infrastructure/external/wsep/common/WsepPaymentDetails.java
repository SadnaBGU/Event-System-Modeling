package com.eventsystem.infrastructure.external.wsep.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

public record WsepPaymentDetails(
        @JsonProperty("card_number") String cardNumber,
        String month,
        String year,
        String holder,
        String cvv,
        String id
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static WsepPaymentDetails fromJson(String paymentDetailsToken) {
        if (paymentDetailsToken == null || paymentDetailsToken.isBlank()) {
            throw new IllegalArgumentException("Payment details are required.");
        }

        WsepPaymentDetails details;
        try {
            details = MAPPER.readValue(paymentDetailsToken, WsepPaymentDetails.class);
        } catch (Exception e) {
            // Malformed JSON only. Field-level problems are reported by validate() with a
            // specific, user-facing message and must NOT be flattened into this generic one.
            throw new IllegalArgumentException(
                    "Payment details are not in a valid format. Expected JSON with: "
                            + "card_number, month, year, holder, cvv, id.",
                    e
            );
        }

        details.validate();
        return details;
    }

    /**
     * Validates the card fields and fails with a short, informative message naming the
     * offending field (e.g. CVV), so the buyer gets actionable feedback instead of a
     * generic "payment failed". Validation runs before any WSEP call.
     */
    private void validate() {
        requirePresent(holder, "Cardholder name");
        requireDigits(cardNumber, "Card number", 13, 19);
        requireExpiryMonth(month);
        requireExpiryYear(year);
        requireDigits(cvv, "CVV", 3, 4);
        requireDigits(id, "ID number", 1, 20);
    }

    private static void requirePresent(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
    }

    private static void requireDigits(String value, String label, int minLen, int maxLen) {
        requirePresent(value, label);
        String digits = value.trim();
        if (!digits.matches("\\d+")) {
            throw new IllegalArgumentException(label + " must contain only digits.");
        }
        if (digits.length() < minLen || digits.length() > maxLen) {
            String expected = minLen == maxLen ? minLen + " digits" : minLen + "–" + maxLen + " digits";
            throw new IllegalArgumentException(label + " must be " + expected + ".");
        }
    }

    private static void requireExpiryMonth(String value) {
        requirePresent(value, "Card expiry month");
        int month;
        try {
            month = Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Card expiry month must be a number between 1 and 12.");
        }
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Card expiry month must be between 1 and 12.");
        }
    }

    private static void requireExpiryYear(String value) {
        requirePresent(value, "Card expiry year");
        if (!value.trim().matches("\\d{4}")) {
            throw new IllegalArgumentException("Card expiry year must be a 4-digit year.");
        }
    }
}