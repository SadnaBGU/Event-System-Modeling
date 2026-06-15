package com.eventsystem.application.order;

import com.eventsystem.domain.order.OrderItem;
import com.eventsystem.domain.shared.Money;

import jakarta.persistence.Embedded;

public record OrderItemDTO(
    String zoneId,
    String seatId,
    int quantity,
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