package com.eventsystem.domain.policy.rule.basic;
import com.eventsystem.domain.policy.rule.PolicyType;
import com.eventsystem.domain.policy.shared.PolicyValidationResult;
import com.eventsystem.domain.policy.shared.PurchaseContext;

public enum AlwaysTruePolicy implements IBasicPolicy {
    INSTANCE;

    @Override
    public PolicyType type() {
        return PolicyType.ALWAYS_TRUE;
    }

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