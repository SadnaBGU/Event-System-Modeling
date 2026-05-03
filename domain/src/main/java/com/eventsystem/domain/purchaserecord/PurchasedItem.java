package com.eventsystem.domain.purchaserecord;

import java.math.BigDecimal;

public record PurchasedItem(String zoneName, String rowLabel, Integer seatNumber, int quantity, BigDecimal priceAtPurchase) {}
