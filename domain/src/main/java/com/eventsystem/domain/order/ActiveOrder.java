package com.eventsystem.domain.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.eventsystem.domain.domainexceptions.ActiveOrderHasExpiredException;
import com.eventsystem.domain.domainexceptions.ActiveOrderNotActiveException;
import com.eventsystem.domain.shared.Money;



public class ActiveOrder {
    private final String orderId;
    private final BuyerReference buyerRef;
    private final String eventId;
    private final List<OrderItem> items;
    private final Instant reservationExpiry;
    private OrderStatus status;
    private long version;

    ActiveOrder(String orderId, BuyerReference buyerRef, String eventId, Instant reservationExpiry) {
        this.orderId = orderId;
        this.buyerRef = buyerRef;
        this.eventId = eventId;
        this.items = new ArrayList<>();
        this.reservationExpiry = reservationExpiry;
        this.status = OrderStatus.ACTIVE;
        this.version = 0L;
    }

    public void addItem(OrderItem item) {
        verifyActive();
        items.add(item);
    }

    public void removeItem(String zoneId, String seatId) {
        verifyActive();
        items.removeIf(item -> item.getZoneId().equals(zoneId) && 
                               (item.getSeatId() != null && item.getSeatId().equals(seatId)));
    }

    public void checkout() {
        verifyActive();
        this.status = OrderStatus.CHECKED_OUT;
    }

    public List<OrderItem> expire() {
        this.status = OrderStatus.EXPIRED;
        return List.copyOf(items);
    }

    public List<OrderItem> cancel() {
        this.status = OrderStatus.CANCELLED;
        return List.copyOf(items);
    }

    public boolean isExpired() {
        return Instant.now().isAfter(reservationExpiry) && status == OrderStatus.ACTIVE;
    }

    private void verifyActive() {
        if (status != OrderStatus.ACTIVE) {
            throw new ActiveOrderNotActiveException(eventId);
        }
        if (isExpired()) {
            throw new ActiveOrderHasExpiredException(eventId);
        }
    }

    public String getOrderId() { 
        return orderId; 
    }

    public long getVersion() { 
        return version; 
    }

    public BuyerReference getBuyerRef() { 
        return buyerRef; 
    }

    public String getEventId() { 
        return eventId; 
    }

    public Money calculateBaseTotal() {
        String currency = items.isEmpty() ? "USD" : items.get(0).getUnitPrice().currency();
        return items.stream()
                .map(OrderItem::getUnitPrice)
                .reduce(new Money(BigDecimal.ZERO, currency), Money::add);
    }

    public List<OrderItem> getItems() { 
        return List.copyOf(items); 
    }
}

