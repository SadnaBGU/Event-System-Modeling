package com.eventsystem.domain.policy;

import com.eventsystem.domain.event.EventId;
import java.io.Serializable;
import java.util.Objects;

/**
 * PurchasePolicy Domain Object
 * Defines purchase restrictions for an event (max tickets per buyer, age restrictions, etc.)
 */
public class PurchasePolicy implements Serializable {
    private final EventId eventId;
    private final int maxTicketsPerBuyer;
    private final String ageRestrictions; // nullable
    private final String reservedSeatLimits; // nullable

    public PurchasePolicy(EventId eventId, int maxTicketsPerBuyer, String ageRestrictions, String reservedSeatLimits) {
        if (maxTicketsPerBuyer <= 0) {
            throw new IllegalArgumentException("Max tickets per buyer must be positive");
        }
        this.eventId = Objects.requireNonNull(eventId);
        this.maxTicketsPerBuyer = maxTicketsPerBuyer;
        this.ageRestrictions = ageRestrictions;
        this.reservedSeatLimits = reservedSeatLimits;
    }

    public EventId eventId() {
        return eventId;
    }

    public int maxTicketsPerBuyer() {
        return maxTicketsPerBuyer;
    }

    public String ageRestrictions() {
        return ageRestrictions;
    }

    public String reservedSeatLimits() {
        return reservedSeatLimits;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PurchasePolicy that = (PurchasePolicy) o;
        return maxTicketsPerBuyer == that.maxTicketsPerBuyer &&
                Objects.equals(eventId, that.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, maxTicketsPerBuyer);
    }

    @Override
    public String toString() {
        return "PurchasePolicy{" +
                "eventId=" + eventId +
                ", maxTicketsPerBuyer=" + maxTicketsPerBuyer +
                '}';
    }
}
