package com.eventsystem.application.order;

import com.eventsystem.domain.order.BuyerReference;
import java.util.List;

public interface ITicketIssuancePort {

    IssuanceResult issueTickets(String eventId, String activeOrderId, List<TicketIssuanceItem> items, BuyerReference buyer);
}