package com.eventsystem.domain.policy.basic;
import com.eventsystem.domain.policy.PurchaseContext;
import com.eventsystem.domain.domainexceptions.PolicyException;
import com.eventsystem.domain.policy.IPolicy;

public enum NeverAllowPolicy
 implements IPolicy {
    INSTANCE;

    @Override
    public boolean validate(PurchaseContext context) {
        return false;
    }

    @Override
    public void require(PurchaseContext context) {
        throw new PolicyException("Purchase policy restrics current purchase");
    }
}