package com.eventsystem.domain.policy.basic;

import com.eventsystem.domain.domainexceptions.PolicyException;
import com.eventsystem.domain.policy.PolicyValidationResult;
import com.eventsystem.domain.policy.PurchaseContext;

public final class CodePolicy implements IBasicPolicy {

    private final String discountCode;

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