package com.eventsystem.domain.policy;

import com.eventsystem.domain.order.BuyerType;
import com.eventsystem.domain.policy.shared.PurchaseContext;
import com.eventsystem.domain.policy.shared.ZonePurchaseContext;
import com.eventsystem.domain.shared.Money;
import com.eventsystem.domain.zone.ZoneId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.eventsystem.domain.policy.PolicyTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PurchaseContextTest {

    @Test
    void ticketCountSumsQuantitiesAcrossZones() {
        PurchaseContext context = new PurchaseContext(
                EVENT_ID,
                COMPANY_ID,
                zoneContexts(
                        zoneContext(VIP_ZONE, 2, "200.00"),
                        zoneContext(BALCONY_ZONE, 3, "150.00")
                ),
                BuyerType.MEMBER,
                LocalDate.now().minusYears(25),
                PURCHASE_DATE,
                null
        );

        assertThat(context.ticketCount()).isEqualTo(5);
        assertThat(context.ticketCountInZone(VIP_ZONE)).isEqualTo(2);
        assertThat(context.ticketCountInZone(BALCONY_ZONE)).isEqualTo(3);
        assertThat(context.ticketCountInZone(REGULAR_ZONE)).isZero();
    }

    @Test
    void baseTotalSumsZoneSubtotals() {
        PurchaseContext context = new PurchaseContext(
                EVENT_ID,
                COMPANY_ID,
                zoneContexts(
                        zoneContext(VIP_ZONE, 2, "200.00"),
                        zoneContext(BALCONY_ZONE, 3, "150.00")
                ),
                BuyerType.MEMBER,
                LocalDate.now().minusYears(25),
                PURCHASE_DATE,
                null
        );

        assertThat(context.baseTotal().amount()).isEqualByComparingTo("350.00");
        assertThat(context.baseTotal().currency()).isEqualTo(TEST_CURRENCY);
    }

    @Test
    void subtotalForZonesReturnsOnlyRequestedZones() {
        PurchaseContext context = new PurchaseContext(
                EVENT_ID,
                COMPANY_ID,
                zoneContexts(
                        zoneContext(VIP_ZONE, 2, "200.00"),
                        zoneContext(BALCONY_ZONE, 3, "150.00"),
                        zoneContext(REGULAR_ZONE, 1, "80.00")
                ),
                BuyerType.MEMBER,
                LocalDate.now().minusYears(25),
                PURCHASE_DATE,
                null
        );

        Money subtotal = context.subtotalForZones(List.of(VIP_ZONE, BALCONY_ZONE));

        assertThat(subtotal.amount()).isEqualByComparingTo("350.00");
        assertThat(subtotal.currency()).isEqualTo(TEST_CURRENCY);
    }

    @Test
    void zonesOfEachEventTicketIsDerivedFromQuantities() {
        PurchaseContext context = new PurchaseContext(
                EVENT_ID,
                COMPANY_ID,
                zoneContexts(
                        zoneContext(VIP_ZONE, 2, "200.00"),
                        zoneContext(BALCONY_ZONE, 3, "150.00")
                ),
                BuyerType.MEMBER,
                LocalDate.now().minusYears(25),
                PURCHASE_DATE,
                null
        );

        assertThat(context.zonesOfEachEventTicket())
                .containsExactly(
                        VIP_ZONE,
                        VIP_ZONE,
                        BALCONY_ZONE,
                        BALCONY_ZONE,
                        BALCONY_ZONE
                );
    }

    @Test
    void onlyForZonesPreservesQuantityAndSubtotalForAffectedZones() {
        PurchaseContext context = new PurchaseContext(
                EVENT_ID,
                COMPANY_ID,
                zoneContexts(
                        zoneContext(VIP_ZONE, 2, "200.00"),
                        zoneContext(BALCONY_ZONE, 3, "150.00"),
                        zoneContext(REGULAR_ZONE, 1, "80.00")
                ),
                BuyerType.MEMBER,
                LocalDate.now().minusYears(25),
                PURCHASE_DATE,
                null
        );

        PurchaseContext filtered = context.onlyForZones(List.of(VIP_ZONE, BALCONY_ZONE));

        assertThat(filtered.ticketCount()).isEqualTo(5);
        assertThat(filtered.baseTotal().amount()).isEqualByComparingTo("350.00");
        assertThat(filtered.zones()).containsOnlyKeys(VIP_ZONE, BALCONY_ZONE);
    }

    @Test
    void constructorRejectsZoneMapKeyThatDoesNotMatchZoneContextZoneId() {
        Map<ZoneId, ZonePurchaseContext> zones = new LinkedHashMap<>();
        zones.put(
                VIP_ZONE,
                new ZonePurchaseContext(
                        BALCONY_ZONE,
                        2,
                        Money.of(BigDecimal.valueOf(200), TEST_CURRENCY)
                )
        );

        assertThatThrownBy(() -> new PurchaseContext(
                EVENT_ID,
                COMPANY_ID,
                zones,
                BuyerType.MEMBER,
                LocalDate.now().minusYears(25),
                PURCHASE_DATE,
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("zone map key must match");
    }

    @Test
    void withCodeKeepsZonesAndNormalizesBlankCodeToNull() {
        PurchaseContext context = contextWithTickets(VIP_ZONE, VIP_ZONE);

        PurchaseContext withBlankCode = context.withCode("   ");

        assertThat(withBlankCode.discountCode()).isNull();
        assertThat(withBlankCode.zones()).isEqualTo(context.zones());
        assertThat(withBlankCode.ticketCount()).isEqualTo(2);
    }
}