package com.eventsystem.application.event;

import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.OrderItem;
import com.eventsystem.domain.purchaserecord.DiscountSnapshot;
import com.eventsystem.domain.purchaserecord.EventSnapshot;

import java.math.BigDecimal;
import java.util.List;

public interface EventQueryPort {
    boolean validatePurchasePolicy(String eventId, BuyerReference buyer, List<OrderItem> items);
    DiscountSnapshot applyDiscount(String eventId, String discountCode, BigDecimal baseTotal);
    EventSnapshot getEventSnapshot(String eventId);
}