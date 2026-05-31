package com.eventsystem.application.policy.policybuilder;

import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.policy.IPolicy;
import com.eventsystem.domain.policy.PolicyBuilder;
import com.eventsystem.domain.policy.PolicyScope;
import com.eventsystem.domain.policy.composite.AndPolicy;
import com.eventsystem.domain.policy.composite.OrPolicy;
import com.eventsystem.domain.policy.composite.ZoneSpecificPolicy;
import com.eventsystem.domain.policy.basic.*;
import com.eventsystem.domain.zone.ZoneId;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class PolicyCommandAssembler {

    public IPolicy toPolicy(PolicyRuleCommand command) {
        Objects.requireNonNull(command, "policy rule command must not be null");

        String type = command.type();
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("policy rule type is required");
        }

        return switch (type.toUpperCase()) {
            case "MIN_AGE" -> new MinAgePolicy(requireValue(command, "MIN_AGE"));
            case "MIN_TICKETS" -> new MinTicketPolicy(requireValue(command, "MIN_TICKETS"));
            case "MAX_TICKETS" -> new MaxTicketPolicy(requireValue(command, "MAX_TICKETS"));
            case "BEFORE_DATE" -> new UntilDatePolicy(LocalDate.parse(requireDate(command, "BEFORE_DATE")));
            case "AFTER_DATE" -> new AfterDatePolicy(LocalDate.parse(requireDate(command, "AFTER_DATE")));
            case "CODE" -> new CodePolicy(requireCode(command));

            case "AND" -> new AndPolicy(toChildren(command));
            case "OR" -> new OrPolicy(toChildren(command));

            case "ZONE_RESTRICT" -> new ZoneSpecificPolicy(
                    toZoneIds(command),
                    new AndPolicy(toChildren(command)),
                    true
            );

            case "ZONE_REQUIRE" -> new ZoneSpecificPolicy(
                    toZoneIds(command),
                    new AndPolicy(toChildren(command)),
                    false
            );

            case "ALLOW_ALL" -> AlwaysTruePolicy.INSTANCE;
            case "NEVER_ALLOW" -> NeverAllowPolicy.INSTANCE;

            default -> throw new IllegalArgumentException("Unsupported policy rule type: " + command.type());
        };
    }

    public PolicyScope toScope(PolicyScopeCommand command) {
        Objects.requireNonNull(command, "policy scope command must not be null");

        Set<EventId> eventIds = command.eventIds() == null
                ? Set.of()
                : command.eventIds().stream()
                    .map(EventId::new)
                    .collect(Collectors.toUnmodifiableSet());

        return new PolicyScope(command.companyWide(), eventIds);
    }

    private List<IPolicy> toChildren(PolicyRuleCommand command) {
        if (command.children() == null || command.children().isEmpty()) {
            throw new IllegalArgumentException(command.type() + " policy requires children");
        }

        return command.children().stream()
                .map(this::toPolicy)
                .toList();
    }

    private Set<ZoneId> toZoneIds(PolicyRuleCommand command) {
        if (command.zoneIds() == null || command.zoneIds().isEmpty()) {
            throw new IllegalArgumentException(command.type() + " policy requires zoneIds");
        }

        return command.zoneIds().stream()
                .map(ZoneId::new)
                .collect(Collectors.toUnmodifiableSet());
    }

    private int requireValue(PolicyRuleCommand command, String ruleType) {
        if (command.value() == null) {
            throw new IllegalArgumentException(ruleType + " policy requires value");
        }
        return command.value();
    }

    private String requireDate(PolicyRuleCommand command, String ruleType) {
        if (command.date() == null || command.date().isBlank()) {
            throw new IllegalArgumentException(ruleType + " policy requires date");
        }
        return command.date();
    }

    private String requireCode(PolicyRuleCommand command) {
        if (command.code() == null || command.code().isBlank()) {
            throw new IllegalArgumentException("CODE policy requires code");
        }
        return command.code();
    }
}