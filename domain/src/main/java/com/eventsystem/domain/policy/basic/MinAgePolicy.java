package com.eventsystem.domain.policy.basic;

import java.time.LocalDate;
import java.time.Period;

import com.eventsystem.domain.domainexceptions.PurchasePolicyException;
import com.eventsystem.domain.policy.IPolicy;
import com.eventsystem.domain.policy.PurchaseContext;
import com.eventsystem.domain.domainexceptions.PolicyException;


public final class MinAgePolicy implements IPolicy{
    
    private final int minAge;

    public MinAgePolicy(int minAge) {
        if (minAge < 0) {
            throw new PolicyException("Minimum age must not be negative");
        }
        this.minAge = minAge;
    }

    @Override
    public boolean validate(PurchaseContext context) {
        return Period.between(context.buyerBirthDate(), LocalDate.now()).getYears() >= minAge;
    }

    @Override
    public void require(PurchaseContext context) {
        if (!validate(context)) {
            throw new PurchasePolicyException(String.format(
                "Buyer must be over age %d to buy tickets for %s", minAge, context.getEventName()
            ));
        }

    }
    
}
