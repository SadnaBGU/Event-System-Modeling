package com.eventsystem.domain.policy;

import java.util.Objects;
import java.util.Optional;

public record PolicyValidationResult(
        boolean valid,
        String reason
) {
    public PolicyValidationResult {
        if (!valid && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("Invalid policy result must include a reason");
        }

        if (valid && reason != null && !reason.isBlank()) {
            throw new IllegalArgumentException("Valid policy result should not include a failure reason");
        }
    }

    public boolean isSuccess() {
        return valid;
    }

    public static PolicyValidationResult success() {
        return new PolicyValidationResult(true, null);
    }

    public static PolicyValidationResult failure(String reason) {
        return new PolicyValidationResult(false, Objects.requireNonNull(reason, "reason must not be null"));
    }

    public Optional<String> failureReason() {
        return Optional.ofNullable(reason);
    }
}