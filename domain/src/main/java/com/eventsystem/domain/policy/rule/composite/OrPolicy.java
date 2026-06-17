package com.eventsystem.domain.policy.rule.composite;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.eventsystem.domain.domainexceptions.PolicyException;
import com.eventsystem.domain.policy.rule.IPolicy;
import com.eventsystem.domain.policy.rule.PolicyType;
import com.eventsystem.domain.policy.shared.PolicyValidationResult;
import com.eventsystem.domain.policy.shared.PurchaseContext;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class OrPolicy implements ICompositePolicy {
    @JsonProperty("children")
    private final List<IPolicy> policies;

    @Override
    public PolicyType type() {
        return PolicyType.OR;
    }
    @JsonCreator
    public OrPolicy(@JsonProperty("children") List<IPolicy> policies) {
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