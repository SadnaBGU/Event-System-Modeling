package com.eventsystem.application.event;

import com.eventsystem.domain.event.*;
import com.eventsystem.domain.domainexceptions.EventDomainException;
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

    private static final String ACTOR_ID = "actor-1";
    private static final String COMPANY_ID = "company-1";

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventPermissionChecker permissionChecker;

    private EventService service;

    @BeforeEach
    void setUp() {
        service = new EventService(eventRepository, permissionChecker);
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
                COMPANY_ID,
                defaultDetails(),
                VenueMap.empty()
        );
    }

    private Event createPublishableDraftEvent() {
        Event event = createDraftEvent();
        event.addZone(ZoneId.random());
        return event;
    }

    private void allowManageEvents() {
        when(permissionChecker.canManageEvents(ACTOR_ID, COMPANY_ID)).thenReturn(true);
    }

    private void denyManageEvents() {
        when(permissionChecker.canManageEvents(ACTOR_ID, COMPANY_ID)).thenReturn(false);
    }

    // ── createDraft ─────────────────────────────────────────────────────────

    @Test
    void createDraft_actorHasPermission_savesEventAndReturnsId() {
        EventDetails details = defaultDetails();
        VenueMap venueMap = VenueMap.empty();
        allowManageEvents();

        EventId eventId = service.createDraft(ACTOR_ID, COMPANY_ID, details, venueMap);

        assertThat(eventId).isNotNull();

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository).save(eventCaptor.capture());

        Event savedEvent = eventCaptor.getValue();
        assertThat(savedEvent.id()).isEqualTo(eventId);
        assertThat(savedEvent.companyId()).isEqualTo(COMPANY_ID);
        assertThat(savedEvent.details()).isEqualTo(details);
        assertThat(savedEvent.venueMap()).isEqualTo(venueMap);
        assertThat(savedEvent.status()).isEqualTo(EventStatus.DRAFT);

        verify(permissionChecker).canManageEvents(ACTOR_ID, COMPANY_ID);
    }

    @Test
    void createDraft_actorWithoutPermission_throwsAndDoesNotSave() {
        denyManageEvents();

        assertThatThrownBy(() -> service.createDraft(ACTOR_ID, COMPANY_ID, defaultDetails(), VenueMap.empty()))
                .isInstanceOf(SecurityException.class);

        verify(eventRepository, never()).save(any());
        verify(permissionChecker).canManageEvents(ACTOR_ID, COMPANY_ID);
    }

    @Test
    void createDraft_nullActorId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.createDraft(null, COMPANY_ID, defaultDetails(), VenueMap.empty()));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    @Test
    void createDraft_blankActorId_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.createDraft("   ", COMPANY_ID, defaultDetails(), VenueMap.empty()));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    @Test
    void createDraft_nullCompanyId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.createDraft(ACTOR_ID, null, defaultDetails(), VenueMap.empty()));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    @Test
    void createDraft_nullDetails_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.createDraft(ACTOR_ID, COMPANY_ID, null, VenueMap.empty()));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    @Test
    void createDraft_nullVenueMap_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.createDraft(ACTOR_ID, COMPANY_ID, defaultDetails(), null));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    // ── updateDetails ───────────────────────────────────────────────────────

    @Test
    void updateDetails_actorHasPermission_delegatesToEventAndSaves() {
        Event event = createDraftEvent();
        EventDetails newDetails = new EventDetails(
                "Updated Concert",
                List.of(LocalDateTime.now().plusDays(20)),
                "Rock",
                "Jerusalem",
                "Updated description"
        );

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));
        allowManageEvents();

        service.updateDetails(ACTOR_ID, event.id(), newDetails);

        assertThat(event.details()).isEqualTo(newDetails);
        verify(eventRepository).save(event);
        verify(permissionChecker).canManageEvents(ACTOR_ID, COMPANY_ID);
    }

    @Test
    void updateDetails_actorWithoutPermission_throwsAndDoesNotSave() {
        Event event = createDraftEvent();
        EventDetails newDetails = new EventDetails(
                "Updated Concert",
                List.of(LocalDateTime.now().plusDays(20)),
                "Rock",
                "Jerusalem",
                "Updated description"
        );

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));
        denyManageEvents();

        assertThatThrownBy(() -> service.updateDetails(ACTOR_ID, event.id(), newDetails))
                .isInstanceOf(SecurityException.class);

        verify(eventRepository, never()).save(any());
        verify(permissionChecker).canManageEvents(ACTOR_ID, COMPANY_ID);
    }

    @Test
    void updateDetails_eventNotFound_throws() {
        EventId unknownId = EventId.random();
        when(eventRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.updateDetails(ACTOR_ID, unknownId, defaultDetails()));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    @Test
    void updateDetails_nullActorId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.updateDetails(null, EventId.random(), defaultDetails()));

        verify(eventRepository, never()).save(any());
    }

    @Test
    void updateDetails_nullEventId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.updateDetails(ACTOR_ID, null, defaultDetails()));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    @Test
    void updateDetails_nullNewDetails_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.updateDetails(ACTOR_ID, EventId.random(), null));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    // ── updateVenueMap ──────────────────────────────────────────────────────

    @Test
    void updateVenueMap_actorHasPermission_delegatesToEventAndSaves() {
        Event event = createDraftEvent();
        VenueMap newVenueMap = VenueMap.empty();

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));
        allowManageEvents();

        service.updateVenueMap(ACTOR_ID, event.id(), newVenueMap);

        assertThat(event.venueMap()).isEqualTo(newVenueMap);
        verify(eventRepository).save(event);
        verify(permissionChecker).canManageEvents(ACTOR_ID, COMPANY_ID);
    }

    @Test
    void updateVenueMap_actorWithoutPermission_throwsAndDoesNotSave() {
        Event event = createDraftEvent();

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));
        denyManageEvents();

        assertThatThrownBy(() -> service.updateVenueMap(ACTOR_ID, event.id(), VenueMap.empty()))
                .isInstanceOf(SecurityException.class);

        verify(eventRepository, never()).save(any());
        verify(permissionChecker).canManageEvents(ACTOR_ID, COMPANY_ID);
    }

    @Test
    void updateVenueMap_eventNotFound_throws() {
        EventId unknownId = EventId.random();
        when(eventRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.updateVenueMap(ACTOR_ID, unknownId, VenueMap.empty()));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    @Test
    void updateVenueMap_nullActorId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.updateVenueMap(null, EventId.random(), VenueMap.empty()));

        verify(eventRepository, never()).save(any());
    }

    @Test
    void updateVenueMap_nullEventId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.updateVenueMap(ACTOR_ID, null, VenueMap.empty()));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    @Test
    void updateVenueMap_nullNewVenueMap_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.updateVenueMap(ACTOR_ID, EventId.random(), null));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    // ── addZone / removeZone ────────────────────────────────────────────────

    @Test
    void addZone_actorHasPermission_delegatesToEventAndSaves() {
        Event event = createDraftEvent();
        ZoneId zoneId = ZoneId.random();

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));
        allowManageEvents();

        service.addZone(ACTOR_ID, event.id(), zoneId);

        assertThat(event.zoneIds()).contains(zoneId);
        verify(eventRepository).save(event);
        verify(permissionChecker).canManageEvents(ACTOR_ID, COMPANY_ID);
    }

    @Test
    void addZone_actorWithoutPermission_throwsAndDoesNotSave() {
        Event event = createDraftEvent();

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));
        denyManageEvents();

        assertThatThrownBy(() -> service.addZone(ACTOR_ID, event.id(), ZoneId.random()))
                .isInstanceOf(SecurityException.class);

        verify(eventRepository, never()).save(any());
        verify(permissionChecker).canManageEvents(ACTOR_ID, COMPANY_ID);
    }

    @Test
    void addZone_eventNotFound_throws() {
        EventId unknownId = EventId.random();

        when(eventRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.addZone(ACTOR_ID, unknownId, ZoneId.random()));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    @Test
    void addZone_nullActorId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.addZone(null, EventId.random(), ZoneId.random()));

        verify(eventRepository, never()).save(any());
    }

    @Test
    void addZone_nullEventId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.addZone(ACTOR_ID, null, ZoneId.random()));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    @Test
    void addZone_nullZoneId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.addZone(ACTOR_ID, EventId.random(), null));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    @Test
    void addZone_duplicateZone_doesNotSave() {
        Event event = createDraftEvent();
        ZoneId zoneId = ZoneId.random();
        event.addZone(zoneId);

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));
        allowManageEvents();

        assertThatThrownBy(() -> service.addZone(ACTOR_ID, event.id(), zoneId))
                .isInstanceOf(EventDomainException.class);

        verify(eventRepository, never()).save(any());
        verify(permissionChecker).canManageEvents(ACTOR_ID, COMPANY_ID);
    }

    @Test
    void removeZone_actorHasPermission_delegatesToEventAndSaves() {
        Event event = createDraftEvent();
        ZoneId zoneId = ZoneId.random();
        event.addZone(zoneId);

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));
        allowManageEvents();

        service.removeZone(ACTOR_ID, event.id(), zoneId);

        assertThat(event.zoneIds()).doesNotContain(zoneId);
        verify(eventRepository).save(event);
        verify(permissionChecker).canManageEvents(ACTOR_ID, COMPANY_ID);
    }

    @Test
    void removeZone_actorWithoutPermission_throwsAndDoesNotSave() {
        Event event = createDraftEvent();
        ZoneId zoneId = ZoneId.random();
        event.addZone(zoneId);

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));
        denyManageEvents();

        assertThatThrownBy(() -> service.removeZone(ACTOR_ID, event.id(), zoneId))
                .isInstanceOf(SecurityException.class);

        verify(eventRepository, never()).save(any());
        verify(permissionChecker).canManageEvents(ACTOR_ID, COMPANY_ID);
    }

    @Test
    void removeZone_eventNotFound_throws() {
        EventId unknownId = EventId.random();

        when(eventRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.removeZone(ACTOR_ID, unknownId, ZoneId.random()));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    @Test
    void removeZone_nullActorId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.removeZone(null, EventId.random(), ZoneId.random()));

        verify(eventRepository, never()).save(any());
    }

    @Test
    void removeZone_nullEventId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.removeZone(ACTOR_ID, null, ZoneId.random()));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    @Test
    void removeZone_nullZoneId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.removeZone(ACTOR_ID, EventId.random(), null));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    @Test
    void removeZone_missingZone_doesNotSave() {
        Event event = createDraftEvent();
        ZoneId missingZoneId = ZoneId.random();

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));
        allowManageEvents();

        assertThatThrownBy(() -> service.removeZone(ACTOR_ID, event.id(), missingZoneId))
                .isInstanceOf(EventDomainException.class);

        verify(eventRepository, never()).save(any());
        verify(permissionChecker).canManageEvents(ACTOR_ID, COMPANY_ID);
    }

    // ── publish / cancel ────────────────────────────────────────────────────

    @Test
    void publish_actorHasPermission_delegatesToEventAndSaves() {
        Event event = createPublishableDraftEvent();

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));
        allowManageEvents();

        service.publish(ACTOR_ID, event.id());

        assertThat(event.status()).isEqualTo(EventStatus.PUBLISHED);
        assertThat(event.isPublished()).isTrue();
        verify(eventRepository).save(event);
        verify(permissionChecker).canManageEvents(ACTOR_ID, COMPANY_ID);
    }

    @Test
    void publish_actorWithoutPermission_throwsAndDoesNotSave() {
        Event event = createPublishableDraftEvent();

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));
        denyManageEvents();

        assertThatThrownBy(() -> service.publish(ACTOR_ID, event.id()))
                .isInstanceOf(SecurityException.class);

        verify(eventRepository, never()).save(any());
        verify(permissionChecker).canManageEvents(ACTOR_ID, COMPANY_ID);
    }

    @Test
    void publish_eventNotFound_throws() {
        EventId unknownId = EventId.random();

        when(eventRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.publish(ACTOR_ID, unknownId));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    @Test
    void publish_nullActorId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.publish(null, EventId.random()));

        verify(eventRepository, never()).save(any());
    }

    @Test
    void publish_blankActorId_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.publish("   ", EventId.random()));

        verifyNoInteractions(eventRepository);
        verifyNoInteractions(permissionChecker);
    }

    @Test
    void publish_nullEventId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.publish(ACTOR_ID, null));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    @Test
    void publish_whenEventHasNoZones_doesNotSave() {
        Event event = createDraftEvent();

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));
        allowManageEvents();

        assertThatThrownBy(() -> service.publish(ACTOR_ID, event.id()))
                .isInstanceOf(EventDomainException.class);

        verify(eventRepository, never()).save(any());
        verify(permissionChecker).canManageEvents(ACTOR_ID, COMPANY_ID);
    }

    @Test
    void publish_whenEventIsCancelled_doesNotSave() {
        Event event = createPublishableDraftEvent();
        event.cancel();

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));
        allowManageEvents();

        assertThatThrownBy(() -> service.publish(ACTOR_ID, event.id()))
                .isInstanceOf(EventDomainException.class);

        verify(eventRepository, never()).save(any());
        verify(permissionChecker).canManageEvents(ACTOR_ID, COMPANY_ID);
    }

    @Test
    void cancel_actorHasPermission_delegatesToEventAndSaves() {
        Event event = createDraftEvent();

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));
        allowManageEvents();

        service.cancel(ACTOR_ID, event.id());

        assertThat(event.status()).isEqualTo(EventStatus.CANCELLED);
        assertThat(event.isCancelled()).isTrue();
        verify(eventRepository).save(event);
        verify(permissionChecker).canManageEvents(ACTOR_ID, COMPANY_ID);
    }

    @Test
    void cancel_actorWithoutPermission_throwsAndDoesNotSave() {
        Event event = createDraftEvent();

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));
        denyManageEvents();

        assertThatThrownBy(() -> service.cancel(ACTOR_ID, event.id()))
                .isInstanceOf(SecurityException.class);

        verify(eventRepository, never()).save(any());
        verify(permissionChecker).canManageEvents(ACTOR_ID, COMPANY_ID);
    }

    @Test
    void cancel_eventNotFound_throws() {
        EventId unknownId = EventId.random();

        when(eventRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.cancel(ACTOR_ID, unknownId));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    @Test
    void cancel_nullActorId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.cancel(null, EventId.random()));

        verify(eventRepository, never()).save(any());
    }

    @Test
    void cancel_nullEventId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.cancel(ACTOR_ID, null));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    // ── queries ─────────────────────────────────────────────────────────────

    @Test
    void findById_returnsEvent() {
        Event event = createDraftEvent();

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        Event result = service.findById(event.id());

        assertThat(result).isSameAs(event);
        verifyNoInteractions(permissionChecker);
    }

    @Test
    void findById_eventNotFound_throws() {
        EventId unknownId = EventId.random();

        when(eventRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.findById(unknownId));

        verifyNoInteractions(permissionChecker);
    }

    @Test
    void findById_nullEventId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.findById(null));

        verifyNoInteractions(permissionChecker);
    }

    @Test
    void findByCompany_delegatesToRepository() {
        Event event = createDraftEvent();

        when(eventRepository.findByCompany(COMPANY_ID)).thenReturn(List.of(event));

        List<Event> result = service.findByCompany(COMPANY_ID);

        assertThat(result).containsExactly(event);
        verify(eventRepository).findByCompany(COMPANY_ID);
        verifyNoInteractions(permissionChecker);
    }

    @Test
    void findByCompany_nullCompanyId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.findByCompany(null));

        verifyNoInteractions(permissionChecker);
    }

    @Test
    void findPublishedByCompany_returnsOnlyPublishedEvents() {
        Event draftEvent = createDraftEvent();

        Event publishedEvent = createPublishableDraftEvent();
        publishedEvent.publish();

        Event cancelledEvent = createDraftEvent();
        cancelledEvent.cancel();

        when(eventRepository.findByCompany(COMPANY_ID))
                .thenReturn(List.of(draftEvent, publishedEvent, cancelledEvent));

        List<Event> result = service.findPublishedByCompany(COMPANY_ID);

        assertThat(result).containsExactly(publishedEvent);
        verifyNoInteractions(permissionChecker);
    }

    @Test
    void findPublishedByCompany_whenNoEvents_returnsEmptyList() {
        when(eventRepository.findByCompany(COMPANY_ID)).thenReturn(List.of());

        List<Event> result = service.findPublishedByCompany(COMPANY_ID);

        assertThat(result).isEmpty();
        verifyNoInteractions(permissionChecker);
    }

    @Test
    void findPublishedByCompany_nullCompanyId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.findPublishedByCompany(null));

        verifyNoInteractions(permissionChecker);
    }

    // ── sales method ───────────────────────────────────────────────────────────

    @Test
    void setSalesMethod_actorHasPermission_updatesSalesMethodAndSaves() {
        Event event = createDraftEvent();

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));
        allowManageEvents();

        service.setSalesMethod(ACTOR_ID, event.id(), SalesMethod.LOTTERY);

        assertThat(event.salesMethod()).isEqualTo(SalesMethod.LOTTERY);
        assertThat(event.isMethodLottery()).isTrue();
        verify(eventRepository).save(event);
        verify(permissionChecker).canManageEvents(ACTOR_ID, COMPANY_ID);
    }

    @Test
    void setSalesMethod_actorWithoutPermission_throwsAndDoesNotSave() {
        Event event = createDraftEvent();

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));
        denyManageEvents();

        assertThatThrownBy(() -> service.setSalesMethod(ACTOR_ID, event.id(), SalesMethod.LOTTERY))
                .isInstanceOf(SecurityException.class);

        verify(eventRepository, never()).save(any());
        verify(permissionChecker).canManageEvents(ACTOR_ID, COMPANY_ID);
    }

    @Test
    void setSalesMethod_eventNotFound_throws() {
        EventId unknownId = EventId.random();

        when(eventRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.setSalesMethod(ACTOR_ID, unknownId, SalesMethod.LOTTERY));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    @Test
    void setSalesMethod_nullActorId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.setSalesMethod(null, EventId.random(), SalesMethod.LOTTERY));

        verifyNoInteractions(eventRepository);
        verifyNoInteractions(permissionChecker);
    }

    @Test
    void setSalesMethod_blankActorId_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.setSalesMethod("   ", EventId.random(), SalesMethod.LOTTERY));

        verifyNoInteractions(eventRepository);
        verifyNoInteractions(permissionChecker);
    }

    @Test
    void setSalesMethod_nullEventId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.setSalesMethod(ACTOR_ID, null, SalesMethod.LOTTERY));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    @Test
    void setSalesMethod_nullSalesMethod_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.setSalesMethod(ACTOR_ID, EventId.random(), null));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    @Test
    void setMethodRegular_setsRegularAndSaves() {
        Event event = createDraftEvent();
        event.setMethodLottery();

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));
        allowManageEvents();

        service.setMethodRegular(ACTOR_ID, event.id());

        assertThat(event.salesMethod()).isEqualTo(SalesMethod.REGULAR);
        assertThat(event.isMethodSale()).isTrue();
        verify(eventRepository).save(event);
    }

    @Test
    void setMethodQueue_setsVirtualQueueAndSaves() {
        Event event = createDraftEvent();

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));
        allowManageEvents();

        service.setMethodQueue(ACTOR_ID, event.id());

        assertThat(event.salesMethod()).isEqualTo(SalesMethod.VIRTUAL_QUEUE);
        assertThat(event.isMethodQueue()).isTrue();
        verify(eventRepository).save(event);
    }

    @Test
    void setMethodLottery_setsLotteryAndSaves() {
        Event event = createDraftEvent();

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));
        allowManageEvents();

        service.setMethodLottery(ACTOR_ID, event.id());

        assertThat(event.salesMethod()).isEqualTo(SalesMethod.LOTTERY);
        assertThat(event.isMethodLottery()).isTrue();
        verify(eventRepository).save(event);
    }

    // ── over ───────────────────────────────────────────────────────────────────

    @Test
    void eventOver_actorHasPermission_marksEventOverAndSaves() {
        Event event = createPublishableDraftEvent();
        event.publish();

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));
        allowManageEvents();

        service.eventOver(ACTOR_ID, event.id());

        assertThat(event.status()).isEqualTo(EventStatus.OVER);
        assertThat(event.isOver()).isTrue();
        verify(eventRepository).save(event);
        verify(permissionChecker).canManageEvents(ACTOR_ID, COMPANY_ID);
    }

    @Test
    void eventOver_actorWithoutPermission_throwsAndDoesNotSave() {
        Event event = createPublishableDraftEvent();
        event.publish();

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));
        denyManageEvents();

        assertThatThrownBy(() -> service.eventOver(ACTOR_ID, event.id()))
                .isInstanceOf(SecurityException.class);

        verify(eventRepository, never()).save(any());
        verify(permissionChecker).canManageEvents(ACTOR_ID, COMPANY_ID);
    }

    @Test
    void eventOver_eventNotFound_throws() {
        EventId unknownId = EventId.random();

        when(eventRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.eventOver(ACTOR_ID, unknownId));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    @Test
    void eventOver_whenDomainRejects_doesNotSave() {
        Event event = createDraftEvent();

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));
        allowManageEvents();

        assertThatThrownBy(() -> service.eventOver(ACTOR_ID, event.id()))
                .isInstanceOf(EventDomainException.class);

        verify(eventRepository, never()).save(any());
        verify(permissionChecker).canManageEvents(ACTOR_ID, COMPANY_ID);
    }

    @Test
    void eventOver_nullActorId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.eventOver(null, EventId.random()));

        verifyNoInteractions(eventRepository);
        verifyNoInteractions(permissionChecker);
    }

    @Test
    void eventOver_blankActorId_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.eventOver("   ", EventId.random()));

        verifyNoInteractions(eventRepository);
        verifyNoInteractions(permissionChecker);
    }

    @Test
    void eventOver_nullEventId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.eventOver(ACTOR_ID, null));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }
}