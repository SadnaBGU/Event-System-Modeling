package com.eventsystem.application;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.order.BuyerType;
import com.eventsystem.domain.order.OrderItem;
import com.eventsystem.domain.policy.shared.PurchaseContext;
import com.eventsystem.domain.policy.shared.ZonePurchaseContext;
import com.eventsystem.domain.shared.Money;
import com.eventsystem.domain.zone.ZoneId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TestPurchaseContexts {

    public static final String DEFAULT_CURRENCY = "USD";
    public static final Money DEFAULT_UNIT_PRICE = Money.of(BigDecimal.valueOf(100), DEFAULT_CURRENCY);

    private TestPurchaseContexts() {
    }

    public static PurchaseContext contextWithZones(
            EventId eventId,
            CompanyId companyId,
            ZoneId... zones
    ) {
        return contextWithZones(
                eventId,
                companyId,
                LocalDate.now().minusYears(25),
                LocalDate.now(),
                null,
                zones
        );
    }

    public static PurchaseContext contextWithZones(
            EventId eventId,
            CompanyId companyId,
            LocalDate buyerBirthDate,
            String discountCode,
            ZoneId... zones
    ) {
        return contextWithZones(
                eventId,
                companyId,
                buyerBirthDate,
                LocalDate.now(),
                discountCode,
                zones
        );
    }

    public static PurchaseContext contextWithZones(
            EventId eventId,
            CompanyId companyId,
            LocalDate buyerBirthDate,
            LocalDate purchaseDate,
            String discountCode,
            ZoneId... zones
    ) {
        return new PurchaseContext(
                eventId,
                companyId,
                zoneContextsFromTicketZones(List.of(zones)),
                BuyerType.MEMBER,
                buyerBirthDate,
                purchaseDate,
                discountCode
        );
    }

    public static PurchaseContext contextWithZoneSubtotal(
            EventId eventId,
            CompanyId companyId,
            ZoneId zoneId,
            int quantity,
            String subtotal
    ) {
        return new PurchaseContext(
                eventId,
                companyId,
                Map.of(
                        zoneId,
                        new ZonePurchaseContext(
                                zoneId,
                                quantity,
                                Money.of(new BigDecimal(subtotal), DEFAULT_CURRENCY)
                        )
                ),
                BuyerType.MEMBER,
                LocalDate.now().minusYears(25),
                LocalDate.now(),
                null
        );
    }

    public static PurchaseContext contextFromOrderItems(
            EventId eventId,
            CompanyId companyId,
            List<OrderItem> items,
            String discountCode
    ) {
        return contextFromOrderItems(
                eventId,
                companyId,
                items,
                LocalDate.now().minusYears(25),
                LocalDate.now(),
                discountCode
        );
    }

    public static PurchaseContext contextFromOrderItems(
            EventId eventId,
            CompanyId companyId,
            List<OrderItem> items,
            LocalDate buyerBirthDate,
            LocalDate purchaseDate,
            String discountCode
    ) {
        return new PurchaseContext(
                eventId,
                companyId,
                zoneContextsFromOrderItems(items),
                BuyerType.MEMBER,
                buyerBirthDate,
                purchaseDate,
                discountCode
        );
    }

    public static Map<ZoneId, ZonePurchaseContext> zoneContextsFromTicketZones(List<ZoneId> zones) {
        Objects.requireNonNull(zones, "zones must not be null");

        Map<ZoneId, Integer> quantitiesByZone = new LinkedHashMap<>();

        for (ZoneId zoneId : zones) {
            Objects.requireNonNull(zoneId, "zones cannot contain null zone id");

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
                            DEFAULT_UNIT_PRICE.multiply(quantity)
                    )
            );
        }

        return result;
    }

    public static Map<ZoneId, ZonePurchaseContext> zoneContextsFromOrderItems(List<OrderItem> items) {
        Objects.requireNonNull(items, "items must not be null");

        Map<ZoneId, ZonePurchaseContext> result = new LinkedHashMap<>();

        for (OrderItem item : items) {
            Objects.requireNonNull(item, "order item must not be null");
            Objects.requireNonNull(item.getZoneId(), "order item zone id must not be null");
            Objects.requireNonNull(item.getUnitPrice(), "order item unit price must not be null");

            if (item.getQuantity() <= 0) {
                throw new IllegalArgumentException("order item quantity must be positive");
            }

            ZoneId zoneId = new ZoneId(item.getZoneId());
            Money itemSubtotal = item.getUnitPrice().multiply(item.getQuantity());

            ZonePurchaseContext existing = result.get(zoneId);

            if (existing == null) {
                result.put(
                        zoneId,
                        new ZonePurchaseContext(
                                zoneId,
                                item.getQuantity(),
                                itemSubtotal
                        )
                );
            } else {
                result.put(
                        zoneId,
                        new ZonePurchaseContext(
                                zoneId,
                                existing.quantity() + item.getQuantity(),
                                existing.subtotal().add(itemSubtotal)
                        )
                );
            }
        }

        return result;
    }
}