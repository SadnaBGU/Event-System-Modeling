package com.eventsystem.domain.purchaserecord;

import java.math.BigDecimal;

public record PurchasedItem(String zoneName, String seatId, int quantity, BigDecimal priceAtPurchase) {}
