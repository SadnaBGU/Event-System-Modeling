package com.eventsystem.domain.policy.rule.basic;

import com.eventsystem.domain.policy.rule.PolicyType;
import com.eventsystem.domain.policy.shared.PolicyValidationResult;
import com.eventsystem.domain.policy.shared.PurchaseContext;
import com.fasterxml.jackson.annotation.JsonCreator;

public final class AlwaysTruePolicy implements IBasicPolicy {

    public static final AlwaysTruePolicy INSTANCE = new AlwaysTruePolicy();

    @JsonCreator
    public AlwaysTruePolicy() {
    }

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