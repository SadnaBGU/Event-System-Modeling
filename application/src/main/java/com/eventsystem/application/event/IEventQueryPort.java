package com.eventsystem.application.event;

import com.eventsystem.domain.purchaserecord.EventSnapshot;

import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.company.CompanyId;


public interface IEventQueryPort {
    EventSnapshot getEventSnapshot(String eventId);

    CompanyId companyOfEvent(EventId eventId);
}