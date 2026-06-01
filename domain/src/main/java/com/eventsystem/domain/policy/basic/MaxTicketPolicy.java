package com.eventsystem.domain.policy.basic;

import com.eventsystem.domain.domainexceptions.PolicyException;
import com.eventsystem.domain.policy.PolicyValidationResult;
import com.eventsystem.domain.policy.PurchaseContext;

public final class MaxTicketPolicy implements IBasicPolicy {

    private final int maxTickets;

    public MaxTicketPolicy(int maxAllowed) {
        if (maxAllowed < 1) {
            throw new PolicyException("Max allowed tickets must be at least 1");
        }
        this.maxTickets = maxAllowed;
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