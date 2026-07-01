package com.eventsystem.domain.policy.rule.basic;

import com.eventsystem.domain.domainexceptions.PolicyException;
import com.eventsystem.domain.order.BuyerType;
import com.eventsystem.domain.policy.rule.PolicyType;
import com.eventsystem.domain.policy.shared.PolicyValidationResult;
import com.eventsystem.domain.policy.shared.PurchaseContext;
import com.fasterxml.jackson.annotation.JsonCreator;

public final class RequireMemberPolicy implements IBasicPolicy {

    public static final RequireMemberPolicy INSTANCE = new RequireMemberPolicy();

    @JsonCreator
    public RequireMemberPolicy() {
    }

    @Override
    public PolicyType type() {
        return PolicyType.REQUIRE_MEMBER;
    }

    @Override
    public PolicyValidationResult evaluate(PurchaseContext context) {
        return context.buyerType() == BuyerType.GUEST ?
            PolicyValidationResult.failure("Purchase restricted to signed members only") :
            PolicyValidationResult.success();
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