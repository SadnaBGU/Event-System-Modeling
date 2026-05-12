package com.eventsystem.application.event;

import com.eventsystem.domain.event.*;
import com.eventsystem.domain.zone.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    private EventService service;

    @BeforeEach
    void setUp() {
        service = new EventService(eventRepository);
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
        return Event.createDraft(
                "company-1",
                defaultDetails(),
                VenueMap.empty()
        );
    }

    private Event createPublishableDraftEvent() {
        Event event = createDraftEvent();
        event.addZone(ZoneId.random());
        return event;
    }

    // ── createDraft ─────────────────────────────────────────────────────────

    @Test
    void createDraft_savesEventAndReturnsId() {
        EventDetails details = defaultDetails();
        VenueMap venueMap = VenueMap.empty();

        EventId eventId = service.createDraft("company-1", details, venueMap);

        assertThat(eventId).isNotNull();

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository).save(eventCaptor.capture());

        Event savedEvent = eventCaptor.getValue();
        assertThat(savedEvent.id()).isEqualTo(eventId);
        assertThat(savedEvent.companyId()).isEqualTo("company-1");
        assertThat(savedEvent.details()).isEqualTo(details);
        assertThat(savedEvent.venueMap()).isEqualTo(venueMap);
        assertThat(savedEvent.status()).isEqualTo(EventStatus.DRAFT);
    }

    @Test
    void createDraft_nullCompanyId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.createDraft(null, defaultDetails(), VenueMap.empty()));

        verify(eventRepository, never()).save(any());
    }

    @Test
    void createDraft_nullDetails_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.createDraft("company-1", null, VenueMap.empty()));

        verify(eventRepository, never()).save(any());
    }

    @Test
    void createDraft_nullVenueMap_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.createDraft("company-1", defaultDetails(), null));

        verify(eventRepository, never()).save(any());
    }

    // ── updateDetails ───────────────────────────────────────────────────────

    @Test
    void updateDetails_delegatesToEventAndSaves() {
        Event event = createDraftEvent();
        EventDetails newDetails = new EventDetails(
                "Updated Concert",
                List.of(LocalDateTime.now().plusDays(20)),
                "Rock",
                "Jerusalem",
                "Updated description"
        );

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        service.updateDetails(event.id(), newDetails);

        assertThat(event.details()).isEqualTo(newDetails);
        verify(eventRepository).save(event);
    }

    @Test
    void updateDetails_eventNotFound_throws() {
        EventId unknownId = EventId.random();
        when(eventRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.updateDetails(unknownId, defaultDetails()));

        verify(eventRepository, never()).save(any());
    }

    @Test
    void updateDetails_nullEventId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.updateDetails(null, defaultDetails()));

        verify(eventRepository, never()).save(any());
    }

    @Test
    void updateDetails_nullNewDetails_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.updateDetails(EventId.random(), null));

        verify(eventRepository, never()).save(any());
    }

    // ── updateVenueMap ──────────────────────────────────────────────────────

    @Test
    void updateVenueMap_delegatesToEventAndSaves() {
        Event event = createDraftEvent();
        VenueMap newVenueMap = VenueMap.empty();

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        service.updateVenueMap(event.id(), newVenueMap);

        assertThat(event.venueMap()).isEqualTo(newVenueMap);
        verify(eventRepository).save(event);
    }

    @Test
    void updateVenueMap_eventNotFound_throws() {
        EventId unknownId = EventId.random();
        when(eventRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.updateVenueMap(unknownId, VenueMap.empty()));

        verify(eventRepository, never()).save(any());
    }

    @Test
    void updateVenueMap_nullEventId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.updateVenueMap(null, VenueMap.empty()));

        verify(eventRepository, never()).save(any());
    }

    @Test
    void updateVenueMap_nullNewVenueMap_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.updateVenueMap(EventId.random(), null));

        verify(eventRepository, never()).save(any());
    }

    // ── addZone / removeZone ────────────────────────────────────────────────

    @Test
    void addZone_delegatesToEventAndSaves() {
        Event event = createDraftEvent();
        ZoneId zoneId = ZoneId.random();

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        service.addZone(event.id(), zoneId);

        assertThat(event.zoneIds()).contains(zoneId);
        verify(eventRepository).save(event);
    }

    @Test
    void addZone_eventNotFound_throws() {
        EventId unknownId = EventId.random();

        when(eventRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.addZone(unknownId, ZoneId.random()));

        verify(eventRepository, never()).save(any());
    }

    @Test
    void addZone_nullEventId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.addZone(null, ZoneId.random()));

        verify(eventRepository, never()).save(any());
    }

    @Test
    void addZone_nullZoneId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.addZone(EventId.random(), null));

        verify(eventRepository, never()).save(any());
    }

    @Test
    void addZone_duplicateZone_doesNotSave() {
        Event event = createDraftEvent();
        ZoneId zoneId = ZoneId.random();
        event.addZone(zoneId);

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> service.addZone(event.id(), zoneId))
                .isInstanceOf(EventDomainException.class);

        verify(eventRepository, never()).save(any());
    }

    @Test
    void removeZone_delegatesToEventAndSaves() {
        Event event = createDraftEvent();
        ZoneId zoneId = ZoneId.random();
        event.addZone(zoneId);

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        service.removeZone(event.id(), zoneId);

        assertThat(event.zoneIds()).doesNotContain(zoneId);
        verify(eventRepository).save(event);
    }

    @Test
    void removeZone_eventNotFound_throws() {
        EventId unknownId = EventId.random();

        when(eventRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.removeZone(unknownId, ZoneId.random()));

        verify(eventRepository, never()).save(any());
    }

    @Test
    void removeZone_nullEventId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.removeZone(null, ZoneId.random()));

        verify(eventRepository, never()).save(any());
    }

    @Test
    void removeZone_nullZoneId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.removeZone(EventId.random(), null));

        verify(eventRepository, never()).save(any());
    }

    @Test
    void removeZone_missingZone_doesNotSave() {
        Event event = createDraftEvent();
        ZoneId missingZoneId = ZoneId.random();

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> service.removeZone(event.id(), missingZoneId))
                .isInstanceOf(EventDomainException.class);

        verify(eventRepository, never()).save(any());
    }

    // ── publish / cancel ────────────────────────────────────────────────────

    @Test
    void publish_delegatesToEventAndSaves() {
        Event event = createPublishableDraftEvent();

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        service.publish(event.id());

        assertThat(event.status()).isEqualTo(EventStatus.PUBLISHED);
        assertThat(event.isPublished()).isTrue();
        verify(eventRepository).save(event);
    }

    @Test
    void publish_eventNotFound_throws() {
        EventId unknownId = EventId.random();

        when(eventRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.publish(unknownId));

        verify(eventRepository, never()).save(any());
    }

    @Test
    void publish_nullEventId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.publish(null));

        verify(eventRepository, never()).save(any());
    }

    @Test
    void publish_whenEventHasNoZones_doesNotSave() {
        Event event = createDraftEvent();

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> service.publish(event.id()))
                .isInstanceOf(EventDomainException.class);

        verify(eventRepository, never()).save(any());
    }

    @Test
    void publish_whenEventIsCancelled_doesNotSave() {
        Event event = createPublishableDraftEvent();
        event.cancel();

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> service.publish(event.id()))
                .isInstanceOf(EventDomainException.class);

        verify(eventRepository, never()).save(any());
    }

    @Test
    void cancel_delegatesToEventAndSaves() {
        Event event = createDraftEvent();

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        service.cancel(event.id());

        assertThat(event.status()).isEqualTo(EventStatus.CANCELLED);
        assertThat(event.isCancelled()).isTrue();
        verify(eventRepository).save(event);
    }

    @Test
    void cancel_eventNotFound_throws() {
        EventId unknownId = EventId.random();

        when(eventRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.cancel(unknownId));

        verify(eventRepository, never()).save(any());
    }

    @Test
    void cancel_nullEventId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.cancel(null));

        verify(eventRepository, never()).save(any());
    }

    // ── queries ─────────────────────────────────────────────────────────────

    @Test
    void findById_returnsEvent() {
        Event event = createDraftEvent();

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        Event result = service.findById(event.id());

        assertThat(result).isSameAs(event);
    }

    @Test
    void findById_eventNotFound_throws() {
        EventId unknownId = EventId.random();

        when(eventRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.findById(unknownId));
    }

    @Test
    void findById_nullEventId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.findById(null));
    }

    @Test
    void findByCompany_delegatesToRepository() {
        Event event = createDraftEvent();

        when(eventRepository.findByCompany("company-1")).thenReturn(List.of(event));

        List<Event> result = service.findByCompany("company-1");

        assertThat(result).containsExactly(event);
        verify(eventRepository).findByCompany("company-1");
    }

    @Test
    void findByCompany_nullCompanyId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.findByCompany(null));
    }

    @Test
    void findPublishedByCompany_returnsOnlyPublishedEvents() {
        Event draftEvent = createDraftEvent();

        Event publishedEvent = createPublishableDraftEvent();
        publishedEvent.publish();

        Event cancelledEvent = createDraftEvent();
        cancelledEvent.cancel();

        when(eventRepository.findByCompany("company-1"))
                .thenReturn(List.of(draftEvent, publishedEvent, cancelledEvent));

        List<Event> result = service.findPublishedByCompany("company-1");

        assertThat(result).containsExactly(publishedEvent);
    }

    @Test
    void findPublishedByCompany_whenNoEvents_returnsEmptyList() {
        when(eventRepository.findByCompany("company-1")).thenReturn(List.of());

        List<Event> result = service.findPublishedByCompany("company-1");

        assertThat(result).isEmpty();
    }

    @Test
    void findPublishedByCompany_nullCompanyId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.findPublishedByCompany(null));
    }
}