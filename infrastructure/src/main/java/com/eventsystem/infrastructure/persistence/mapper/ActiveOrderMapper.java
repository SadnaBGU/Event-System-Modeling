package com.eventsystem.infrastructure.persistence.mapper;

import com.eventsystem.domain.order.ActiveOrder;
import com.eventsystem.domain.order.OrderItem;
import com.eventsystem.infrastructure.persistence.entities.*;

import java.util.List;

public class ActiveOrderMapper {

    public static ActiveOrderEntity toEntity(ActiveOrder order) {

        ActiveOrderEntity entity = new ActiveOrderEntity();

        entity.setOrderId(order.getOrderId());
        entity.setBuyerRef(new BuyerReferenceEmbeddable(
                order.getBuyerRef().type(),
                order.getBuyerRef().sessionId(),
                order.getBuyerRef().memberId()
        ));

        entity.setEventId(order.getEventId());
        entity.setReservationExpiry(order.getReservationExpiry());
        entity.setStatus(order.getStatus());
        entity.setVersion(order.getVersion());

        entity.setItems(
                order.getItems().stream()
                        .map(ActiveOrderMapper::toEmbeddable)
                        .toList()
        );

        return entity;
    }

    public static ActiveOrder toDomain(ActiveOrderEntity entity) {

        ActiveOrder order = new ActiveOrder(
                entity.getOrderId(),
                new com.eventsystem.domain.order.BuyerReference(
                        entity.getBuyerRef().getType(),
                        entity.getBuyerRef().getSessionId(),
                        entity.getBuyerRef().getMemberId()
                ),
                entity.getEventId(),
                entity.getReservationExpiry()
        );

        entity.getItems()
                .forEach(i ->
                        order.addItem(new OrderItem(
                                i.getZoneId(),
                                i.getSeatId(),
                                i.getQuantity(),
                                i.getUnitPrice()
                        ))
                );

        return order;
    }

    private static OrderItem toEmbeddable(OrderItem item) {
        return new OrderItem(
                item.getZoneId(),
                item.getSeatId(),
                item.getQuantity(),
                item.getUnitPrice()
        );
    }
}