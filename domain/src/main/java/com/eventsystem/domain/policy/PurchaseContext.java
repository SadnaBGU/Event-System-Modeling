package com.eventsystem.domain.policy;

import com.eventsystem.domain.purchaserecord.EventSnapshot;
import com.eventsystem.domain.zone.ZoneId;
import com.eventsystem.domain.company.CompanyId;


import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

//TODO- ADD anything more that is relevant
public record PurchaseContext(LocalDate buyerBirthDate, EventSnapshot eventSnapshot, CompanyId companyId,
                                String discountCode, List<ZoneId> zonesOfEachEventTicket ) {

    public PurchaseContext {
        Objects.requireNonNull(eventSnapshot, "eventSnapshot must not be null");
        Objects.requireNonNull(buyerBirthDate, "eventSnapshot must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(zonesOfEachEventTicket, "zonesOfEachEventTicket must not be null");

        zonesOfEachEventTicket = List.copyOf(zonesOfEachEventTicket);
    }

    public String getEventName() {
        return eventSnapshot.eventName();
    }

    public String getEventId() {
        return eventSnapshot.eventId();
    }

    public String getCompanyName() {
        return eventSnapshot.companyName();
    }
}




