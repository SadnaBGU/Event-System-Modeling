package com.eventsystem.application.policy.policybuilder;

import java.util.List;

public record DiscountPolicyCommand(
        String actorId,
        String companyId,
        String policyName,
        PolicyScopeCommand scope,
        List<DiscountCommand> discounts,
        boolean stackable,
        boolean activate
) {
}