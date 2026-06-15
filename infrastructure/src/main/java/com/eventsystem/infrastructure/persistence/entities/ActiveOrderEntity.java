package com.eventsystem.infrastructure.persistence.entities;

import java.time.Instant;
import java.util.List;

import com.eventsystem.domain.order.OrderItem;
import com.eventsystem.domain.order.OrderStatus;
import com.eventsystem.domain.shared.Money;

import jakarta.persistence.*;

@Entity
@Table(name = "active_orders")
public class ActiveOrderEntity {

    @Id
    private String orderId;

    @Embedded
    private BuyerReferenceEmbeddable buyerRef;

    private String eventId;

    @ElementCollection
    @CollectionTable(
        name = "active_order_items",
        joinColumns = @JoinColumn(name = "order_id")
    )
    private List<OrderItem> items;

    private Instant reservationExpiry;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private long version;

    public ActiveOrderEntity() {}

    // getters/setters

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public BuyerReferenceEmbeddable getBuyerRef() {
        return buyerRef;
    }

    public void setBuyerRef(BuyerReferenceEmbeddable buyerRef) {
        this.buyerRef = buyerRef;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public void setItems(List<OrderItem> items) {
        this.items = items;
    }

    public Instant getReservationExpiry() {
        return reservationExpiry;
    }

    public void setReservationExpiry(Instant reservationExpiry) {
        this.reservationExpiry = reservationExpiry;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }
}