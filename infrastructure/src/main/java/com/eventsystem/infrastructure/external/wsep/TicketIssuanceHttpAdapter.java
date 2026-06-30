package com.eventsystem.infrastructure.external.wsep;

import com.eventsystem.application.order.ITicketIssuancePort;
import com.eventsystem.application.order.IssuanceResult;
import com.eventsystem.application.order.TicketIssuanceItem;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.infrastructure.external.wsep.common.WsepAction;
import com.eventsystem.infrastructure.external.wsep.common.WsepCommunicationException;
import com.eventsystem.infrastructure.external.wsep.common.WsepHttpClient;
import com.eventsystem.infrastructure.external.wsep.common.WsepResponseParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

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
    private final ObjectMapper objectMapper;

    public TicketIssuanceHttpAdapter(WsepHttpClient client) {
        this.client = client;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public IssuanceResult issueTickets(
            String eventId,
            String activeOrderId,
            List<TicketIssuanceItem> items,
            BuyerReference buyer
    ) {
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

            for (TicketIssuanceItem item : items) {
                String ticketId = issueSingleItem(eventId, customerId, item);
                issuedTicketIds.add(ticketId);
            }

            log.info("WSEP ticket issuance succeeded for eventId={}, activeOrderId={}, issuedTicketCount={}",
                    eventId, activeOrderId, issuedTicketIds.size());

            return IssuanceResult.successful(issuedTicketIds);

        } catch (WsepCommunicationException e) {
            log.error(
                    "WSEP ticket issuance communication failure for eventId={}, activeOrderId={}. Rolling back {} issued tickets",
                    eventId, activeOrderId, issuedTicketIds.size(), e
            );
            rollbackIssuedTicketsBestEffort(issuedTicketIds);
            throw e;

        } catch (Exception e) {
            log.warn(
                    "WSEP ticket issuance failed for eventId={}, activeOrderId={}. Rolling back {} issued tickets. Reason={}",
                    eventId, activeOrderId, issuedTicketIds.size(), e.getMessage()
            );
            rollbackIssuedTicketsBestEffort(issuedTicketIds);
            return IssuanceResult.failed(e.getMessage());
        }
    }

    private String issueSingleItem(String eventId, String customerId, TicketIssuanceItem item) {
        validateTicketIssuanceItem(item);

        Map<String, String> params = new LinkedHashMap<>();
        params.put("action_type", WsepAction.ISSUE_TICKET.actionType());
        params.put("customer_id", customerId);
        params.put("event_id", eventId);

        // WSEP examples use the external/display zone name, not our internal UUID.
        params.put("zone", item.zoneName());

        boolean assignedSeat = item.isAssignedSeat();

        if (assignedSeat) {
            params.put("is_seating", "true");
            params.put("seats", seatsJson(item));
        } else {
            params.put("quantity", String.valueOf(item.quantity()));
        }

        log.debug(
                "Calling WSEP issue_ticket for eventId={}, zoneName={}, assignedSeat={}, quantity={}, seatId={}, rowLabel={}, seatNumber={}",
                eventId,
                item.zoneName(),
                assignedSeat,
                item.quantity(),
                item.seatId(),
                item.rowLabel(),
                item.seatNumber()
        );

        log.warn("""
                DEBUG WSEP issue_ticket params:
                action_type={}
                customer_id={}
                event_id={}
                zone={}
                assignedSeat={}
                quantity={}
                seatId={}
                rowLabel={}
                seatNumber={}
                seats={}
                fullParams={}
                """,
                params.get("action_type"),
                params.get("customer_id"),
                params.get("event_id"),
                params.get("zone"),
                assignedSeat,
                item.quantity(),
                item.seatId(),
                item.rowLabel(),
                item.seatNumber(),
                params.get("seats"),
                params
        );

        String response = client.post(params);

        if (WsepResponseParser.isFailure(response)) {
            log.warn("WSEP issue_ticket rejected for eventId={}, zoneName={}, assignedSeat={}, response={}",
                    eventId, item.zoneName(), assignedSeat, response);
            throw new IllegalStateException("Ticket issuance rejected by WSEP");
        }

        log.debug("WSEP issue_ticket succeeded for eventId={}, zoneName={}, ticketId={}",
                eventId, item.zoneName(), response.trim());

        return response.trim();
    }

    private void validateTicketIssuanceItem(TicketIssuanceItem item) {
        if (item == null) {
            throw new IllegalArgumentException("Ticket issuance item must not be null");
        }

        if (item.zoneName() == null || item.zoneName().isBlank()) {
            throw new IllegalArgumentException("zoneName is required for ticket issuance");
        }

        if (item.quantity() <= 0) {
            throw new IllegalArgumentException("quantity must be positive for ticket issuance");
        }

        if (item.isAssignedSeat()) {
            if (item.rowLabel() == null || item.rowLabel().isBlank()) {
                throw new IllegalArgumentException("rowLabel is required for assigned seating ticket issuance");
            }

            if (item.seatNumber() == null) {
                throw new IllegalArgumentException("seatNumber is required for assigned seating ticket issuance");
            }
        }
    }

    private String seatsJson(TicketIssuanceItem item) {
        try {
            return objectMapper.writeValueAsString(List.of(
                    Map.of(
                            "row", item.rowLabel(),
                            "seat", item.seatNumber()
                    )
            ));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize assigned seating data for WSEP", e);
        }
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
}