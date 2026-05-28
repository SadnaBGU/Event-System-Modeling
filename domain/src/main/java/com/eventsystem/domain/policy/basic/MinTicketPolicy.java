package com.eventsystem.domain.policy.basic;

import com.eventsystem.domain.domainexceptions.PolicyException;
import com.eventsystem.domain.domainexceptions.PurchasePolicyException;
import com.eventsystem.domain.policy.PurchaseContext;

public final class MinTicketPolicy implements IBasicPolicy{

    private final int minTickets;

    public MinTicketPolicy(int minAllowed) {
        if (minAllowed < 1) {
            throw new PolicyException("Minimum allowed tickets must be at least 1");
        }
        this.minTickets = minAllowed;
    }

    @Override
    public boolean validate(PurchaseContext context) {
        return context.ticketCount() >= minTickets;
    }

    @Override
    public void require(PurchaseContext context) {
        if (!validate(context)) {
            throw new PurchasePolicyException(String.format(
                "Cannot Purchase less than %d tickets to Event %s",
                 minTickets, context.eventId().toString()
            ));
        }

    }
    
}
