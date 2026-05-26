package com.eventsystem.domain.policy.basic;

import com.eventsystem.domain.domainexceptions.PolicyException;
import com.eventsystem.domain.domainexceptions.PurchasePolicyException;
import com.eventsystem.domain.policy.IPolicy;
import com.eventsystem.domain.policy.PurchaseContext;

public final class CodePolicy implements IPolicy{

    private final String discountCode;

    public CodePolicy(String code) {
        if (code == null || code.isEmpty() || code.isBlank()) {
            throw new PolicyException("Chosen code cannot be null, empty or blank");
        }
        this.discountCode = code;
    }

    @Override
    public boolean validate(PurchaseContext context) {
        return discountCode.equals(context.discountCode());
    }

    @Override
    public void require(PurchaseContext context) {
        if (!validate(context)) {
            throw new PurchasePolicyException(String.format(
                "Wrong code for Event %s", context.getEventName()));
        }
    }
    
}
