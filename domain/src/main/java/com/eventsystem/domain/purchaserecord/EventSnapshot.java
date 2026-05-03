package com.eventsystem.domain.purchaserecord;

import java.time.LocalDate;

public record EventSnapshot(String eventName, String companyName, LocalDate eventDate, String location) {}