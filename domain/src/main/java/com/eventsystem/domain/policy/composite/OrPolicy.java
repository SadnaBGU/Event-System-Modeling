package com.eventsystem.domain.policy.composite;

import java.util.List;
import java.util.Objects;

import com.eventsystem.domain.domainexceptions.PolicyException;
import com.eventsystem.domain.policy.IPolicy;
import com.eventsystem.domain.policy.PurchaseContext;


public final class OrPolicy implements IPolicy {
    private final List<IPolicy> policies;

    public OrPolicy(List<IPolicy> policies) {
        if (policies == null || policies.isEmpty())
        {
            throw new PolicyException("Policies cannot be null or empty");
        }
        if (policies.stream().anyMatch(Objects::isNull)) {
            throw new PolicyException("OrPolicy cannot contain null policies");
        }

        this.policies = List.copyOf(policies);
    }

    @Override
    public boolean validate(PurchaseContext context) {
        return policies.stream().anyMatch(policy -> policy.validate(context));
    }

    @Override
    public void require(PurchaseContext context) {
        if (policies == null || policies.isEmpty())
        {
            return;
        }
        if (!validate(context))
        for (IPolicy policy : policies) {
            policy.require(context);
        }
    }
    
}
