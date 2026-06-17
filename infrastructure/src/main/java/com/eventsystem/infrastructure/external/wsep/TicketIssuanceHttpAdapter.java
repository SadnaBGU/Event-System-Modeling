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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@Primary
public class TicketIssuanceHttpAdapter implements ITicketIssuancePort {

    private static final Logger log = LoggerFactory.getLogger(TicketIssuanceHttpAdapter.class);

    private final WsepHttpClient client;

    public TicketIssuanceHttpAdapter(WsepHttpClient client) {
        this.client = client;
    }

    @Override
    public IssuanceResult issueTickets(String eventId, String activeOrderId, List<OrderItem> items,
            BuyerReference buyer) {
        if (items == null || items.isEmpty()) {
            log.warn("WSEP ticket issuance requested for empty order, eventId={}, activeOrderId={}",
                    eventId, activeOrderId);
            return IssuanceResult.failed("Cannot issue tickets for an empty order");
        }

        log.info("Calling WSEP issue_ticket for eventId={}, activeOrderId={}, itemCount={}",
                eventId, activeOrderId, items.size());
        List<String> issuedTicketIds = new ArrayList<>();

        try {
            String customerId = resolveCustomerId(buyer);

            for (OrderItem item : items) {
                String ticketId = issueSingleItem(eventId, customerId, item);
                issuedTicketIds.add(ticketId);
            }

            log.info("WSEP ticket issuance succeeded for eventId={}, activeOrderId={}, issuedTicketCount={}",
                    eventId, activeOrderId, issuedTicketIds.size());
            return IssuanceResult.successful(String.join(",", issuedTicketIds));

        } catch (WsepCommunicationException e) {
            log.error(
                    "WSEP ticket issuance communication failure for eventId={}, activeOrderId={}. Rolling back {} issued tickets",
                    eventId, activeOrderId, issuedTicketIds.size(), e);
            rollbackIssuedTicketsBestEffort(issuedTicketIds);
            throw e;

        } catch (Exception e) {
            log.warn(
                    "WSEP ticket issuance failed for eventId={}, activeOrderId={}. Rolling back {} issued tickets. Reason={}",
                    eventId, activeOrderId, issuedTicketIds.size(), e.getMessage());
            rollbackIssuedTicketsBestEffort(issuedTicketIds);
            return IssuanceResult.failed(e.getMessage());
        }
    }

    private String issueSingleItem(String eventId, String customerId, OrderItem item) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("action_type", WsepAction.ISSUE_TICKET.actionType());
        params.put("customer_id", customerId);
        params.put("event_id", eventId);
        params.put("zone", item.getZoneId());

        boolean isSeatingZoneTicket = isAssignedSeat(item);
        if (isSeatingZoneTicket) {
            params.put("is_seating", "true");
            params.put("seats", seatsJson(item));
        } else {
            params.put("quantity", String.valueOf(item.getQuantity()));
        }
        log.debug("Calling WSEP issue_ticket for eventId={}, zone={}, assignedSeat={}, quantity={}",
                eventId, item.getZoneId(), isSeatingZoneTicket, item.getQuantity());

        String response = client.post(params);

        if (WsepResponseParser.isFailure(response)) {
            log.warn("WSEP issue_ticket rejected for eventId={}, zone={}, assignedSeat={}",
                    eventId, item.getZoneId(), isSeatingZoneTicket);
            throw new IllegalStateException("Ticket issuance rejected by WSEP");
        }

        log.debug("WSEP issue_ticket succeeded for eventId={}, zone={}, ticketId={}",
                eventId, item.getZoneId(), response.trim());
        return response.trim();
    }

    private void rollbackIssuedTicketsBestEffort(List<String> issuedTicketIds) {
        if (issuedTicketIds.isEmpty()) {
            log.debug("No WSEP issued tickets to rollback");
            return;
        }

        log.info("Rolling back {} already-issued WSEP tickets using cancel_ticket",
                issuedTicketIds.size());

        for (String ticketId : issuedTicketIds) {
            try {
                cancelIssuedTicket(ticketId);
                log.info("WSEP cancel_ticket succeeded for ticketId={}", ticketId);
            } catch (Exception e) {
                log.error("WSEP cancel_ticket failed for ticketId={}. Manual reconciliation may be required",
                        ticketId, e);
            }
        }
    }

    private void cancelIssuedTicket(String ticketId) {
        log.debug("Calling WSEP cancel_ticket for ticketId={}", ticketId);

        Map<String, String> params = new LinkedHashMap<>();
        params.put("action_type", WsepAction.CANCEL_TICKET.actionType());
        params.put("ticket_id", ticketId);

        String response = client.post(params);

        if (!WsepResponseParser.isSuccessOne(response)) {
            log.warn("WSEP cancel_ticket rejected for ticketId={}, response={}", ticketId, response);
            throw new IllegalStateException("Ticket cancellation rejected by WSEP. Response: " + response);
        }
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