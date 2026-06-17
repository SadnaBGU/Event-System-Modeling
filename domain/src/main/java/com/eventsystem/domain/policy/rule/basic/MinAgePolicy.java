package com.eventsystem.domain.policy.rule.basic;

import java.time.Period;

import com.eventsystem.domain.domainexceptions.PolicyException;
import com.eventsystem.domain.policy.rule.PolicyType;
import com.eventsystem.domain.policy.shared.PolicyValidationResult;
import com.eventsystem.domain.policy.shared.PurchaseContext;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class MinAgePolicy implements IBasicPolicy {
    @JsonProperty("minAge")
    private final int minAge;

    @Override
    public PolicyType type() {
        return PolicyType.MIN_AGE;
    }

    @JsonCreator
    public MinAgePolicy(@JsonProperty("minAge") int minAge) {
        if (minAge < 0) {
            throw new PolicyException("Minimum age must not be negative");
        }
        this.minAge = minAge;
    }

    public int minAge() {
        return minAge;
    }

    @Override
    public PolicyValidationResult evaluate(PurchaseContext context) {
        int age = Period.between(context.buyerBirthDate(), context.purchaseDate()).getYears();

        if (age >= minAge) {
            return PolicyValidationResult.success();
        }

        return PolicyValidationResult.failure(String.format(
                "Buyer must be over age %d to buy tickets for %s",
                minAge,
                context.eventId()
        ));
    }
}