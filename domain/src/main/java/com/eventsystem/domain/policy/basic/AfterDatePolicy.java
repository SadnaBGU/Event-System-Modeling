package com.eventsystem.domain.policy.basic;

import java.time.LocalDate;
import java.util.Objects;

import com.eventsystem.domain.policy.PolicyValidationResult;
import com.eventsystem.domain.policy.PurchaseContext;

public final class AfterDatePolicy implements IBasicPolicy {

    private final LocalDate deadlineDate;

    public AfterDatePolicy(LocalDate deadlineDate) {
        this.deadlineDate = Objects.requireNonNull(deadlineDate, "Chosen date cannot be null");
    }

    public LocalDate deadlineDate() {
        return deadlineDate;
    }

    @Override
    public PolicyValidationResult evaluate(PurchaseContext context) {
        if (context.purchaseDate().isAfter(deadlineDate)) {
            return PolicyValidationResult.success();
        }

        return PolicyValidationResult.failure(String.format(
                "Cannot purchase tickets to event %s before date %s",
                context.eventId(),
                deadlineDate
        ));
    }
}