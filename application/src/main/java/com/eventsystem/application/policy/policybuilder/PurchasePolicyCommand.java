package com.eventsystem.application.policy.policybuilder;

public record PurchasePolicyCommand(
        String actorId,
        String companyId,
        String policyName,
        PolicyScopeCommand scope,
        PolicyRuleCommand rule,
        boolean activate
) {
}