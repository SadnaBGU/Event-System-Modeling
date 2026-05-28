package com.eventsystem.application.event;

import com.eventsystem.domain.event.*;
import com.eventsystem.domain.domainexceptions.EventDomainException;
import com.eventsystem.domain.zone.Zone;
import com.eventsystem.domain.zone.ZoneId;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.BuyerType;
import com.eventsystem.domain.order.OrderItem;
import com.eventsystem.domain.policy.PolicyValidationResult;
import com.eventsystem.domain.policy.PurchaseContext;
import com.eventsystem.domain.policy.PurchasePolicy;
import com.eventsystem.domain.purchaserecord.DiscountSnapshot;
import com.eventsystem.domain.purchaserecord.EventSnapshot;
import com.eventsystem.domain.shared.Money;

import com.eventsystem.application.policy.IPurchasePolicyRepository;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * UC7: Ticket Selection and Reservation
 *
 * Tests for UATs:
 * - UAT-16: Successful Reservation
 * - UAT-22: Tickets Unavailable
 *
 * UC3: Virtual Queue and Load Management
 *
 * Tests for UATs:
 * - UAT-09: Sold Out While Queued
 *
 * UC9: Checkout and Payment Completion
 *
 * Tests for UATs:
 * - UAT-26: Successful Checkout
 * - UAT-27: Checkout Policy Violation
 * - UAT-47: Invalid Coupon at Checkout
 *
 * Event purchase support coverage:
 * - Check whether an event is purchasable
 * - Enforce purchasable-event requirement before reservation or checkout
 * - Check whether all zones are full
 * - Mark event as sold out when all zones are full
 * - Validate that selected zones belong to the event
 * - Validate purchase policy before checkout
 * - Apply discount snapshot
 * - Create event snapshot for purchase records
 */
@ExtendWith(MockitoExtension.class)
class EventPurchaseSupportServiceTest {

    @Mock
    private IEventRepository eventRepository;

    @Mock
    private IZoneRepository zoneRepository;

    @Mock
    private IPurchasePolicyRepository ppRepository;


    private EventPurchaseSupportService service;

    @BeforeEach
    void setUp() {
        service = new EventPurchaseSupportService(eventRepository, zoneRepository, ppRepository );
    }

    private EventDetails defaultDetails() {
        return new EventDetails(
                "Test Event",
                List.of(LocalDateTime.now().plusDays(10)),
                "Category",
                "Here",
                "A test event"
        );
    }

    private Event createDraftEvent() {
        return Event.createDraft("company-1", defaultDetails(), VenueMap.empty());
    }

    private Event createPublishedEvent() {
        Event event = createDraftEvent();
        event.addZone(ZoneId.random());
        event.publish();
        return event;
    }

    private Zone zoneWithAvailableCount(int availableCount) {
        Zone zone = mock(Zone.class);
        when(zone.getAvailableCount()).thenReturn(availableCount);
        return zone;
    }

    // ── isPurchasable ───────────────────────────────────────────────────────

    // UAT-16: Successful Reservation
    // A published event is considered purchasable before ticket selection/reservation.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void isPurchasable_whenEventIsPublished_returnsTrue() {
        Event event = createPublishedEvent();

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        boolean result = service.isPurchasable(event.id());

        assertThat(result).isTrue();
    }

    // UAT-16: Successful Reservation
    // A draft event is not purchasable and cannot be used for reservation.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void isPurchasable_whenEventIsDraft_returnsFalse() {
        Event event = createDraftEvent();

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        boolean result = service.isPurchasable(event.id());

        assertThat(result).isFalse();
    }

    // UAT-16: Successful Reservation
    // A cancelled event is not purchasable and cannot be used for reservation.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void isPurchasable_whenEventIsCancelled_returnsFalse() {
        Event event = createDraftEvent();
        event.cancel();

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        boolean result = service.isPurchasable(event.id());

        assertThat(result).isFalse();
    }

    // Supporting test for UC7
    // Reject purchasability check when the event does not exist.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void isPurchasable_eventNotFound_throws() {
        EventId unknownId = EventId.random();

        when(eventRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.isPurchasable(unknownId));

        verifyNoInteractions(zoneRepository);
    }

    // Supporting validation test for UC7
    // Reject purchasability check when event ID is null.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void isPurchasable_nullEventId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.isPurchasable(null));

        verifyNoInteractions(eventRepository);
        verifyNoInteractions(zoneRepository);
    }

    // ── requirePurchasable ──────────────────────────────────────────────────

    // UAT-16: Successful Reservation
    // Allow reservation flow to continue when the event is published and purchasable.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void requirePurchasable_whenPublished_doesNotThrow() {
        Event event = createPublishedEvent();

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        assertThatCode(() -> service.requirePurchasable(event.id()))
                .doesNotThrowAnyException();
    }

    // UAT-16: Successful Reservation
    // Reject reservation flow when the event is not published/purchasable.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void requirePurchasable_whenDraft_throws() {
        Event event = createDraftEvent();

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> service.requirePurchasable(event.id()))
                .isInstanceOf(EventDomainException.class);
    }

    // Supporting test for UC7
    // Reject purchasable requirement when the event does not exist.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void requirePurchasable_eventNotFound_throws() {
        EventId unknownId = EventId.random();

        when(eventRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.requirePurchasable(unknownId));

        verifyNoInteractions(zoneRepository);
    }

    // Supporting validation test for UC7
    // Reject purchasable requirement when event ID is null.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void requirePurchasable_nullEventId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.requirePurchasable(null));

        verifyNoInteractions(eventRepository);
        verifyNoInteractions(zoneRepository);
    }

    // ── areAllZonesFull ─────────────────────────────────────────────────────

    // UAT-09: Sold Out While Queued
    // Detect that the event is sold out when all related zones have no availability.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void areAllZonesFull_whenAllZonesFull_returnsTrue() {
        EventId eventId = EventId.random();

        Zone zone1 = zoneWithAvailableCount(0);
        Zone zone2 = zoneWithAvailableCount(0);

        when(zoneRepository.findByEventId(eventId)).thenReturn(List.of(zone1, zone2));

        boolean result = service.areAllZonesFull(eventId);

        assertThat(result).isTrue();
        verifyNoInteractions(eventRepository);
    }

    // UAT-16 / UAT-22: Successful Reservation / Tickets Unavailable
    // Detect that an event still has ticket availability when at least one zone has capacity.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void areAllZonesFull_whenOneZoneHasAvailability_returnsFalse() {
        EventId eventId = EventId.random();

        Zone zone1 = zoneWithAvailableCount(0);
        Zone zone2 = zoneWithAvailableCount(5);

        when(zoneRepository.findByEventId(eventId)).thenReturn(List.of(zone1, zone2));

        boolean result = service.areAllZonesFull(eventId);

        assertThat(result).isFalse();
        verifyNoInteractions(eventRepository);
    }

    // Supporting inventory test for UC7
    // An event with no zones should not be considered sold out by zone availability.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void areAllZonesFull_whenNoZones_returnsFalse() {
        EventId eventId = EventId.random();

        when(zoneRepository.findByEventId(eventId)).thenReturn(List.of());

        boolean result = service.areAllZonesFull(eventId);

        assertThat(result).isFalse();
        verifyNoInteractions(eventRepository);
    }

    // Supporting validation test for UC7
    // Reject full-zone check when event ID is null.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void areAllZonesFull_nullEventId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.areAllZonesFull(null));

        verifyNoInteractions(eventRepository);
        verifyNoInteractions(zoneRepository);
    }

    // ── markSoldOutIfAllZonesFull ───────────────────────────────────────────

    // UAT-09: Sold Out While Queued
    // Mark the event as sold out when all zones are full.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void markSoldOutIfAllZonesFull_whenAllZonesFull_marksSoldOutAndSaves() {
        Event event = createPublishedEvent();

        Zone zone1 = zoneWithAvailableCount(0);
        Zone zone2 = zoneWithAvailableCount(0);

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));
        when(zoneRepository.findByEventId(event.id())).thenReturn(List.of(zone1, zone2));

        service.markSoldOutIfAllZonesFull(event.id());

        assertThat(event.isSoldOut()).isTrue();
        verify(eventRepository).save(event);
    }

    // UAT-16: Successful Reservation
    // Do not mark the event as sold out when at least one zone still has availability.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void markSoldOutIfAllZonesFull_whenNotAllZonesFull_doesNotSave() {
        Event event = createPublishedEvent();

        Zone zone1 = zoneWithAvailableCount(0);
        Zone zone2 = zoneWithAvailableCount(2);

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));
        when(zoneRepository.findByEventId(event.id())).thenReturn(List.of(zone1, zone2));

        service.markSoldOutIfAllZonesFull(event.id());

        assertThat(event.isPublished()).isTrue();
        verify(eventRepository, never()).save(any());
    }

    // Supporting inventory test for UC7
    // Do not mark an event as sold out when it has no zones.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void markSoldOutIfAllZonesFull_whenNoZones_doesNotSave() {
        Event event = createPublishedEvent();

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));
        when(zoneRepository.findByEventId(event.id())).thenReturn(List.of());

        service.markSoldOutIfAllZonesFull(event.id());

        assertThat(event.isPublished()).isTrue();
        verify(eventRepository, never()).save(any());
    }

    // Supporting lifecycle test for UC7
    // Do not check zones or save when the event is still a draft.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void markSoldOutIfAllZonesFull_whenEventIsDraft_doesNotCheckZonesOrSave() {
        Event event = createDraftEvent();

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        service.markSoldOutIfAllZonesFull(event.id());

        assertThat(event.isDraft()).isTrue();
        verifyNoInteractions(zoneRepository);
        verify(eventRepository, never()).save(any());
    }

    // Supporting test for UC3 / UC7
    // Reject sold-out update when the event does not exist.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void markSoldOutIfAllZonesFull_eventNotFound_throws() {
        EventId unknownId = EventId.random();

        when(eventRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.markSoldOutIfAllZonesFull(unknownId));

        verifyNoInteractions(zoneRepository);
        verify(eventRepository, never()).save(any());
    }

    // Supporting validation test for UC3 / UC7
    // Reject sold-out update when event ID is null.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void markSoldOutIfAllZonesFull_nullEventId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.markSoldOutIfAllZonesFull(null));

        verifyNoInteractions(eventRepository);
        verifyNoInteractions(zoneRepository);
    }

    // ── getEventForSnapshot ─────────────────────────────────────────────────

    // UAT-26: Successful Checkout
    // Load the event so checkout can create a purchase-record snapshot.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void getEventForSnapshot_returnsLoadedEvent() {
        Event event = createDraftEvent();

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        Event result = service.getEventForSnapshot(event.id());

        assertThat(result).isSameAs(event);
    }

    // Supporting test for UC9
    // Reject snapshot creation when the event does not exist.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void getEventForSnapshot_eventNotFound_throws() {
        EventId unknownId = EventId.random();

        when(eventRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.getEventForSnapshot(unknownId));

        verifyNoInteractions(zoneRepository);
    }

    // Supporting validation test for UC9
    // Reject snapshot creation when event ID is null.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void getEventForSnapshot_nullEventId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.getEventForSnapshot(null));

        verifyNoInteractions(eventRepository);
        verifyNoInteractions(zoneRepository);
    }

    // ── requireZoneBelongsToEvent ───────────────────────────────────────────

    // UAT-16: Successful Reservation
    // Allow ticket selection when the selected zone belongs to the event.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void requireZoneBelongsToEvent_whenZoneBelongs_doesNotThrow() {
        Event event = createDraftEvent();
        ZoneId zoneId = ZoneId.random();
        event.addZone(zoneId);

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        assertThatCode(() -> service.requireZoneBelongsToEvent(event.id(), zoneId))
                .doesNotThrowAnyException();

        verifyNoInteractions(zoneRepository);
    }

    // UAT-22: Tickets Unavailable
    // Reject ticket selection when the selected zone does not belong to the event.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void requireZoneBelongsToEvent_whenZoneDoesNotBelong_throws() {
        Event event = createDraftEvent();

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> service.requireZoneBelongsToEvent(event.id(), ZoneId.random()))
                .isInstanceOf(EventDomainException.class);

        verifyNoInteractions(zoneRepository);
    }

    // Supporting test for UC7
    // Reject zone-belonging validation when the event does not exist.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void requireZoneBelongsToEvent_eventNotFound_throws() {
        EventId unknownId = EventId.random();

        when(eventRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.requireZoneBelongsToEvent(unknownId, ZoneId.random()));

        verifyNoInteractions(zoneRepository);
    }

    // Supporting validation test for UC7
    // Reject zone-belonging validation when event ID is null.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void requireZoneBelongsToEvent_nullEventId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.requireZoneBelongsToEvent(null, ZoneId.random()));

        verifyNoInteractions(eventRepository);
        verifyNoInteractions(zoneRepository);
    }

    // Supporting validation test for UC7
    // Reject zone-belonging validation when zone ID is null.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void requireZoneBelongsToEvent_nullZoneId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.requireZoneBelongsToEvent(EventId.random(), null));

        verifyNoInteractions(eventRepository);
        verifyNoInteractions(zoneRepository);
    }

    // UAT-26: Successful Checkout
    // Approve purchase-policy validation when the event is published and all selected zones belong to the event.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void validatePurchasePolicy_whenPublishedAndZoneBelongs_returnsTrue() {
        Event event = createDraftEvent();
        ZoneId zoneId = ZoneId.random();
        event.addZone(zoneId);
        event.publish();

        OrderItem item = new OrderItem(
                zoneId.value(),
                "seat-1",
                1,
                Money.of(BigDecimal.valueOf(100), "ILS")
        );

        BuyerReference buyer = new BuyerReference(BuyerType.MEMBER, null, "member-1");

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        boolean result = service.validatePurchasePolicy(event.id().value(), buyer, List.of(item));

        assertThat(result).isTrue();
    }

    // UAT-27: Checkout Policy Violation
    // Reject checkout when the event is not published.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void validatePurchasePolicy_whenEventIsNotPublished_returnsFalse() {
        Event event = createDraftEvent();
        ZoneId zoneId = ZoneId.random();
        event.addZone(zoneId);

        OrderItem item = new OrderItem(
                zoneId.value(),
                "seat-1",
                1,
                Money.of(BigDecimal.valueOf(100), "ILS")
        );

        BuyerReference buyer = new BuyerReference(BuyerType.MEMBER, null, "member-1");

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        boolean result = service.validatePurchasePolicy(event.id().value(), buyer, List.of(item));

        assertThat(result).isFalse();
    }

    // UAT-27: Checkout Policy Violation
    // Reject checkout when the selected zone does not belong to the event.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void validatePurchasePolicy_whenZoneDoesNotBelongToEvent_returnsFalse() {
        Event event = createDraftEvent();
        ZoneId eventZoneId = ZoneId.random();
        ZoneId otherZoneId = ZoneId.random();
        event.addZone(eventZoneId);
        event.publish();

        OrderItem item = new OrderItem(
                otherZoneId.value(),
                "seat-1",
                1,
                Money.of(BigDecimal.valueOf(100), "ILS")
        );

        BuyerReference buyer = new BuyerReference(BuyerType.MEMBER, null, "member-1");

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        boolean result = service.validatePurchasePolicy(event.id().value(), buyer, List.of(item));

        assertThat(result).isFalse();
    }

    // UAT-47: Invalid Coupon at Checkout
    // Return zero discount when no discount policy/coupon is applicable.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void applyDiscount_withoutDiscountPolicy_returnsZeroDiscount() {
        Event event = createPublishedEvent();
        Money baseTotal = Money.of(BigDecimal.valueOf(100), "ILS");

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        DiscountSnapshot result = service.applyDiscount(event.id().value(), null, baseTotal);

        assertThat(result.discountName()).isEqualTo("NO_DISCOUNT");
        assertThat(result.discountAmount()).isEqualTo(Money.of(BigDecimal.ZERO, "ILS"));
    }

    // UAT-26: Successful Checkout
    // Create an event snapshot for the purchase record after checkout.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void getEventSnapshot_returnsSnapshotFromEventDetails() {
        Event event = createPublishedEvent();

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        EventSnapshot snapshot = service.getEventSnapshot(event.id().value());

        assertThat(snapshot.eventId()).isEqualTo(event.id().value());
        assertThat(snapshot.eventName()).isEqualTo(event.details().name());
        assertThat(snapshot.companyName()).isEqualTo(event.companyId().toString());
        assertThat(snapshot.eventDate()).isEqualTo(event.details().dates().get(0).toLocalDate());
        assertThat(snapshot.location()).isEqualTo(event.details().location());
    }

    @Test
    void validatePurchasePolicy_whenNoPolicyStored_usesAllowAllPolicy() {
        Event event = createDraftEvent();
        ZoneId zoneId = ZoneId.random();
        event.addZone(zoneId);
        event.publish();

        OrderItem item = new OrderItem(
                zoneId.value(),
                "seat-1",
                3,
                Money.of(BigDecimal.valueOf(100), "ILS")
        );

        BuyerReference buyer = new BuyerReference(BuyerType.MEMBER, null, "member-1");

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));
        when(ppRepository.findByEventId(event.id())).thenReturn(Optional.empty());

        PolicyValidationResult result = service.getValidatePurchasePolicyResults(
                event.id().value(),
                buyer,
                List.of(item),
                LocalDate.now().minusYears(25)
        );

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void validatePurchasePolicy_whenStoredPolicyRejectsOrder_returnsFailure_UAT27() {
        Event event = createDraftEvent();
        ZoneId zoneId = ZoneId.random();
        event.addZone(zoneId);
        event.publish();

        OrderItem item = new OrderItem(
                zoneId.value(),
                "seat-1",
                2,
                Money.of(BigDecimal.valueOf(100), "ILS")
        );

        BuyerReference buyer = new BuyerReference(BuyerType.MEMBER, null, "member-1");

        PurchasePolicy maxOneTicketPolicy =
                new PurchasePolicy(new com.eventsystem.domain.policy.basic.MaxTicketPolicy(1));

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));
        when(ppRepository.findByEventId(event.id())).thenReturn(Optional.of(maxOneTicketPolicy));

        PolicyValidationResult result = service.getValidatePurchasePolicyResults(
                event.id().value(),
                buyer,
                List.of(item),
                LocalDate.now().minusYears(25)
        );

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.reason()).contains("Cannot Purchase more than 1 tickets");
    }

    @Test
    void fromPurchaseInfo_expandsOrderItemQuantityIntoOneZonePerTicket() {
        Event event = createDraftEvent();
        ZoneId zoneId = ZoneId.random();
        event.addZone(zoneId);
        event.publish();

        OrderItem item = new OrderItem(
                zoneId.value(),
                null,
                3,
                Money.of(BigDecimal.valueOf(100), "ILS")
        );

        BuyerReference buyer = new BuyerReference(BuyerType.MEMBER, null, "member-1");

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        PurchaseContext context = service.fromPurchaseInfo(
                event.id().value(),
                buyer,
                List.of(item),
                LocalDate.now().minusYears(25)
        );

        assertThat(context.zonesOfEachEventTicket())
                .containsExactly(zoneId, zoneId, zoneId);
    }

    @Test
    void fromPurchaseInfo_trimsDiscountCode() {
        Event event = createDraftEvent();
        ZoneId zoneId = ZoneId.random();
        event.addZone(zoneId);
        event.publish();

        OrderItem item = new OrderItem(
                zoneId.value(),
                null,
                1,
                Money.of(BigDecimal.valueOf(100), "ILS")
        );

        BuyerReference buyer = new BuyerReference(BuyerType.MEMBER, null, "member-1");

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        PurchaseContext context = service.fromPurchaseInfo(
                event.id().value(),
                buyer,
                List.of(item),
                LocalDate.now().minusYears(25),
                "  SAVE20  "
        );

        assertThat(context.discountCode()).isEqualTo("SAVE20");
    }

    @Test
    void fromPurchaseInfo_blankDiscountCodeBecomesNull() {
        Event event = createDraftEvent();
        ZoneId zoneId = ZoneId.random();
        event.addZone(zoneId);
        event.publish();

        OrderItem item = new OrderItem(
                zoneId.value(),
                null,
                1,
                Money.of(BigDecimal.valueOf(100), "ILS")
        );

        BuyerReference buyer = new BuyerReference(BuyerType.MEMBER, null, "member-1");

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        PurchaseContext context = service.fromPurchaseInfo(
                event.id().value(),
                buyer,
                List.of(item),
                LocalDate.now().minusYears(25),
                "   "
        );

        assertThat(context.discountCode()).isNull();
    } 

    @Test
    void validatePurchasePolicy_whenOrderItemZoneIdIsBlank_returnsFailure() {
        Event event = createDraftEvent();
        event.addZone(ZoneId.random());
        event.publish();

        OrderItem item = new OrderItem(
                "   ",
                null,
                1,
                Money.of(BigDecimal.valueOf(100), "ILS")
        );

        BuyerReference buyer = new BuyerReference(BuyerType.MEMBER, null, "member-1");

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        PolicyValidationResult result = service.getValidatePurchasePolicyResults(
                event.id().value(),
                buyer,
                List.of(item),
                LocalDate.now().minusYears(25)
        );

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.reason()).contains("Invalid zone id");
        verify(ppRepository, never()).findByEventId(any());
    }

    @Test
    void evaluatePurchasePolicy_whenContextContainsZoneNotInEvent_returnsFailure() {
        Event event = createDraftEvent();
        ZoneId realZone = ZoneId.random();
        ZoneId wrongZone = ZoneId.random();
        event.addZone(realZone);
        event.publish();

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        PurchaseContext context = new PurchaseContext(
                event.id(),
                event.companyId(),
                List.of(wrongZone),
                LocalDate.now().minusYears(25),
                null
        );

        PolicyValidationResult result = service.validateContextAgainstEvent(event.id(), context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.reason()).contains("invalid zone");
        verify(ppRepository, never()).findByEventId(any());
    }
}