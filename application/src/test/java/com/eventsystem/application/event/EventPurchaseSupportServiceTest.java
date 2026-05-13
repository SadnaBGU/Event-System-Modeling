package com.eventsystem.application.event;

import com.eventsystem.domain.event.*;
import com.eventsystem.domain.domainexceptions.EventDomainException;
import com.eventsystem.domain.zone.Zone;
import com.eventsystem.domain.zone.ZoneId;
import com.eventsystem.domain.zone.ZoneRepository;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.BuyerType;
import com.eventsystem.domain.order.OrderItem;
import com.eventsystem.domain.purchaserecord.DiscountSnapshot;
import com.eventsystem.domain.purchaserecord.EventSnapshot;
import com.eventsystem.domain.shared.Money;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventPurchaseSupportServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private ZoneRepository zoneRepository;

    private EventPurchaseSupportService service;

    @BeforeEach
    void setUp() {
        service = new EventPurchaseSupportService(eventRepository, zoneRepository);
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

    @Test
    void isPurchasable_whenEventIsPublished_returnsTrue() {
        Event event = createPublishedEvent();

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        boolean result = service.isPurchasable(event.id());

        assertThat(result).isTrue();
    }

    @Test
    void isPurchasable_whenEventIsDraft_returnsFalse() {
        Event event = createDraftEvent();

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        boolean result = service.isPurchasable(event.id());

        assertThat(result).isFalse();
    }

    @Test
    void isPurchasable_whenEventIsCancelled_returnsFalse() {
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
    }

    @Test
    void isPurchasable_nullEventId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.isPurchasable(null));

        verifyNoInteractions(eventRepository);
        verifyNoInteractions(zoneRepository);
    }

    // ── requirePurchasable ──────────────────────────────────────────────────

    @Test
    void requirePurchasable_whenPublished_doesNotThrow() {
        Event event = createPublishedEvent();

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        assertThatCode(() -> service.requirePurchasable(event.id()))
                .doesNotThrowAnyException();
    }

    @Test
    void requirePurchasable_whenDraft_throws() {
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
    }

    @Test
    void requirePurchasable_nullEventId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.requirePurchasable(null));

        verifyNoInteractions(eventRepository);
        verifyNoInteractions(zoneRepository);
    }

    // ── areAllZonesFull ─────────────────────────────────────────────────────

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

    @Test
    void areAllZonesFull_whenNoZones_returnsFalse() {
        EventId eventId = EventId.random();

        when(zoneRepository.findByEventId(eventId)).thenReturn(List.of());

        boolean result = service.areAllZonesFull(eventId);

        assertThat(result).isFalse();
        verifyNoInteractions(eventRepository);
    }

    @Test
    void areAllZonesFull_nullEventId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.areAllZonesFull(null));

        verifyNoInteractions(eventRepository);
        verifyNoInteractions(zoneRepository);
    }

    // ── markSoldOutIfAllZonesFull ───────────────────────────────────────────

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

    @Test
    void markSoldOutIfAllZonesFull_whenNoZones_doesNotSave() {
        Event event = createPublishedEvent();

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));
        when(zoneRepository.findByEventId(event.id())).thenReturn(List.of());

        service.markSoldOutIfAllZonesFull(event.id());

        assertThat(event.isPublished()).isTrue();
        verify(eventRepository, never()).save(any());
    }

    @Test
    void markSoldOutIfAllZonesFull_whenEventIsDraft_doesNotCheckZonesOrSave() {
        Event event = createDraftEvent();

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        service.markSoldOutIfAllZonesFull(event.id());

        assertThat(event.isDraft()).isTrue();
        verifyNoInteractions(zoneRepository);
        verify(eventRepository, never()).save(any());
    }

    @Test
    void markSoldOutIfAllZonesFull_eventNotFound_throws() {
        EventId unknownId = EventId.random();

        when(eventRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.markSoldOutIfAllZonesFull(unknownId));

        verifyNoInteractions(zoneRepository);
        verify(eventRepository, never()).save(any());
    }

    @Test
    void markSoldOutIfAllZonesFull_nullEventId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.markSoldOutIfAllZonesFull(null));

        verifyNoInteractions(eventRepository);
        verifyNoInteractions(zoneRepository);
    }

    // ── getEventForSnapshot ─────────────────────────────────────────────────

    @Test
    void getEventForSnapshot_returnsLoadedEvent() {
        Event event = createDraftEvent();

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        Event result = service.getEventForSnapshot(event.id());

        assertThat(result).isSameAs(event);
    }

    @Test
    void getEventForSnapshot_eventNotFound_throws() {
        EventId unknownId = EventId.random();

        when(eventRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.getEventForSnapshot(unknownId));

        verifyNoInteractions(zoneRepository);
    }

    @Test
    void getEventForSnapshot_nullEventId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.getEventForSnapshot(null));

        verifyNoInteractions(eventRepository);
        verifyNoInteractions(zoneRepository);
    }

    // ── requireZoneBelongsToEvent ───────────────────────────────────────────

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

    @Test
    void requireZoneBelongsToEvent_whenZoneDoesNotBelong_throws() {
        Event event = createDraftEvent();

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> service.requireZoneBelongsToEvent(event.id(), ZoneId.random()))
                .isInstanceOf(EventDomainException.class);

        verifyNoInteractions(zoneRepository);
    }

    @Test
    void requireZoneBelongsToEvent_eventNotFound_throws() {
        EventId unknownId = EventId.random();

        when(eventRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.requireZoneBelongsToEvent(unknownId, ZoneId.random()));

        verifyNoInteractions(zoneRepository);
    }

    @Test
    void requireZoneBelongsToEvent_nullEventId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.requireZoneBelongsToEvent(null, ZoneId.random()));

        verifyNoInteractions(eventRepository);
        verifyNoInteractions(zoneRepository);
    }

    @Test
    void requireZoneBelongsToEvent_nullZoneId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.requireZoneBelongsToEvent(EventId.random(), null));

        verifyNoInteractions(eventRepository);
        verifyNoInteractions(zoneRepository);
    }

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

    @Test
    void applyDiscount_withoutDiscountPolicy_returnsZeroDiscount() {
        Event event = createPublishedEvent();
        Money baseTotal = Money.of(BigDecimal.valueOf(100), "ILS");

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        DiscountSnapshot result = service.applyDiscount(event.id().value(), null, baseTotal);

        assertThat(result.discountName()).isEqualTo("NO_DISCOUNT");
        assertThat(result.discountAmount()).isEqualTo(Money.of(BigDecimal.ZERO, "ILS"));
    }

    @Test
    void getEventSnapshot_returnsSnapshotFromEventDetails() {
        Event event = createPublishedEvent();

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        EventSnapshot snapshot = service.getEventSnapshot(event.id().value());

        assertThat(snapshot.eventId()).isEqualTo(event.id().value());
        assertThat(snapshot.eventName()).isEqualTo(event.details().name());
        assertThat(snapshot.companyName()).isEqualTo(event.companyId());
        assertThat(snapshot.eventDate()).isEqualTo(event.details().dates().get(0).toLocalDate());
        assertThat(snapshot.location()).isEqualTo(event.details().location());
    }
}