package com.eventsystem.application.policy.policybuilder;

import java.util.List;

public record PolicyRuleCommand(
        String type,
        Integer value,
        String code,
        String date,
        List<String> zoneIds,
        List<PolicyRuleCommand> children
) {
}