package com.eventsystem.application.policy.policybuilder;

import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.policy.Discount;
import com.eventsystem.domain.policy.IPolicy;
import com.eventsystem.domain.policy.PolicyConflictDetector;
import com.eventsystem.domain.policy.PolicyScope;
import com.eventsystem.domain.policy.basic.AfterDatePolicy;
import com.eventsystem.domain.policy.basic.AlwaysTruePolicy;
import com.eventsystem.domain.policy.basic.CodePolicy;
import com.eventsystem.domain.policy.basic.MaxTicketPolicy;
import com.eventsystem.domain.policy.basic.MinAgePolicy;
import com.eventsystem.domain.policy.basic.MinTicketPolicy;
import com.eventsystem.domain.policy.basic.NeverAllowPolicy;
import com.eventsystem.domain.policy.basic.UntilDatePolicy;
import com.eventsystem.domain.policy.composite.AndPolicy;
import com.eventsystem.domain.policy.composite.OrPolicy;
import com.eventsystem.domain.policy.composite.ZoneSpecificPolicy;
import com.eventsystem.domain.zone.ZoneId;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class PolicyCommandAssembler {

    public IPolicy toPolicy(PolicyRuleCommand command) {
        IPolicy policy = buildPolicy(command);

        PolicyConflictDetector.requireValidPolicy(policy);

        return policy;
    }

    public Discount toDiscount(DiscountCommand command) {
        Objects.requireNonNull(command, "discount command must not be null");

        IPolicy rule = command.rule() == null
                ? AlwaysTruePolicy.INSTANCE
                : toPolicy(command.rule());

        boolean isVisible = parseVisibility(command.visibility());
        LocalDate endDate = parseEndDate(command.endDate());

        return new Discount(
                command.name(),
                command.percent(),
                rule,
                isVisible,
                endDate
        );
    }

    public PolicyScope toScope(PolicyScopeCommand command) {
        Objects.requireNonNull(command, "policy scope command must not be null");

        Set<EventId> eventIds = command.eventIds() == null
                ? Set.of()
                : command.eventIds()
                        .stream()
                        .map(EventId::new)
                        .collect(Collectors.toUnmodifiableSet());

        return new PolicyScope(command.companyWide(), eventIds);
    }

    private IPolicy buildPolicy(PolicyRuleCommand command) {
        Objects.requireNonNull(command, "policy rule command must not be null");

        String type = command.type();
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("policy rule type is required");
        }

        return switch (type.toUpperCase()) {
            case "MIN_AGE" -> new MinAgePolicy(requireValue(command, "MIN_AGE"));

            case "MIN_TICKETS" -> new MinTicketPolicy(requireValue(command, "MIN_TICKETS"));

            case "MAX_TICKETS" -> new MaxTicketPolicy(requireValue(command, "MAX_TICKETS"));

            case "BEFORE_DATE" -> new UntilDatePolicy(
                    LocalDate.parse(requireDate(command, "BEFORE_DATE"))
            );

            case "AFTER_DATE" -> new AfterDatePolicy(
                    LocalDate.parse(requireDate(command, "AFTER_DATE"))
            );

            case "CODE" -> new CodePolicy(requireCode(command));

            case "AND" -> new AndPolicy(buildChildren(command));

            case "OR" -> new OrPolicy(buildChildren(command));

            case "ZONE_RESTRICT" -> new ZoneSpecificPolicy(
                    toZoneIds(command),
                    new AndPolicy(buildChildren(command)),
                    true
            );

            case "ZONE_REQUIRE" -> new ZoneSpecificPolicy(
                    toZoneIds(command),
                    new AndPolicy(buildChildren(command)),
                    false
            );

            case "ALLOW_ALL" -> AlwaysTruePolicy.INSTANCE;

            case "NEVER_ALLOW" -> NeverAllowPolicy.INSTANCE;

            default -> throw new IllegalArgumentException(
                    "Unsupported policy rule type: " + command.type()
            );
        };
    }

    private List<IPolicy> buildChildren(PolicyRuleCommand command) {
        if (command.children() == null || command.children().isEmpty()) {
            throw new IllegalArgumentException(command.type() + " policy requires children");
        }

        return command.children()
                .stream()
                .map(this::buildPolicy)
                .toList();
    }

    private Set<ZoneId> toZoneIds(PolicyRuleCommand command) {
        if (command.zoneIds() == null || command.zoneIds().isEmpty()) {
            throw new IllegalArgumentException(command.type() + " policy requires zoneIds");
        }

        return command.zoneIds()
                .stream()
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

    private boolean parseVisibility(String visibility) {
        if (visibility == null || visibility.isBlank()) {
            return true;
        }

        return switch (visibility.trim().toUpperCase()) {
            case "VISIBLE" -> true;
            case "HIDDEN" -> false;
            default -> throw new IllegalArgumentException(
                "Discount visibility must be either VISIBLE or HIDDEN"
            );
        };
    }

    private LocalDate parseEndDate(String endDate) {
        if (endDate == null || endDate.isBlank()) {
            return null;
        }

        try {
            return LocalDate.parse(endDate.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Discount endDate must be in ISO format: yyyy-MM-dd"
            );
        }
    }
}