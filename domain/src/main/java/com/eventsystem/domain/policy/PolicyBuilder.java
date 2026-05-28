package com.eventsystem.domain.policy;

import com.eventsystem.domain.zone.ZoneId;
import com.eventsystem.domain.policy.basic.*;
import com.eventsystem.domain.policy.composite.*;


import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class PolicyBuilder {

    private final List<IPolicy> policies = new ArrayList<>();

    private PolicyBuilder() {
    }

    public static PolicyBuilder start() {
        return new PolicyBuilder();
    }

    public PolicyBuilder minAge(int minAge) {
        policies.add(new MinAgePolicy(minAge));
        return this;
    }

    public PolicyBuilder minTickets(int minTickets) {
        policies.add(new MinTicketPolicy(minTickets));
        return this;
    }

    public PolicyBuilder maxTickets(int maxTickets) {
        policies.add(new MaxTicketPolicy(maxTickets));
        return this;
    }

    public PolicyBuilder beforeDate(LocalDate date) {
        policies.add(new UntilDatePolicy(date));
        return this;
    }

    public PolicyBuilder afterDate(LocalDate date) {
        policies.add(new AfterDatePolicy(date));
        return this;
    }

    public PolicyBuilder code(String code) {
        policies.add(new CodePolicy(code));
        return this;
    }

    public PolicyBuilder add(IPolicy policy) {
        policies.add(Objects.requireNonNull(policy, "policy must not be null"));
        return this;
    }

    public IPolicy build() {
        if (policies.isEmpty()) {
            return AlwaysTruePolicy.INSTANCE;
        }

        if (policies.size() == 1) {
            return policies.get(0);
        }

        return new AndPolicy(policies);
    }

    public static IPolicy and(IPolicy... policies) {
        return new AndPolicy(List.of(policies));
    }

    public static IPolicy or(IPolicy... policies) {
        return new OrPolicy(List.of(policies));
    }

    public static IPolicy PolicyRestrictToZones(Set<ZoneId> zoneIds, IPolicy policy) {
        return new ZoneSpecificPolicy(zoneIds, policy, true);
    }

    public static IPolicy PolicyRequireFromZones(Set<ZoneId> zoneIds, IPolicy policy) {
        return new ZoneSpecificPolicy(zoneIds, policy, false);
    }

    public static IPolicy PolicyRestrictToZones(Set<ZoneId> zoneIds, List<IPolicy> policies) {
        return new ZoneSpecificPolicy(zoneIds, new AndPolicy(policies), true);
    }

    public static IPolicy PolicyRequireFromZones(Set<ZoneId> zoneIds, List<IPolicy> policies) {
        return new ZoneSpecificPolicy(zoneIds, new AndPolicy(policies), false);
    }
}