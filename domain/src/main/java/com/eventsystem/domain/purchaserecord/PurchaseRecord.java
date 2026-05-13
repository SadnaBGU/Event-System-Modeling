package com.eventsystem.domain.purchaserecord;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.eventsystem.domain.shared.Money;


public record PurchaseRecord(
    String recordId,
    String buyerId,
    BuyerSnapshot buyerSnapshot,
    EventSnapshot eventSnapshot,
    List<PurchasedItem> items,
    Money totalPaid,
    List<DiscountSnapshot> discountsApplied,
    Instant purchaseTimestamp,
    String paymentConfirmationId,
    String ticketIssuanceConfirmationId
) {
    public static PurchaseRecord create(
            String buyerId,
            BuyerSnapshot buyerSnapshot,
            EventSnapshot eventSnapshot,
            List<PurchasedItem> items,
            Money totalPaid,
            List<DiscountSnapshot> discountsApplied,
            String paymentConfirmationId,
            String ticketIssuanceConfirmationId) {
        
        return new PurchaseRecord(
            UUID.randomUUID().toString(),
            buyerId,
            buyerSnapshot,
            eventSnapshot,
            List.copyOf(items),
            totalPaid,
            List.copyOf(discountsApplied),
            Instant.now(),
            paymentConfirmationId,
            ticketIssuanceConfirmationId
        );
    }
}