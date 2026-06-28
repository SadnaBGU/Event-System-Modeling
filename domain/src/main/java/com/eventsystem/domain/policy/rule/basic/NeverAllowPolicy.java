package com.eventsystem.domain.policy.rule.basic;

import com.eventsystem.domain.domainexceptions.PolicyException;
import com.eventsystem.domain.policy.rule.PolicyType;
import com.eventsystem.domain.policy.shared.PolicyValidationResult;
import com.eventsystem.domain.policy.shared.PurchaseContext;
import com.fasterxml.jackson.annotation.JsonCreator;

public final class NeverAllowPolicy implements IBasicPolicy {

    public static final NeverAllowPolicy INSTANCE = new NeverAllowPolicy();

    @JsonCreator
    public NeverAllowPolicy() {
    }

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