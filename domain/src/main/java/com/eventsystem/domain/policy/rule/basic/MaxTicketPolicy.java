package com.eventsystem.domain.policy.rule.basic;

import com.eventsystem.domain.domainexceptions.PolicyException;
import com.eventsystem.domain.policy.rule.PolicyType;
import com.eventsystem.domain.policy.shared.PolicyValidationResult;
import com.eventsystem.domain.policy.shared.PurchaseContext;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
public final class MaxTicketPolicy implements IBasicPolicy {
    @JsonProperty("maxTickets")
    private final int maxTickets;
    @JsonCreator
    public MaxTicketPolicy(@JsonProperty("maxTickets")int maxAllowed) {
        if (maxAllowed < 1) {
            throw new PolicyException("Max allowed tickets must be at least 1");
        }
        this.maxTickets = maxAllowed;
    }

    @Override
    public PolicyType type() {
        return PolicyType.MAX_TICKETS;
    }

    public int maxTickets() {
        return maxTickets;
    }

    @Override
    public PolicyValidationResult evaluate(PurchaseContext context) {
        if (context.ticketCount() <= maxTickets) {
            return PolicyValidationResult.success();
        }

        return PolicyValidationResult.failure(String.format(
                "Cannot purchase more than %d tickets to event %s",
                maxTickets,
                context.eventId()
        ));
    }
}