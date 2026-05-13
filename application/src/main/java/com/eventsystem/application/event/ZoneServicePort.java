package com.eventsystem.application.event;

import com.eventsystem.domain.order.OrderItem;
import com.eventsystem.domain.zone.SeatId;
import com.eventsystem.domain.zone.ZoneId;

public interface ZoneServicePort {
    OrderItem reserveSeat(ZoneId zoneId, SeatId seatId);
    void releaseSeat(ZoneId zoneId, SeatId seatId);
}
