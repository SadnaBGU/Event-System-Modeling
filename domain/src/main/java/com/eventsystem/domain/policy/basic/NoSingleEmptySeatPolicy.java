package com.eventsystem.domain.policy.basic;

import com.eventsystem.domain.domainexceptions.PurchasePolicyException;
import com.eventsystem.domain.policy.PolicyType;
import com.eventsystem.domain.policy.PolicyValidationResult;
import com.eventsystem.domain.policy.PurchaseContext;

public class NoSingleEmptySeatPolicy implements IBasicPolicy{

    @Override
    public PolicyType type() {
        return PolicyType.NO_SINGLE_EMPTY_SEAT;
    }

    @Override
    public PolicyValidationResult evaluate(PurchaseContext context) {
        return PolicyValidationResult.failure(
                "NoSingleEmptySeatPolicy- Not supported"
        );
    }

    @Override
    public boolean validate(PurchaseContext context) {
        // Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'validate'");
    }

    @Override
    public void require(PurchaseContext context) {
        if (!validate(context)) {
            throw new PurchasePolicyException(String.format(
                "Cannot leave nearby single empty seats to Event %s", context.eventId().toString()));
        }

    }
    //TODO - After implementing, add to builder!
}
