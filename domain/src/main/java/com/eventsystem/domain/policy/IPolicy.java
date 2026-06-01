package com.eventsystem.domain.policy;

import com.eventsystem.domain.domainexceptions.PurchasePolicyException;

public interface IPolicy {

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
