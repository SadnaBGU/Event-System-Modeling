package com.eventsystem.domain.policy.composite;

import java.util.List;
import java.util.Objects;

import com.eventsystem.domain.domainexceptions.PolicyException;
import com.eventsystem.domain.policy.IPolicy;
import com.eventsystem.domain.policy.PolicyType;
import com.eventsystem.domain.policy.PolicyValidationResult;
import com.eventsystem.domain.policy.PurchaseContext;

public final class AndPolicy implements ICompositePolicy {

    private final List<IPolicy> policies;

    @Override
    public PolicyType type() {
        return PolicyType.AND;
    }

    public AndPolicy(List<IPolicy> policies) {
        if (policies == null || policies.isEmpty()) {
            throw new PolicyException("Policies cannot be null or empty");
        }

        if (policies.stream().anyMatch(Objects::isNull)) {
            throw new PolicyException("AndPolicy cannot contain null policies");
        }

        this.policies = List.copyOf(policies);
    }

    @Override
    public PolicyValidationResult evaluate(PurchaseContext context) {
        for (IPolicy policy : policies) {
            PolicyValidationResult result = policy.evaluate(context);
            if (!result.isSuccess()) {
                return result;
            }
        }

        return PolicyValidationResult.success();
    }

    @Override
    public List<IPolicy> children() {
        return policies;
    }
}