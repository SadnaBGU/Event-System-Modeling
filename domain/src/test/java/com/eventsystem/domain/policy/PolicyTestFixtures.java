package com.eventsystem.domain.policy;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.purchaserecord.EventSnapshot;
import com.eventsystem.domain.zone.ZoneId;

import java.time.LocalDate;
import java.util.List;

public final class PolicyTestFixtures {

    public static final CompanyId COMPANY_ID = new CompanyId("company-1");
    public static final CompanyId OTHER_COMPANY_ID = new CompanyId("company-2");
    public static final EventId EVENT_ID = new EventId("event-1");
    public static final EventId OTHER_EVENT_ID = new EventId("event-2");
    public static final ZoneId VIP_ZONE = new ZoneId("vip-zone");
    public static final ZoneId REGULAR_ZONE = new ZoneId("regular-zone");
    public static final ZoneId BALCONY_ZONE = new ZoneId("balcony-zone");
    public static final LocalDate PURCHASE_DATE = LocalDate.now();
    
    private PolicyTestFixtures() {
    }

    public static PurchaseContext contextWithTickets(ZoneId... zones) {
        return PurchaseContext.fromPurchaseInfo(EVENT_ID, COMPANY_ID, List.of(zones), 
                                LocalDate.now().minusYears(25));
    }

    public static PurchaseContext contextWithCode(String code, ZoneId... zones) {
        return PurchaseContext.fromPurchaseInfo(EVENT_ID, COMPANY_ID, List.of(zones), 
                                LocalDate.now().minusYears(25), code);
    }

    public static PurchaseContext contextWithBirthDate(LocalDate birthDate, ZoneId... zones) {
        return PurchaseContext.fromPurchaseInfo(EVENT_ID, COMPANY_ID, List.of(zones),  birthDate, null);
    }

    public static PurchaseContext contextForCompanyAndEvent(CompanyId companyId, EventId eventId, String code, ZoneId... zones) {
        return PurchaseContext.fromPurchaseInfo(eventId, companyId,List.of(zones) ,LocalDate.now().minusYears(25), code);
    }

    public static EventSnapshot snapshot(EventId eventId) {
        return new EventSnapshot(
                eventId.toString(),
                "Test Event",
                "Test Company",
                LocalDate.now().plusDays(30),
                "Tel Aviv"
        );
    }

    public static PurchaseContext context(
            CompanyId companyId,
            EventId eventId,
            LocalDate buyerBirthDate,
            String discountCode,
            List<ZoneId> zones
    ) {
        return new PurchaseContext( eventId, companyId, zones, buyerBirthDate, discountCode);
    }
}
