package com.eventsystem.domain.purchaserecord;

import java.time.LocalDate;
import jakarta.persistence.Embeddable;

@Embeddable
public record EventSnapshot(
    String eventId,
    String eventName,
    String companyName,
    LocalDate eventDate,
    String location
) {}