package com.eventsystem.domain.purchaserecord;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.eventsystem.domain.shared.Money;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;


public record PurchaseRecord(
    String recordId,
    String buyerId,
    @Embedded
    BuyerSnapshot buyerSnapshot,
    @Embedded
    EventSnapshot eventSnapshot,
    @ElementCollection
    List<PurchasedItem> items,
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "price_amount")),
        @AttributeOverride(name = "currency", column = @Column(name = "price_currency"))
    })
    Money totalPaid,
    @ElementCollection
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