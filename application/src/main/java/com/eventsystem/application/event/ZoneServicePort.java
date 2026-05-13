package com.eventsystem.application.event;

import com.eventsystem.domain.order.OrderItem;

public interface ZoneServicePort {
    OrderItem lockSeat(String zoneId, String seatId, String activeOrderId);
    void unlockSeat(String zoneId, String seatId);
}
