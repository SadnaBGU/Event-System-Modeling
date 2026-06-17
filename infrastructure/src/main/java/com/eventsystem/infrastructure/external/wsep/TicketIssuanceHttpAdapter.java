package com.eventsystem.infrastructure.external.wsep;

import com.eventsystem.application.order.ITicketIssuancePort;
import com.eventsystem.application.order.IssuanceResult;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.OrderItem;
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
        Map<String, String> params = new LinkedHashMap<>();
        params.put("action_type", WsepAction.ACTION_ISSUE_TICKETS.actionString());
        params.put("event_id", eventId);
        params.put("order_id", activeOrderId);
        params.put("buyer_id", buyer.memberId());
        params.put("tickets", serializeItems(items));

        String response = client.post(params);

        if (isFailure(response)) {
            return IssuanceResult.failed("Ticket issuance rejected by WSEP");
        }

        return IssuanceResult.successful(response);
    }

    private String serializeItems(List<OrderItem> items) {
        return items.stream()
                .map(item -> item.getZoneId() + ":" + item.getSeatId() + ":" + item.getQuantity())
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private boolean isFailure(String response) {
        return response == null
                || response.isBlank()
                || response.equalsIgnoreCase("false")
                || response.equals("0")
                || response.equals("-1");
    }
}