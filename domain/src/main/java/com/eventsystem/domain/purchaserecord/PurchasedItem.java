package com.eventsystem.domain.purchaserecord;

import java.math.BigDecimal;

import com.eventsystem.domain.shared.Money;

public record PurchasedItem(String zoneName, String seatId, int quantity, Money priceAtPurchase) {}
