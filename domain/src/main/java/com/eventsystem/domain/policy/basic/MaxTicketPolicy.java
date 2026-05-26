package com.eventsystem.domain.policy.basic;

import com.eventsystem.domain.domainexceptions.PolicyException;
import com.eventsystem.domain.domainexceptions.PurchasePolicyException;
import com.eventsystem.domain.policy.IPolicy;
import com.eventsystem.domain.policy.PurchaseContext;

public final class MaxTicketPolicy implements IPolicy{

    private final int maxTickets;

    public MaxTicketPolicy(int maxAllowed) {
        if (maxAllowed < 1) {
            throw new PolicyException("Max allowed tickets must be at least 1");
        }
        this.maxTickets = maxAllowed;
    }

    @Override
    public boolean validate(PurchaseContext context) {
        return context.zonesOfEachEventTicket().size() <= maxTickets;
    }

    @Override
    public void require(PurchaseContext context) {
        if (!validate(context)) {
            throw new PurchasePolicyException(String.format(
                "Cannot Purchase more than %d tickets to Event %s",
                 maxTickets, context.getEventName()
            ));
        }

    }
    
}
