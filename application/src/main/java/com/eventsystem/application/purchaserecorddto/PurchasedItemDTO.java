package com.eventsystem.application.purchaserecorddto;

import com.eventsystem.domain.shared.Money;

public record PurchasedItemDTO(String zoneName, String seatId, int quantity, Money priceAtPurchase) {
    public static PurchasedItemDTO fromDomain(
            com.eventsystem.domain.purchaserecord.PurchasedItem purchasedItem) {
        
        return new PurchasedItemDTO(
            purchasedItem.zoneName(),
            purchasedItem.seatId(),
            purchasedItem.quantity(),
            purchasedItem.priceAtPurchase()
        );
    }
}
