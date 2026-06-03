package com.eventsystem.domain.policy.rule.basic;

import com.eventsystem.domain.domainexceptions.PolicyException;
import com.eventsystem.domain.policy.rule.PolicyType;
import com.eventsystem.domain.policy.shared.PolicyValidationResult;
import com.eventsystem.domain.policy.shared.PurchaseContext;

public final class CodePolicy implements IBasicPolicy {

    private final String discountCode;

    @Override
    public PolicyType type() {
        return PolicyType.CODE;
    }

    public CodePolicy(String code) {
        if (code == null || code.isBlank()) {
            throw new PolicyException("Chosen code cannot be null, empty or blank");
        }
        this.discountCode = code;
    }

    public String requiredCode() {
        return discountCode;
    }

    @Override
    public PolicyValidationResult evaluate(PurchaseContext context) {
        if (discountCode.equals(context.discountCode())) {
            return PolicyValidationResult.success();
        }

        return PolicyValidationResult.failure("Invalid coupon code");
    }
}