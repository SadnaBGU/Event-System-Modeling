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
            throw new IllegalArgumentException("Payment details token must not be blank");
        }

        try {
            WsepPaymentDetails details = MAPPER.readValue(paymentDetailsToken, WsepPaymentDetails.class);
            details.validate();
            return details;
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Payment details token must be JSON with: card_number, month, year, holder, cvv, id",
                    e
            );
        }
    }

    private void validate() {
        require(cardNumber, "card_number");
        require(month, "month");
        require(year, "year");
        require(holder, "holder");
        require(cvv, "cvv");
        require(id, "id");
    }

    private static void require(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required WSEP payment field: " + fieldName);
        }
    }
}