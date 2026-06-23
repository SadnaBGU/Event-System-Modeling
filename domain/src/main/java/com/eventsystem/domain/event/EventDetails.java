package com.eventsystem.domain.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Embeddable
public class EventDetails {

    @Column(name = "event_name")
    private String name;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "event_dates", joinColumns = @JoinColumn(name = "event_id"))
    @Column(name = "event_date")
    private List<LocalDateTime> dates;

    @Column(name = "category")
    private String category;

    @Column(name = "location")
    private String location;

    @Column(length = 1000)
    private String description;

    protected EventDetails() {
    }

    public EventDetails(String name, List<LocalDateTime> dates, String category, String location, String description) {
        if (!isValidStringArg(name)) {
            throw new IllegalArgumentException("event name must not be blank");
        }

        Objects.requireNonNull(dates, "event date must not be null");
        if (dates.isEmpty()) {
            throw new IllegalArgumentException("date must be chosen for the event");
        }

        for (LocalDateTime date : dates) {
            Objects.requireNonNull(date, "event date must not be null");
        }
        
        if (!isValidStringArg(category)) {
            throw new IllegalArgumentException("category must not be blank");
        }

        if (!isValidStringArg(location)) {
            throw new IllegalArgumentException("location must not be blank");
        }

        if (!isValidStringArg(description)) {
            throw new IllegalArgumentException("description must not be blank");
        }

        this.name = name;
        // Mutable defensive copy: Hibernate must be able to clear/replace this
        // @ElementCollection on merge (List.copyOf is immutable and would throw).
        this.dates = new ArrayList<>(dates);
        this.category = category;
        this.location = location;
        this.description = description;
    }

    private static boolean isValidStringArg(String arg) {
        return !(arg == null || arg.isBlank());
    }

    public String name() { return name; }
    public List<LocalDateTime> dates() { return dates; }
    public String category() { return category; }
    public String location() { return location; }
    public String description() { return description; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EventDetails that = (EventDetails) o;
        return Objects.equals(name, that.name) &&
               Objects.equals(dates, that.dates) &&
               Objects.equals(category, that.category) &&
               Objects.equals(location, that.location) &&
               Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, dates, category, location, description);
    }
}