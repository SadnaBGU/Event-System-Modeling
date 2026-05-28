package com.eventsystem.domain.policy;


public interface IPolicy {

    //check without exceptions (for discounts)
    public boolean validate(PurchaseContext context);

    //if not valid- throw(for purchase policies)
    public void require(PurchaseContext context);

    public boolean isValidPolicy();

}
