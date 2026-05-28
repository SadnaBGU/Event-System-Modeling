package com.eventsystem.domain.policy;

import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.zone.ZoneId;
import com.eventsystem.domain.company.CompanyId;


import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

//TODO- ADD anything more that is relevant
public record PurchaseContext(EventId eventId, CompanyId companyId, List<ZoneId> zonesOfEachEventTicket,
                                LocalDate buyerBirthDate, String discountCode ) {

    public PurchaseContext {
        Objects.requireNonNull(eventId, "eventSnapshot must not be null");
        Objects.requireNonNull(buyerBirthDate, "buyerBirthDate must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(zonesOfEachEventTicket, "zonesOfEachEventTicket must not be null");

        zonesOfEachEventTicket = List.copyOf(zonesOfEachEventTicket);
    }

    public int ticketCount() {
        return zonesOfEachEventTicket.size();
    }

    public int ticketCountForZone(ZoneId zoneTocount) {
        int counter  = 0;
        for (ZoneId zoneId : zonesOfEachEventTicket) {
            if (zoneId.equals(zoneTocount)) {
                counter++;
            }
        }
        return counter;
    }

}




