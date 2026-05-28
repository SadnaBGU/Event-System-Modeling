package com.eventsystem.domain.policy.basic;
import com.eventsystem.domain.policy.PurchaseContext;
import com.eventsystem.domain.policy.IPolicy;

public enum AlwaysTruePolicy implements IPolicy {
    INSTANCE;

    @Override
    public boolean validate(PurchaseContext context) {
        return true;
    }

    @Override
    public void require(PurchaseContext context) {
        // no-op
    }
}