package com.eventsystem.application.order;

import java.time.Instant;
import java.util.List;

import com.eventsystem.domain.order.OrderStatus;

public record ActiveOrderDTO(
    String orderId,
    BuyerRefernceDTO buyerRef,
    String eventId,
    List<OrderItemDTO> items,
    Instant reservationExpiry,
    OrderStatus status,
    long version
) {

    public static ActiveOrderDTO fromDomain(com.eventsystem.domain.order.ActiveOrder order) {
        return new ActiveOrderDTO(
            order.getOrderId(),
            BuyerRefernceDTO.fromDomain(order.getBuyerRef()),
            order.getEventId(),
            order.getItems().stream().map(OrderItemDTO::fromDomain).toList(),
            order.getReservationExpiry(),
            order.getStatus(),
            order.getVersion()
        );
    }
    
}
