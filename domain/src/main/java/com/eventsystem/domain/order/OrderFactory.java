package com.eventsystem.domain.order;

import java.time.Instant;
import java.util.UUID;

public class OrderFactory {
    public ActiveOrder createOrder(BuyerReference buyer, String eventId, Instant expiryTime) {
        return new ActiveOrder(UUID.randomUUID().toString(), buyer, eventId, expiryTime);
    }
}