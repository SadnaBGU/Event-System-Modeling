package com.eventsystem.application.order;

import com.eventsystem.domain.shared.Money;

import java.util.List;

public record CheckoutResult(
        String orderId,
        String purchaseRecordId,
        String orderStatus,
        Money totalPaid,
        String paymentTransactionId,
        List<String> issuedTicketCodes
) {
    public CheckoutResult {
        issuedTicketCodes = issuedTicketCodes == null ? List.of() : List.copyOf(issuedTicketCodes);
    }
}