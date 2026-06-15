package com.eventsystem.domain.policy.rule.basic;

import java.time.LocalDate;
import java.util.Objects;

import com.eventsystem.domain.policy.rule.PolicyType;
import com.eventsystem.domain.policy.shared.PolicyValidationResult;
import com.eventsystem.domain.policy.shared.PurchaseContext;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class UntilDatePolicy implements IBasicPolicy {
    @JsonProperty("deadlineDate")
    private final LocalDate deadlineDate;

    @Override
    public PolicyType type() {
        return PolicyType.UNTIL_DATE;
    }

    @JsonCreator
    public UntilDatePolicy(@JsonProperty("deadlineDate") LocalDate deadlineDate) {
        this.deadlineDate = Objects.requireNonNull(deadlineDate, "Chosen date cannot be null");
    }

    public LocalDate deadlineDate() {
        return deadlineDate;
    }

    @Override
    public PolicyValidationResult evaluate(PurchaseContext context) {
        if (!context.purchaseDate().isAfter(deadlineDate)) {
            return PolicyValidationResult.success();
        }

        return PolicyValidationResult.failure(String.format(
                "Cannot purchase tickets to event %s after date %s",
                context.eventId(),
                deadlineDate
        ));
    }
}