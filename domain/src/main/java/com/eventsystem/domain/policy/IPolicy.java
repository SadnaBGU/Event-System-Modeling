package com.eventsystem.domain.policy;


public interface IPolicy {

    //check without exceptions (for discounts)
    boolean validate(PurchaseContext context);

    //if not valid- throw(for purchase policies)
    void require(PurchaseContext context);

    boolean isValidPolicy();

    boolean isComposite();

}
