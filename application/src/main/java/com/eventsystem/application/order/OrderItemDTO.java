package com.eventsystem.application.order;

import com.eventsystem.domain.order.OrderItem;
import com.eventsystem.domain.shared.Money;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;

public record OrderItemDTO(
    String zoneId,
    String seatId,
    int quantity,
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "price_amount")),
        @AttributeOverride(name = "currency", column = @Column(name = "price_currency"))
    })
    Money unitPrice
) {

    public static OrderItemDTO fromDomain(OrderItem item) {
        return new OrderItemDTO(
            item.getZoneId(),
            item.getSeatId(),
            item.getQuantity(),
            item.getUnitPrice()
        );
    }
}