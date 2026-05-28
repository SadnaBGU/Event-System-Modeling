package com.eventsystem.domain.policy.composite;

import java.util.List;
import java.util.Objects;

import com.eventsystem.domain.domainexceptions.PolicyException;
import com.eventsystem.domain.policy.IPolicy;
import com.eventsystem.domain.policy.PurchaseContext;


public final class AndPolicy implements ICompositePolicy {
    
    private final List<IPolicy> policies;

    public AndPolicy(List<IPolicy> policies) {
        if (policies == null || policies.isEmpty())
        {
            throw new PolicyException("Policies cannot be null or empty");
        }

        if (policies.stream().anyMatch(Objects::isNull)) {
            throw new PolicyException("AndPolicy cannot contain null policies");
        }

        this.policies = List.copyOf(policies);
    }

    @Override
    public boolean validate(PurchaseContext context) {
        return policies.stream().allMatch(policy -> policy.validate(context));
    }

    @Override
    public void require(PurchaseContext context) {
        if (policies == null || policies.isEmpty())
            return;
        for (IPolicy policy : policies) {
            policy.require(context);
        }
    }

    @Override
    public List<IPolicy> children() {
        return policies;
    }
}
