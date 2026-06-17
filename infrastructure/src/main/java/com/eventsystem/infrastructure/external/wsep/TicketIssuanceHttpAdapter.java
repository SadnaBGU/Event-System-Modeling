package com.eventsystem.infrastructure.external.wsep;

import com.eventsystem.application.order.ITicketIssuancePort;
import com.eventsystem.application.order.IssuanceResult;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.OrderItem;

import com.eventsystem.infrastructure.external.wsep.common.WsepAction;
import com.eventsystem.infrastructure.external.wsep.common.WsepCommunicationException;
import com.eventsystem.infrastructure.external.wsep.common.WsepHttpClient;
import com.eventsystem.infrastructure.external.wsep.common.WsepResponseParser;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Primary
public class TicketIssuanceHttpAdapter implements ITicketIssuancePort {

    private final WsepHttpClient client;

    public TicketIssuanceHttpAdapter(WsepHttpClient client) {
        this.client = client;
    }

    @Override
    public IssuanceResult issueTickets(String eventId, String activeOrderId, List<OrderItem> items, BuyerReference buyer) {
        if (items == null || items.isEmpty()) {
            return IssuanceResult.failed("Cannot issue tickets for an empty order");
        }

        try {
            String customerId = resolveCustomerId(buyer);

            List<String> ticketIds = items.stream()
                    .map(item -> issueSingleItem(eventId, customerId, item))
                    .toList();

            return IssuanceResult.successful(String.join(",", ticketIds));
        } catch (WsepCommunicationException e) {
            throw e;
        } catch (Exception e) {
            return IssuanceResult.failed(e.getMessage());
        }
    }

    private String issueSingleItem(String eventId, String customerId, OrderItem item) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("action_type", WsepAction.ISSUE_TICKET.actionType());
        params.put("customer_id", customerId);
        params.put("event_id", eventId);
        params.put("zone", item.getZoneId());

        if (isAssignedSeat(item)) {
            params.put("is_seating", "true");
            params.put("seats", seatsJson(item));
        } else {
            params.put("quantity", String.valueOf(item.getQuantity()));
        }

        String response = client.post(params);

        if (WsepResponseParser.isFailure(response)) {
            throw new IllegalStateException("Ticket issuance rejected by WSEP");
        }

        return response.trim();
    }

    private boolean isAssignedSeat(OrderItem item) {
        return item.getSeatId() != null && !item.getSeatId().isBlank();
    }

    private String seatsJson(OrderItem item) {
        SeatParts seatParts = SeatParts.fromSeatId(item.getSeatId());
        return "[{\"row\":" + seatParts.row() + ",\"seat\":" + seatParts.seat() + "}]";
    }

    private String resolveCustomerId(BuyerReference buyer) {
        if (buyer == null) {
            throw new IllegalArgumentException("Buyer reference must not be null");
        }

        if (buyer.memberId() != null && !buyer.memberId().isBlank()) {
            return buyer.memberId();
        }

        if (buyer.sessionId() != null && !buyer.sessionId().isBlank()) {
            return buyer.sessionId();
        }

        throw new IllegalArgumentException("Buyer reference must contain memberId or sessionId");
    }

    private record SeatParts(String row, String seat) {

        static SeatParts fromSeatId(String seatId) {
            if (seatId == null || seatId.isBlank()) {
                throw new IllegalArgumentException("seatId must not be blank for assigned seating");
            }

            String trimmed = seatId.trim();

            if (trimmed.contains(":")) {
                String[] parts = trimmed.split(":", 2);
                return new SeatParts(parts[0].trim(), parts[1].trim());
            }

            if (trimmed.contains("-")) {
                String[] parts = trimmed.split("-", 2);
                return new SeatParts(parts[0].trim(), parts[1].trim());
            }

            if (trimmed.toLowerCase().contains("row=") && trimmed.toLowerCase().contains("seat=")) {
                String[] parts = trimmed.split(",");
                String row = "0";
                String seat = trimmed;

                for (String part : parts) {
                    String[] keyValue = part.split("=", 2);

                    if (keyValue.length == 2 && keyValue[0].trim().equalsIgnoreCase("row")) {
                        row = keyValue[1].trim();
                    }

                    if (keyValue.length == 2 && keyValue[0].trim().equalsIgnoreCase("seat")) {
                        seat = keyValue[1].trim();
                    }
                }

                return new SeatParts(row, seat);
            }

            return new SeatParts("0", trimmed);
        }
    }
}