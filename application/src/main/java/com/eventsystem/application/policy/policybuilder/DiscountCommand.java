package com.eventsystem.application.policy.policybuilder;

import java.math.BigDecimal;

public record DiscountCommand(
        String name,
        BigDecimal percent,
        PolicyRuleCommand rule
) {
}