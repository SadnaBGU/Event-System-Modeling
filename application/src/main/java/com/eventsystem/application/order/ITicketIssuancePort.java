package com.eventsystem.application.order;

import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.OrderItem;
import java.util.List;

public interface ITicketIssuancePort {
    
    IssuanceResult issueTickets(String eventId, String activeOrderId, List<OrderItem> items, BuyerReference buyer);
}