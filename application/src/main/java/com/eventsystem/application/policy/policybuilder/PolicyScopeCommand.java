package com.eventsystem.application.policy.policybuilder;

import java.util.Set;

public record PolicyScopeCommand(
        boolean companyWide,
        Set<String> eventIds
) {
}