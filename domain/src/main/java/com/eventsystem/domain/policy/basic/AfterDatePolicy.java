package com.eventsystem.domain.policy.basic;

import java.time.LocalDate;
import java.util.Objects;

import com.eventsystem.domain.domainexceptions.PurchasePolicyException;
import com.eventsystem.domain.policy.IPolicy;
import com.eventsystem.domain.policy.PurchaseContext;

public final class AfterDatePolicy implements IPolicy{

    private final LocalDate deadlineDate;

    public AfterDatePolicy(LocalDate deadlineDate) {
        this.deadlineDate = Objects.requireNonNull(deadlineDate, "Chosen date cannot be null");
    }

    @Override
    public boolean validate(PurchaseContext context) {
        return LocalDate.now().isAfter(deadlineDate);
    }

    @Override
    public void require(PurchaseContext context) {
        if (!validate(context)) {
            throw new PurchasePolicyException(String.format(
                "Cannot Purchase tickets to Event %s before Date %s", context.eventId().toString(), deadlineDate.toString()
            ));
        }
    }

}
