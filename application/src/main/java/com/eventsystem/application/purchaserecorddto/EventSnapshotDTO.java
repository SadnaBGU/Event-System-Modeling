package com.eventsystem.application.purchaserecorddto;

import java.time.LocalDate;

import com.eventsystem.domain.purchaserecord.EventSnapshot;

public record EventSnapshotDTO(String eventId, String eventName, String companyName, LocalDate eventDate, String location) {
    

    public static EventSnapshotDTO fromDomain(EventSnapshot snapshot) {
        return new EventSnapshotDTO(
            snapshot.eventId(),
            snapshot.eventName(),
            snapshot.companyName(),
            snapshot.eventDate(),
            snapshot.location()
        );
    };
}