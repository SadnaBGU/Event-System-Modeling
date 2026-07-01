package com.eventsystem.domain.policy;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.order.BuyerType;
import com.eventsystem.domain.policy.discount.Discount;
import com.eventsystem.domain.policy.shared.PurchaseContext;
import com.eventsystem.domain.policy.shared.ZonePurchaseContext;
import com.eventsystem.domain.purchaserecord.EventSnapshot;
import com.eventsystem.domain.shared.Money;
import com.eventsystem.domain.zone.ZoneId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PolicyTestFixtures {

    public static final CompanyId COMPANY_ID = new CompanyId("company-1");
    public static final CompanyId OTHER_COMPANY_ID = new CompanyId("company-2");
    public static final EventId EVENT_ID = new EventId("event-1");
    public static final EventId OTHER_EVENT_ID = new EventId("event-2");
    public static final ZoneId VIP_ZONE = new ZoneId("vip-zone");
    public static final ZoneId REGULAR_ZONE = new ZoneId("regular-zone");
    public static final ZoneId BALCONY_ZONE = new ZoneId("balcony-zone");
    public static final LocalDate PURCHASE_DATE = LocalDate.now();

    public static final String TEST_CURRENCY = "ILS";
    public static final Money DEFAULT_UNIT_PRICE = Money.of(BigDecimal.valueOf(100), TEST_CURRENCY);

    private PolicyTestFixtures() {
    }

        public static PurchaseContext contextForGuest(
            CompanyId companyId,
            EventId eventId,
            LocalDate buyerBirthDate,
            String discountCode,
            List<ZoneId> zones) {
        return new PurchaseContext(
                eventId,
                companyId,
                zoneContextsFromTicketZones(zones),
                BuyerType.GUEST,
                buyerBirthDate,
                PURCHASE_DATE,
                discountCode);
    }
    

    public static PurchaseContext contextWithTickets(ZoneId... zones) {
        return contextForCompanyAndEvent(
                COMPANY_ID,
                EVENT_ID,
                null,
                zones);
    }

    public static PurchaseContext contextWithCode(String code, ZoneId... zones) {
        return contextForCompanyAndEvent(
                COMPANY_ID,
                EVENT_ID,
                code,
                zones);
    }

    public static PurchaseContext contextWithBirthDate(LocalDate birthDate, ZoneId... zones) {
        return context(
                COMPANY_ID,
                EVENT_ID,
                birthDate,
                null,
                List.of(zones));
    }

    public static PurchaseContext contextForCompanyAndEvent(
            CompanyId companyId,
            EventId eventId,
            String code,
            ZoneId... zones) {
        return context(
                companyId,
                eventId,
                LocalDate.now().minusYears(25),
                code,
                List.of(zones));
    }

    public static PurchaseContext context(
            CompanyId companyId,
            EventId eventId,
            LocalDate buyerBirthDate,
            String discountCode,
            List<ZoneId> zones) {
        return new PurchaseContext(
                eventId,
                companyId,
                zoneContextsFromTicketZones(zones),
                BuyerType.MEMBER,
                buyerBirthDate,
                PURCHASE_DATE,
                discountCode);
    }

    public static PurchaseContext context(
            CompanyId companyId,
            EventId eventId,
            LocalDate buyerBirthDate,
            LocalDate purchaseDate,
            String discountCode,
            List<ZoneId> zones) {
        return new PurchaseContext(
                eventId,
                companyId,
                zoneContextsFromTicketZones(zones),
                BuyerType.MEMBER,
                buyerBirthDate,
                purchaseDate,
                discountCode);
    }

    public static PurchaseContext pricedContext(
            CompanyId companyId,
            EventId eventId,
            LocalDate buyerBirthDate,
            LocalDate purchaseDate,
            String discountCode,
            Map<ZoneId, ZonePurchaseContext> zones) {
        return new PurchaseContext(
                eventId,
                companyId,
                zones,
                BuyerType.MEMBER,
                buyerBirthDate,
                purchaseDate,
                discountCode);
    }

    public static Map<ZoneId, ZonePurchaseContext> zoneContextsFromTicketZones(List<ZoneId> zones) {
        Map<ZoneId, Integer> quantitiesByZone = new LinkedHashMap<>();

        for (ZoneId zoneId : zones) {
            if (zoneId == null) {
                throw new IllegalArgumentException("zones cannot contain null zone id");
            }

            Integer currentQuantity = quantitiesByZone.get(zoneId);
            int nextQuantity = currentQuantity == null ? 1 : currentQuantity + 1;
            quantitiesByZone.put(zoneId, nextQuantity);
        }

        Map<ZoneId, ZonePurchaseContext> result = new LinkedHashMap<>();

        for (Map.Entry<ZoneId, Integer> entry : quantitiesByZone.entrySet()) {
            ZoneId zoneId = entry.getKey();
            int quantity = entry.getValue();

            result.put(
                    zoneId,
                    new ZonePurchaseContext(
                            zoneId,
                            quantity,
                            DEFAULT_UNIT_PRICE.multiply(quantity)));
        }

        return result;
    }

    public static Map<ZoneId, ZonePurchaseContext> zoneContexts(
            ZonePurchaseContext... zones) {
        Map<ZoneId, ZonePurchaseContext> result = new LinkedHashMap<>();

        for (ZonePurchaseContext zone : zones) {
            result.put(zone.zoneId(), zone);
        }

        return result;
    }

    public static ZonePurchaseContext zoneContext(
            ZoneId zoneId,
            int quantity,
            String subtotal) {
        return new ZonePurchaseContext(
                zoneId,
                quantity,
                Money.of(new BigDecimal(subtotal), TEST_CURRENCY));
    }

    public static EventSnapshot snapshot(EventId eventId) {
        return new EventSnapshot(
                eventId.toString(),
                "Test Event",
                "Test Company",
                LocalDate.now().plusDays(30),
                "Tel Aviv");
    }

    public static Discount GeneralNoExpiryDiscount(String name, BigDecimal discountPercent) {
        return Discount.GeneralDiscount(name, discountPercent, null);
    }
}