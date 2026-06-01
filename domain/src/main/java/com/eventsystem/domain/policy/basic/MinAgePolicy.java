package com.eventsystem.domain.policy.basic;

import java.time.LocalDate;
import java.time.Period;

import com.eventsystem.domain.domainexceptions.PolicyException;
import com.eventsystem.domain.policy.PolicyValidationResult;
import com.eventsystem.domain.policy.PurchaseContext;

public final class MinAgePolicy implements IBasicPolicy {

    private final int minAge;

    public MinAgePolicy(int minAge) {
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
        int age = Period.between(context.buyerBirthDate(), LocalDate.now()).getYears();

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