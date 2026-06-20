package com.eventsystem.domain.policy.shared;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.shared.Money;
import com.eventsystem.domain.zone.ZoneId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record PurchaseContext(
        EventId eventId,
        CompanyId companyId,
        Map<ZoneId, ZonePurchaseContext> zones,
        LocalDate buyerBirthDate,
        LocalDate purchaseDate,
        String discountCode
) {

    private static final String DEFAULT_ZERO_CURRENCY = "ILS";

    public PurchaseContext {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(zones, "zones must not be null");
        Objects.requireNonNull(buyerBirthDate, "buyerBirthDate must not be null");
        Objects.requireNonNull(purchaseDate, "purchaseDate must not be null");

        validateZones(zones);

        zones = Collections.unmodifiableMap(new LinkedHashMap<>(zones));
        discountCode = normalizeDiscountCode(discountCode);
    }

    public int ticketCount() {
        return zones.values()
                .stream()
                .mapToInt(ZonePurchaseContext::quantity)
                .sum();
    }

    public int ticketCountInZone(ZoneId zoneId) {
        Objects.requireNonNull(zoneId, "zoneId must not be null");

        ZonePurchaseContext zoneContext = zones.get(zoneId);
        return zoneContext == null ? 0 : zoneContext.quantity();
    }

    public boolean hasTicketsInZone(ZoneId zoneId) {
        return ticketCountInZone(zoneId) > 0;
    }

    public Money subtotalForZone(ZoneId zoneId) {
        Objects.requireNonNull(zoneId, "zoneId must not be null");

        ZonePurchaseContext zoneContext = zones.get(zoneId);
        return zoneContext == null ? zeroMoney() : zoneContext.subtotal();
    }

    public Money subtotalForZones(Iterable<ZoneId> zoneIds) {
        Objects.requireNonNull(zoneIds, "zoneIds must not be null");

        Money total = zeroMoney();

        for (ZoneId zoneId : zoneIds) {
            total = total.add(subtotalForZone(zoneId));
        }

        return total;
    }

    public Money baseTotal() {
        return zones.values()
                .stream()
                .map(ZonePurchaseContext::subtotal)
                .reduce(zeroMoney(), Money::add);
    }

    /*
     * This is now a derived helper, not stored state.
     *
     * Example:
     * VIP quantity 2, Balcony quantity 3
     *
     * returns:
     * [VIP, VIP, Balcony, Balcony, Balcony]
     *
     * This lets existing policy rules such as ZoneSpecificPolicy keep their
     * current evaluation style while the actual context model becomes richer.
     */
    public List<ZoneId> zonesOfEachEventTicket() {
        return zones.values()
                .stream()
                .flatMap(zoneContext ->
                        java.util.stream.IntStream
                                .range(0, zoneContext.quantity())
                                .mapToObj(i -> zoneContext.zoneId())
                )
                .toList();
    }

    public PurchaseContext withCode(String code) {
        return new PurchaseContext(
                eventId,
                companyId,
                zones,
                buyerBirthDate,
                purchaseDate,
                code
        );
    }

    public PurchaseContext withPurchaseDate(LocalDate newPurchaseDate) {
        return new PurchaseContext(
                eventId,
                companyId,
                zones,
                buyerBirthDate,
                newPurchaseDate,
                discountCode
        );
    }

    public PurchaseContext onlyForZones(Iterable<ZoneId> affectedZones) {
        Objects.requireNonNull(affectedZones, "affectedZones must not be null");

        Map<ZoneId, ZonePurchaseContext> filteredZones = new LinkedHashMap<>();

        for (ZoneId zoneId : affectedZones) {
            Objects.requireNonNull(zoneId, "affectedZones cannot contain null zone id");

            ZonePurchaseContext zoneContext = zones.get(zoneId);
            if (zoneContext != null) {
                filteredZones.put(zoneId, zoneContext);
            }
        }

        return new PurchaseContext(
                eventId,
                companyId,
                filteredZones,
                buyerBirthDate,
                purchaseDate,
                discountCode
        );
    }

    private Money zeroMoney() {
        return zones.values()
                .stream()
                .findFirst()
                .map(zone -> Money.of(BigDecimal.ZERO, zone.subtotal().currency()))
                .orElseGet(() -> Money.of(BigDecimal.ZERO, DEFAULT_ZERO_CURRENCY));
    }

    private static void validateZones(Map<ZoneId, ZonePurchaseContext> zones) {
        if (zones.containsKey(null)) {
            throw new IllegalArgumentException("zones cannot contain null zone id");
        }

        if (zones.containsValue(null)) {
            throw new IllegalArgumentException("zones cannot contain null zone context");
        }

        zones.forEach((zoneId, zoneContext) -> {
            if (!zoneId.equals(zoneContext.zoneId())) {
                throw new IllegalArgumentException("zone map key must match ZonePurchaseContext.zoneId");
            }
        });
    }

    private static String normalizeDiscountCode(String discountCode) {
        return discountCode == null || discountCode.isBlank()
                ? null
                : discountCode.trim();
    }
}