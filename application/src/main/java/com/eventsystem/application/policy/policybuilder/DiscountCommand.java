package com.eventsystem.application.policy.policybuilder;

import java.math.BigDecimal;

public record DiscountCommand(
        String name,
        BigDecimal percent,
        PolicyRuleCommand rule,
        String visibility,
        String endDate
) {
    public DiscountCommand(String name, BigDecimal percent, PolicyRuleCommand rule) {
        this(name, percent, rule, "VISIBLE", null);
    }
}