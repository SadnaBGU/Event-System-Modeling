package com.eventsystem.domain.purchaserecord;

import java.time.LocalDate;

public record EventSnapshot(String eventId, String eventName, String companyName, LocalDate eventDate, String location) {}