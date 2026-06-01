package com.eventsystem.domain.policy.composite;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.eventsystem.domain.domainexceptions.PolicyException;
import com.eventsystem.domain.policy.IPolicy;
import com.eventsystem.domain.policy.PolicyType;
import com.eventsystem.domain.policy.PolicyValidationResult;
import com.eventsystem.domain.policy.PurchaseContext;

public final class OrPolicy implements ICompositePolicy {

    private final List<IPolicy> policies;

    @Override
    public PolicyType type() {
        return PolicyType.OR;
    }

    public OrPolicy(List<IPolicy> policies) {
        if (policies == null || policies.isEmpty()) {
            throw new PolicyException("Policies cannot be null or empty");
        }

        if (policies.stream().anyMatch(Objects::isNull)) {
            throw new PolicyException("OrPolicy cannot contain null policies");
        }

        this.policies = List.copyOf(policies);
    }

    @Override
    public PolicyValidationResult evaluate(PurchaseContext context) {
        List<String> failureReasons = new ArrayList<>();

        for (IPolicy policy : policies) {
            PolicyValidationResult result = policy.evaluate(context);

            if (result.isSuccess()) {
                return PolicyValidationResult.success();
            }

            failureReasons.add(result.reason());
        }

        return PolicyValidationResult.failure(
                "At least one purchase condition must be satisfied. Failed reasons: "
                        + String.join("; ", failureReasons)
        );
    }

    @Override
    public List<IPolicy> children() {
        return policies;
    }
}