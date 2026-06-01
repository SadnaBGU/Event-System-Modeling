package com.eventsystem.domain.policy;

import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.zone.ZoneId;
import com.eventsystem.domain.company.CompanyId;


import java.time.LocalDate;
import java.util.List;
import java.util.Objects;


public record PurchaseContext(EventId eventId, CompanyId companyId, List<ZoneId> zonesOfEachEventTicket,
                                LocalDate buyerBirthDate, LocalDate purchaseDate, String discountCode ) {

    public PurchaseContext {
        Objects.requireNonNull(eventId, "eventSnapshot must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(zonesOfEachEventTicket, "zonesOfEachEventTicket must not be null");
        Objects.requireNonNull(buyerBirthDate, "buyerBirthDate must not be null");
        Objects.requireNonNull(purchaseDate, "purchaseDate must not be null");

        discountCode = normalizeDiscountCode(discountCode);
        zonesOfEachEventTicket = List.copyOf(zonesOfEachEventTicket);
    }

    public PurchaseContext(
            EventId eventId,
            CompanyId companyId,
            List<ZoneId> zonesOfEachEventTicket,
            LocalDate buyerBirthDate,
            String discountCode
    ) {
        this(
                eventId,
                companyId,
                zonesOfEachEventTicket,
                buyerBirthDate,
                LocalDate.now(),
                discountCode
        );
    }

    
    public static PurchaseContext fromPurchaseInfo(EventId eventId,
                                            CompanyId companyId,
                                            List<ZoneId> zonesOfEachTicket,
                                            LocalDate buyerBirthDate) {
        return fromPurchaseInfo(
                eventId,
                companyId,
                zonesOfEachTicket,
                buyerBirthDate,
                null
        );
    }
    

    

    public static PurchaseContext fromPurchaseInfo(EventId eventId,
                                            CompanyId companyId,
                                            List<ZoneId> zonesOfEachTicket,
                                            LocalDate buyerBirthDate,
                                            String discountCode) {
        return new PurchaseContext(
                eventId,
                companyId,
                zonesOfEachTicket,
                buyerBirthDate,
                normalizeDiscountCode(discountCode)
        );
    }

    public static PurchaseContext fromPurchaseInfo(EventId eventId,
                                            CompanyId companyId,
                                            List<ZoneId> zonesOfEachTicket,
                                            LocalDate buyerBirthDate,
                                            LocalDate purchaseDate,
                                            String discountCode) {
        return new PurchaseContext(
                eventId,
                companyId,
                zonesOfEachTicket,
                buyerBirthDate,
                purchaseDate,
                normalizeDiscountCode(discountCode)
        );
    }

    public int ticketCount() {
        return zonesOfEachEventTicket.size();
    }

    public PurchaseContext withCode(String code) {
        return new PurchaseContext(eventId(), companyId(),zonesOfEachEventTicket(),buyerBirthDate(),purchaseDate(), code);
    }

    public PurchaseContext withPurchaseDate(LocalDate newPurchaseDate) {
        return new PurchaseContext(
                eventId,
                companyId,
                zonesOfEachEventTicket,
                buyerBirthDate,
                newPurchaseDate,
                discountCode
        );
    }

    private static String normalizeDiscountCode(String discountCode) {
        return discountCode == null || discountCode.isBlank()
                ? null
                : discountCode.trim();
    }

}




