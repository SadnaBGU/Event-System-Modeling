package com.eventsystem.domain.event;

public enum EventStatus {
    DRAFT,
    PUBLISHED,
    SOLD_OUT,
    CANCELLED
    //TODO - check if Cancelled == Event Ended/Over, if NOT, Add ENDED/OVER status
}