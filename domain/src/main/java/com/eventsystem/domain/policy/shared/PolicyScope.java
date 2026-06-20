package com.eventsystem.domain.policy.shared;

import com.eventsystem.domain.event.EventId;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Objects;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PolicyScope(boolean companyWide, Set<EventId> eventIds) {

    public PolicyScope {
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
    @JsonIgnore
    public Set<EventId> getEventsInScope() {
        return Set.copyOf(eventIds);
    }

    public static PolicyScope companyWideScope() {
        return new PolicyScope(true, Set.of());
    }

    public static PolicyScope forEvents(Set<EventId> eventIds) {
        return new PolicyScope(false, eventIds);
    }

    public static PolicyScope forSingleEvent(EventId eventId) {
        return new PolicyScope(false, Set.of(eventId));
    }

    public static PolicyScope clearScope() {
        return new PolicyScope(false, Set.of());
    }

    public boolean appliesTo(EventId eventId) {
        return isCompanyWide() || isListedIn(eventId);
    }

    public boolean isListedIn(EventId eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        return eventIds.contains(eventId);
    }

    public boolean isCompanyWide() {
        return companyWide;
    }
    @JsonIgnore
    public boolean isRelatedToEvents() {
        return !eventIds.isEmpty();
    }
    @JsonIgnore
    public boolean isScopedToEventsOrCompany() {
        return  companyWide || !eventIds.isEmpty();
    }

    public boolean isForSingleEvent() {
        return !companyWide && eventIds.size() == 1;
    }
    
}