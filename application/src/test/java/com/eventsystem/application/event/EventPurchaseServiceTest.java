package com.eventsystem.application.event;

import com.eventsystem.application.company.ICompanyPermissionServicePort;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.domainexceptions.EventDomainException;
import com.eventsystem.domain.event.Event;
import com.eventsystem.domain.event.EventDetails;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.event.IEventRepository;
import com.eventsystem.domain.event.VenueMap;
import com.eventsystem.domain.policy.shared.PolicyValidationResult;
import com.eventsystem.domain.policy.shared.PurchaseContext;
import com.eventsystem.domain.purchaserecord.EventSnapshot;
import com.eventsystem.domain.zone.IZoneRepository;
import com.eventsystem.domain.zone.Zone;
import com.eventsystem.domain.zone.ZoneId;

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
 *
 * Notes about moved tests:
 * - Purchase-policy rule validation / UAT-27 belongs in PurchasePolicyServiceTest.
 * - Discount/coupon application / UAT-47 belongs in DiscountPolicyServiceTest.
 *
 * Current EventPurchaseService responsibility:
 * - Check whether an event is purchasable.
 * - Enforce purchasable-event requirement before reservation/checkout.
 * - Check whether all zones are full.
 * - Mark event as sold out when all zones are full.
 * - Validate that selected zones belong to the event.
 * - Build event-side purchase context data from event + order items.
 * - Create event snapshot for purchase records.
 * - Expose companyOfEvent for checkout/policy orchestration.
 */
@ExtendWith(MockitoExtension.class)
class EventPurchaseServiceTest {

    private static final CompanyId COMPANY_ID = new CompanyId("company-1");
    private static final String COMPANY_NAME = "Test Company";

    @Mock
    private IEventRepository eventRepository;

    @Mock
    private IZoneRepository zoneRepository;

    @Mock
    private ICompanyPermissionServicePort companyServicePort;

    private EventPurchaseService service;

    @BeforeEach
    void setUp() {
        service = new EventPurchaseService(eventRepository, zoneRepository, companyServicePort);
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
        return Event.createDraft(COMPANY_ID, defaultDetails(), VenueMap.empty());
    }

    private Event createPublishedEventWithZone(ZoneId zoneId) {
        Event event = createDraftEvent();
        event.addZone(zoneId);
        event.publish();
        return event;
    }

    private Event createPublishedEvent() {
        return createPublishedEventWithZone(ZoneId.random());
    }

    private Zone zoneWithAvailableCount(int availableCount) {
        Zone zone = mock(Zone.class);
        when(zone.getAvailableCount()).thenReturn(availableCount);
        return zone;
    }


    // ─────────────────────────────────────────────────────────────────────
    // isPurchasable
    // ─────────────────────────────────────────────────────────────────────

    // UC7 / UAT-16: A published event is considered purchasable before ticket selection/reservation.
    @Test
    void isPurchasable_whenEventIsPublished_returnsTrue_UAT16() {
        Event event = createPublishedEvent();
        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        boolean result = service.isPurchasable(event.id());

        assertThat(result).isTrue();
    }

    // UC7 / UAT-16: A draft event is not purchasable and cannot be used for reservation.
    @Test
    void isPurchasable_whenEventIsDraft_returnsFalse_UAT16() {
        Event event = createDraftEvent();
        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        boolean result = service.isPurchasable(event.id());

        assertThat(result).isFalse();
    }

    // UC7 / UAT-16: A cancelled event is not purchasable and cannot be used for reservation.
    @Test
    void isPurchasable_whenEventIsCancelled_returnsFalse_UAT16() {
        Event event = createDraftEvent();
        event.cancel();
        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        boolean result = service.isPurchasable(event.id());

        assertThat(result).isFalse();
    }

    @Test
    void isPurchasable_eventNotFound_throws() {
        EventId unknownId = EventId.random();
        when(eventRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.isPurchasable(unknownId));

        verifyNoInteractions(zoneRepository);
        verifyNoInteractions(companyServicePort);
    }

    @Test
    void isPurchasable_nullEventId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.isPurchasable(null));

        verifyNoInteractions(eventRepository);
        verifyNoInteractions(zoneRepository);
        verifyNoInteractions(companyServicePort);
    }

    // ─────────────────────────────────────────────────────────────────────
    // requirePurchasable
    // ─────────────────────────────────────────────────────────────────────

    // UC7 / UAT-16: Allow reservation flow to continue when the event is published and purchasable.
    @Test
    void requirePurchasable_whenPublished_doesNotThrow_UAT16() {
        Event event = createPublishedEvent();
        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        assertThatCode(() -> service.requirePurchasable(event.id()))
                .doesNotThrowAnyException();
    }

    // UC7 / UAT-22: Reject reservation flow when the event is not published/purchasable.
    @Test
    void requirePurchasable_whenDraft_throws_UAT22() {
        Event event = createDraftEvent();
        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> service.requirePurchasable(event.id()))
                .isInstanceOf(EventDomainException.class);
    }

    @Test
    void requirePurchasable_eventNotFound_throws() {
        EventId unknownId = EventId.random();
        when(eventRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.requirePurchasable(unknownId));

        verifyNoInteractions(zoneRepository);
        verifyNoInteractions(companyServicePort);
    }

    @Test
    void requirePurchasable_nullEventId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.requirePurchasable(null));

        verifyNoInteractions(eventRepository);
        verifyNoInteractions(zoneRepository);
        verifyNoInteractions(companyServicePort);
    }

    // ─────────────────────────────────────────────────────────────────────
    // areAllZonesFull
    // ─────────────────────────────────────────────────────────────────────

    // UC3 / UAT-09: Detect sold out while queued/reserving when all related zones have no availability.
    @Test
    void areAllZonesFull_whenAllZonesFull_returnsTrue_UAT09() {
        EventId eventId = EventId.random();
        Zone zone1 = zoneWithAvailableCount(0);
        Zone zone2 = zoneWithAvailableCount(0);
        when(zoneRepository.findByEventId(eventId)).thenReturn(List.of(zone1, zone2));

        boolean result = service.areAllZonesFull(eventId);

        assertThat(result).isTrue();
        verifyNoInteractions(eventRepository);
        verifyNoInteractions(companyServicePort);
    }

    // UC7 / UAT-16: Event still has availability when at least one zone has capacity.
    @Test
    void areAllZonesFull_whenOneZoneHasAvailability_returnsFalse_UAT16() {
        EventId eventId = EventId.random();
        Zone zone1 = zoneWithAvailableCount(0);
        Zone zone2 = zoneWithAvailableCount(5);
        when(zoneRepository.findByEventId(eventId)).thenReturn(List.of(zone1, zone2));

        boolean result = service.areAllZonesFull(eventId);

        assertThat(result).isFalse();
        verifyNoInteractions(eventRepository);
        verifyNoInteractions(companyServicePort);
    }

    @Test
    void areAllZonesFull_whenNoZones_returnsFalse() {
        EventId eventId = EventId.random();
        when(zoneRepository.findByEventId(eventId)).thenReturn(List.of());

        boolean result = service.areAllZonesFull(eventId);

        assertThat(result).isFalse();
        verifyNoInteractions(eventRepository);
        verifyNoInteractions(companyServicePort);
    }

    @Test
    void areAllZonesFull_nullEventId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.areAllZonesFull(null));

        verifyNoInteractions(eventRepository);
        verifyNoInteractions(zoneRepository);
        verifyNoInteractions(companyServicePort);
    }

    // ─────────────────────────────────────────────────────────────────────
    // markSoldOutIfAllZonesFull
    // ─────────────────────────────────────────────────────────────────────

    // UC3 / UAT-09: Mark event as sold out when all zones are full.
    @Test
    void markSoldOutIfAllZonesFull_whenAllZonesFull_marksSoldOutAndSaves_UAT09() {
        Event event = createPublishedEvent();
        Zone zone1 = zoneWithAvailableCount(0);
        Zone zone2 = zoneWithAvailableCount(0);

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));
        when(zoneRepository.findByEventId(event.id())).thenReturn(List.of(zone1, zone2));

        service.markSoldOutIfAllZonesFull(event.id());

        assertThat(event.isSoldOut()).isTrue();
        verify(eventRepository).save(event);
        verifyNoInteractions(companyServicePort);
    }

    // UC7 / UAT-16: Do not mark sold out when at least one zone still has availability.
    @Test
    void markSoldOutIfAllZonesFull_whenNotAllZonesFull_doesNotSave_UAT16() {
        Event event = createPublishedEvent();
        Zone zone1 = zoneWithAvailableCount(0);
        Zone zone2 = zoneWithAvailableCount(2);

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));
        when(zoneRepository.findByEventId(event.id())).thenReturn(List.of(zone1, zone2));

        service.markSoldOutIfAllZonesFull(event.id());

        assertThat(event.isPublished()).isTrue();
        verify(eventRepository, never()).save(any());
        verifyNoInteractions(companyServicePort);
    }

    @Test
    void markSoldOutIfAllZonesFull_whenNoZones_doesNotSave() {
        Event event = createPublishedEvent();
        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));
        when(zoneRepository.findByEventId(event.id())).thenReturn(List.of());

        service.markSoldOutIfAllZonesFull(event.id());

        assertThat(event.isPublished()).isTrue();
        verify(eventRepository, never()).save(any());
        verifyNoInteractions(companyServicePort);
    }

    @Test
    void markSoldOutIfAllZonesFull_whenEventIsDraft_doesNotCheckZonesOrSave() {
        Event event = createDraftEvent();
        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        service.markSoldOutIfAllZonesFull(event.id());

        assertThat(event.isDraft()).isTrue();
        verifyNoInteractions(zoneRepository);
        verify(eventRepository, never()).save(any());
        verifyNoInteractions(companyServicePort);
    }

    @Test
    void markSoldOutIfAllZonesFull_eventNotFound_throws() {
        EventId unknownId = EventId.random();
        when(eventRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.markSoldOutIfAllZonesFull(unknownId));

        verifyNoInteractions(zoneRepository);
        verify(eventRepository, never()).save(any());
        verifyNoInteractions(companyServicePort);
    }

    @Test
    void markSoldOutIfAllZonesFull_nullEventId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.markSoldOutIfAllZonesFull(null));

        verifyNoInteractions(eventRepository);
        verifyNoInteractions(zoneRepository);
        verifyNoInteractions(companyServicePort);
    }

    // ─────────────────────────────────────────────────────────────────────
    // getZonesOfEvent
    // ─────────────────────────────────────────────────────────────────────

    // UC7 / UAT-16: Event adapter exposes event zones for purchase/order orchestration.
    @Test
    void getZonesOfEvent_returnsEventZones_UAT16() {
        Event event = createDraftEvent();
        ZoneId zone1 = ZoneId.random();
        ZoneId zone2 = ZoneId.random();
        event.addZone(zone1);
        event.addZone(zone2);
        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        assertThat(service.getZonesOfEvent(event.id()))
                .containsExactlyInAnyOrder(zone1, zone2);

        verifyNoInteractions(zoneRepository);
        verifyNoInteractions(companyServicePort);
    }

    @Test
    void getZonesOfEvent_eventNotFound_throws() {
        EventId unknownId = EventId.random();
        when(eventRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.getZonesOfEvent(unknownId));
    }

    @Test
    void getZonesOfEvent_nullEventId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.getZonesOfEvent(null));

        verifyNoInteractions(eventRepository);
        verifyNoInteractions(zoneRepository);
        verifyNoInteractions(companyServicePort);
    }

    // ─────────────────────────────────────────────────────────────────────
    // getEventForSnapshot
    // ─────────────────────────────────────────────────────────────────────

    // UC9 / UAT-26: Load the event so checkout can create a purchase-record snapshot.
    @Test
    void getEventForSnapshot_returnsLoadedEvent_UAT26() {
        Event event = createDraftEvent();
        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        Event result = service.getEventForSnapshot(event.id());

        assertThat(result).isSameAs(event);
        verifyNoInteractions(zoneRepository);
        verifyNoInteractions(companyServicePort);
    }

    @Test
    void getEventForSnapshot_eventNotFound_throws() {
        EventId unknownId = EventId.random();
        when(eventRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.getEventForSnapshot(unknownId));

        verifyNoInteractions(zoneRepository);
        verifyNoInteractions(companyServicePort);
    }

    @Test
    void getEventForSnapshot_nullEventId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.getEventForSnapshot(null));

        verifyNoInteractions(eventRepository);
        verifyNoInteractions(zoneRepository);
        verifyNoInteractions(companyServicePort);
    }

    // ─────────────────────────────────────────────────────────────────────
    // requireZoneBelongsToEvent
    // ─────────────────────────────────────────────────────────────────────

    // UC7 / UAT-16: Allow ticket selection when selected zone belongs to the event.
    @Test
    void requireZoneBelongsToEvent_whenZoneBelongs_doesNotThrow_UAT16() {
        Event event = createDraftEvent();
        ZoneId zoneId = ZoneId.random();
        event.addZone(zoneId);
        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        assertThatCode(() -> service.requireZoneBelongsToEvent(event.id(), zoneId))
                .doesNotThrowAnyException();

        verifyNoInteractions(zoneRepository);
        verifyNoInteractions(companyServicePort);
    }

    // UC7 / UAT-22: Reject ticket selection when selected zone does not belong to the event.
    @Test
    void requireZoneBelongsToEvent_whenZoneDoesNotBelong_throws_UAT22() {
        Event event = createDraftEvent();
        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> service.requireZoneBelongsToEvent(event.id(), ZoneId.random()))
                .isInstanceOf(EventDomainException.class);

        verifyNoInteractions(zoneRepository);
        verifyNoInteractions(companyServicePort);
    }

    @Test
    void requireZoneBelongsToEvent_eventNotFound_throws() {
        EventId unknownId = EventId.random();
        when(eventRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.requireZoneBelongsToEvent(unknownId, ZoneId.random()));

        verifyNoInteractions(zoneRepository);
        verifyNoInteractions(companyServicePort);
    }

    @Test
    void requireZoneBelongsToEvent_nullEventId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.requireZoneBelongsToEvent(null, ZoneId.random()));

        verifyNoInteractions(eventRepository);
        verifyNoInteractions(zoneRepository);
        verifyNoInteractions(companyServicePort);
    }

    @Test
    void requireZoneBelongsToEvent_nullZoneId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.requireZoneBelongsToEvent(EventId.random(), null));

        verifyNoInteractions(eventRepository);
        verifyNoInteractions(zoneRepository);
        verifyNoInteractions(companyServicePort);
    }

    // ─────────────────────────────────────────────────────────────────────
    // validateContextAgainstEvent
    // ─────────────────────────────────────────────────────────────────────

    // UC9 / UAT-26: Event-side validation passes when event is purchasable and zones belong to it.
    @Test
    void validateContextAgainstEvent_whenPublishedAndAllZonesBelong_returnsSuccess_UAT26() {
        ZoneId zoneId = ZoneId.random();
        Event event = createPublishedEventWithZone(zoneId);
        PurchaseContext context = new PurchaseContext(
                event.id(),
                event.companyId(),
                List.of(zoneId, zoneId),
                LocalDate.now().minusYears(25),
                null
        );
        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        PolicyValidationResult result = service.validateContextAgainstEvent(event.id(), context);

        assertThat(result.isSuccess()).isTrue();
        verifyNoInteractions(zoneRepository);
        verifyNoInteractions(companyServicePort);
    }

    // UC9 / UAT-27: Event-side validation fails before policy evaluation when event is not purchasable.
    @Test
    void validateContextAgainstEvent_whenEventIsDraft_returnsFailure_UAT27() {
        Event event = createDraftEvent();
        ZoneId zoneId = ZoneId.random();
        event.addZone(zoneId);
        PurchaseContext context = new PurchaseContext(
                event.id(),
                event.companyId(),
                List.of(zoneId),
                LocalDate.now().minusYears(25),
                null
        );
        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        PolicyValidationResult result = service.validateContextAgainstEvent(event.id(), context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.failureReason()).isPresent().get().asString().contains("Event tickets are not purchasable");

    }

    // UC9 / UAT-27: Event-side validation fails before policy evaluation when a zone is invalid for event.
    @Test
    void validateContextAgainstEvent_whenContextContainsZoneNotInEvent_returnsFailure_UAT27() {
        ZoneId realZone = ZoneId.random();
        ZoneId wrongZone = ZoneId.random();
        Event event = createPublishedEventWithZone(realZone);
        PurchaseContext context = new PurchaseContext(
                event.id(),
                event.companyId(),
                List.of(wrongZone),
                LocalDate.now().minusYears(25),
                null
        );
        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        PolicyValidationResult result = service.validateContextAgainstEvent(event.id(), context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.failureReason()).isPresent().get().asString().contains("invalid zone");
        verifyNoInteractions(zoneRepository);
        verifyNoInteractions(companyServicePort);
    }

    @Test
    void validateContextAgainstEvent_eventNotFound_throws() {
        EventId unknownId = EventId.random();
        PurchaseContext context = new PurchaseContext(
                unknownId,
                COMPANY_ID,
                List.of(ZoneId.random()),
                LocalDate.now().minusYears(25),
                null
        );
        when(eventRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.validateContextAgainstEvent(unknownId, context));
    }


    // ─────────────────────────────────────────────────────────────────────
    // getEventSnapshot
    // ─────────────────────────────────────────────────────────────────────

    // UC9 / UAT-26: Create event snapshot for the purchase record after checkout.
    @Test
    void getEventSnapshot_returnsSnapshotFromEventDetails_UAT26() {
        Event event = createPublishedEvent();
        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));
        when(companyServicePort.getCompanyName(event.companyId())).thenReturn(COMPANY_NAME);

        EventSnapshot snapshot = service.getEventSnapshot(event.id().value());

        assertThat(snapshot.eventId()).isEqualTo(event.id().value());
        assertThat(snapshot.eventName()).isEqualTo(event.details().name());
        assertThat(snapshot.companyName()).isEqualTo(COMPANY_NAME);
        assertThat(snapshot.eventDate()).isEqualTo(event.details().dates().get(0).toLocalDate());
        assertThat(snapshot.location()).isEqualTo(event.details().location());
    }

    @Test
    void getEventSnapshot_eventNotFound_throws() {
        EventId unknownId = EventId.random();
        when(eventRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.getEventSnapshot(unknownId.value()));

        verifyNoInteractions(zoneRepository);
        verifyNoInteractions(companyServicePort);
    }

    @Test
    void getEventSnapshot_nullEventId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.getEventSnapshot(null));

        verifyNoInteractions(eventRepository);
        verifyNoInteractions(zoneRepository);
        verifyNoInteractions(companyServicePort);
    }

    // ─────────────────────────────────────────────────────────────────────
    // companyOfEvent
    // ─────────────────────────────────────────────────────────────────────

    // UC9 / UAT-26: Expose event company to checkout/policy orchestration.
    @Test
    void companyOfEvent_returnsOwningCompany_UAT26() {
        Event event = createPublishedEvent();
        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        CompanyId result = service.companyOfEvent(event.id());

        assertThat(result).isEqualTo(event.companyId());
        verifyNoInteractions(zoneRepository);
        verifyNoInteractions(companyServicePort);
    }

    @Test
    void companyOfEvent_eventNotFound_throws() {
        EventId unknownId = EventId.random();
        when(eventRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.companyOfEvent(unknownId));
    }

    @Test
    void companyOfEvent_nullEventId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.companyOfEvent(null));

        verifyNoInteractions(eventRepository);
        verifyNoInteractions(zoneRepository);
        verifyNoInteractions(companyServicePort);
    }
}
