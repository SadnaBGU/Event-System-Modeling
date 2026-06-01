package com.eventsystem.domain.policy.basic;
import com.eventsystem.domain.policy.PolicyType;
import com.eventsystem.domain.policy.PolicyValidationResult;
import com.eventsystem.domain.policy.PurchaseContext;
import com.eventsystem.domain.domainexceptions.PolicyException;

public enum NeverAllowPolicy
 implements IBasicPolicy {
    INSTANCE;

    @Override
    public PolicyType type() {
        return PolicyType.NEVER_ALLOW;
    }

    @Override
    public PolicyValidationResult evaluate(PurchaseContext context) {
        return PolicyValidationResult.failure("Purchase policy restricts current purchase");
    }

    @Override
    public boolean validate(PurchaseContext context) {
        return false;
    }

    @Override
    public void require(PurchaseContext context) {
        throw new PolicyException("Purchase policy restricts current purchase");
    }
}