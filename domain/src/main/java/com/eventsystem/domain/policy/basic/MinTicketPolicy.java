package com.eventsystem.domain.policy.basic;

import com.eventsystem.domain.domainexceptions.PolicyException;
import com.eventsystem.domain.policy.PolicyValidationResult;
import com.eventsystem.domain.policy.PurchaseContext;

public final class MinTicketPolicy implements IBasicPolicy {

    private final int minTickets;

    public MinTicketPolicy(int minAllowed) {
        if (minAllowed < 1) {
            throw new PolicyException("Minimum allowed tickets must be at least 1");
        }
        this.minTickets = minAllowed;
    }

    public int minTickets() {
        return minTickets;
    }

    @Override
    public PolicyValidationResult evaluate(PurchaseContext context) {
        if (context.ticketCount() >= minTickets) {
            return PolicyValidationResult.success();
        }

        return PolicyValidationResult.failure(String.format(
                "Cannot purchase less than %d tickets to event %s",
                minTickets,
                context.eventId()
        ));
    }
}