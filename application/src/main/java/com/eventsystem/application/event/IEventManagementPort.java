package com.eventsystem.application.event;

import java.util.List;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.event.SalesMethod;
import com.eventsystem.domain.zone.ZoneId;
import com.eventsystem.domain.order.OrderItem;


public interface IEventManagementPort {
    
    boolean isEventByCompany(EventId eventId, CompanyId companyId);

    List<EventId> allEventsOfCompany(CompanyId companyId);

    void setSalesMethod(MemberId actorId, EventId eventId, SalesMethod salesMethod);

    CompanyId companyOfEvent(EventId eventId);

    List<ZoneId> getZonesOfTicketsForEvent(EventId eventId, List<OrderItem> items);

    boolean isZoneInEvent(EventId eventId, ZoneId zoneId);

}
