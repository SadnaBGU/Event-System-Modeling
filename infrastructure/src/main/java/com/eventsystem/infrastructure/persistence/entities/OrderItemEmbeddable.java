package com.eventsystem.infrastructure.persistence.entities;

import com.eventsystem.domain.shared.Money;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;

@Embeddable
public class OrderItemEmbeddable {

    private String zoneId;
    private String seatId;
    private int quantity;

    @Embedded
    private Money unitPrice;

    protected OrderItemEmbeddable() {}

    public OrderItemEmbeddable(String zoneId, String seatId, int quantity, Money unitPrice) {
        this.zoneId = zoneId;
        this.seatId = seatId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public String getZoneId() {
        return zoneId;
    }

    public String getSeatId() {
        return seatId;
    }

    public int getQuantity() {
        return quantity;
    }

    public Money getUnitPrice() {
        return unitPrice;
    }
}