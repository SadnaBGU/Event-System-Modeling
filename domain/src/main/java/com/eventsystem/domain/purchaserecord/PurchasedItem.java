package com.eventsystem.domain.purchaserecord;

import com.eventsystem.domain.shared.Money;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;

@Embeddable
public record PurchasedItem(
    String zoneName,
    String seatId,
    int quantity,
    Money priceAtPurchase
) {}