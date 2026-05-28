package com.eventsystem.domain.policy;

import com.eventsystem.domain.event.EventId;

import java.util.Objects;
import java.util.Set;

public record DiscountPolicyScope(boolean companyWide, Set<EventId> eventIds) {

    public DiscountPolicyScope {
        Objects.requireNonNull(eventIds, "eventIds must not be null");

        // if (!companyWide && eventIds.isEmpty()) {
        //     throw new IllegalArgumentException(
        //             "Event-specific discount scope must contain at least one event"
        //     );
        // }

        if (eventIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Discount scope cannot contain null event ids");
        }

        eventIds = Set.copyOf(eventIds);
    }

    public Set<EventId> getEventsInScope() {
        return Set.copyOf(eventIds);
    }

    public static DiscountPolicyScope companyWideScope() {
        return new DiscountPolicyScope(true, Set.of());
    }

    public static DiscountPolicyScope forEvents(Set<EventId> eventIds) {
        return new DiscountPolicyScope(false, eventIds);
    }

    public static DiscountPolicyScope forSingleEvent(EventId eventId) {
        return new DiscountPolicyScope(false, Set.of(eventId));
    }

    public static DiscountPolicyScope clearScope() {
        return new DiscountPolicyScope(false, Set.of());
    }

    public boolean appliesTo(EventId eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");

        return companyWide || eventIds.contains(eventId);
    }

    public boolean isCompanyWide() {
        return companyWide;
    }

    public boolean isRelatedToEvents() {
        return !eventIds.isEmpty();
    }

    public boolean isScopedToEventsOrCompany() {
        return  companyWide || !eventIds.isEmpty();
    }
}