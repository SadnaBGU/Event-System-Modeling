package com.eventsystem.domain.policy.rule.basic;

import java.time.LocalDate;
import java.util.Objects;

import com.eventsystem.domain.policy.rule.PolicyType;
import com.eventsystem.domain.policy.shared.PolicyValidationResult;
import com.eventsystem.domain.policy.shared.PurchaseContext;

public final class AfterDatePolicy implements IBasicPolicy {

    @Override
    public PolicyType type() {
        return PolicyType.AFTER_DATE;
    }

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