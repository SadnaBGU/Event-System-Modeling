package com.eventsystem.domain.policy.rule;

import com.eventsystem.domain.domainexceptions.PurchasePolicyException;
import com.eventsystem.domain.policy.shared.PolicyValidationResult;
import com.eventsystem.domain.policy.shared.PurchaseContext;

public interface IPolicy {

    default PolicyType type() {
        return PolicyType.UNKNOWN;
    }
    
    PolicyValidationResult evaluate(PurchaseContext context);

    //check without exceptions (for discounts)
    default boolean validate(PurchaseContext context) {
        return evaluate(context).isSuccess();
    }

    //if not valid- throw(for purchase policies)
    default void require(PurchaseContext context) {
        PolicyValidationResult result = evaluate(context);
        if (!result.isSuccess()) {
            throw new PurchasePolicyException(result.reason());
        }
    }

    boolean isValidPolicy();

    boolean isComposite();

}
