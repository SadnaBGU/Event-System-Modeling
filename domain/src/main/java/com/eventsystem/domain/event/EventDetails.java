package com.eventsystem.domain.event;

import java.util.List;
import java.util.Objects;
import java.time.LocalDateTime;


public record EventDetails( String name, List<LocalDateTime> dates, String category,
                            String location, String description ) {
    public EventDetails {
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
    }

    private static boolean isValidStringArg(String arg) {
        return !(arg == null || arg.isBlank());
    }
}