package com.eventsystem.domain.policy.basic;

import com.eventsystem.domain.domainexceptions.PurchasePolicyException;
import com.eventsystem.domain.policy.IPolicy;
import com.eventsystem.domain.policy.PurchaseContext;

public class NoSingleEmptySeatPolicy implements IPolicy{

    @Override
    public boolean validate(PurchaseContext context) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'validate'");
    }

    @Override
    public void require(PurchaseContext context) {
        if (!validate(context)) {
            throw new PurchasePolicyException(String.format(
                "Cannot leave nearby single empty seats to Event %s", context.getEventName()));
        }

    }
    //TODO - After implementing, add to builder!
}
