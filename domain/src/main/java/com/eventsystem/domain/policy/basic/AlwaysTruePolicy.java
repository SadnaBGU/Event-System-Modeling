package com.eventsystem.domain.policy.basic;
import com.eventsystem.domain.policy.PolicyValidationResult;
import com.eventsystem.domain.policy.PurchaseContext;

public enum AlwaysTruePolicy implements IBasicPolicy {
    INSTANCE;

    @Override
    public PolicyValidationResult evaluate(PurchaseContext context) {
        return PolicyValidationResult.success();
    }

    @Override
    public boolean validate(PurchaseContext context) {
        return true;
    }

    @Override
    public void require(PurchaseContext context) {
        // no-op
    }
}