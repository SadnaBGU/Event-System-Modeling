package com.eventsystem.application.event;

import com.eventsystem.domain.event.*;
import com.eventsystem.application.policy.IPurchasePolicyRepository;
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

/**
 * UC15: Create and Configure Event
 *
 * Tests for UATs:
 * - UAT-41: Successful event creation with event details and venue map
 * - UAT-42: Missing required fields during event creation
 *
 * UC20: Perform Authorized Management Action
 *
 * Tests for UATs:
 * - UAT-61: Authorized manager/owner edits event details
 * - UAT-62: Unauthorized manager action is denied
 * - UAT-63: Permission is re-validated at submission time
 *
 * UC6: Search and View Event Information
 *
 * Tests for UATs:
 * - UAT-14: Search With Results
 * - UAT-15: Search Empty Results
 *
 * UC7: Ticket Selection and Reservation
 *
 * Tests for UATs:
 * - UAT-16: Successful Reservation
 * - UAT-17: Successful Lottery Reservation
 *
 * UC3: Virtual Queue and Load Management
 *
 * Tests for UATs:
 * - UAT-20: Threshold Exceeded Queue Created
 *
 * Event lifecycle coverage:
 * - Draft event creation
 * - Event details update
 * - Venue map update
 * - Zone linking and removal
 * - Publishing
 * - Cancellation
 * - Marking event as over
 * - Sales method configuration: regular, virtual queue, lottery
 */

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    private static final String ACTOR_ID = "actor-1";
    private static final String COMPANY_ID = "company-1";

    @Mock
    private IEventRepository eventRepository;

    @Mock
    private IPurchasePolicyRepository ppRepository;

    @Mock
    private EventPermissionChecker permissionChecker;
    

    private EventService service;

    @BeforeEach
    void setUp() {
        service = new EventService(eventRepository, ppRepository ,permissionChecker);
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

    // UAT-41: Successful Event Creation
    // Create a new draft event when the actor has permission to manage events.
    // ─────────────────────────────────────────────────────────────────────
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

    // UAT-62: Unauthorized Action Denied
    // Prevent event creation when the actor does not have event-management permission.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void createDraft_actorWithoutPermission_throwsAndDoesNotSave() {
        denyManageEvents();

        assertThatThrownBy(() -> service.createDraft(ACTOR_ID, COMPANY_ID, defaultDetails(), VenueMap.empty()))
                .isInstanceOf(SecurityException.class);

        verify(eventRepository, never()).save(any());
        verify(permissionChecker).canManageEvents(ACTOR_ID, COMPANY_ID);
    }

    // UAT-42: Missing Required Fields
    // Reject event creation when actor ID is null.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void createDraft_nullActorId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.createDraft(null, COMPANY_ID, defaultDetails(), VenueMap.empty()));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    // UAT-42: Missing Required Fields
    // Reject event creation when actor ID is blank.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void createDraft_blankActorId_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.createDraft("   ", COMPANY_ID, defaultDetails(), VenueMap.empty()));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    // UAT-42: Missing Required Fields
    // Reject event creation when company ID is null.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void createDraft_nullCompanyId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.createDraft(ACTOR_ID, null, defaultDetails(), VenueMap.empty()));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    // UAT-42: Missing Required Fields
    // Reject event creation when event details are null.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void createDraft_nullDetails_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.createDraft(ACTOR_ID, COMPANY_ID, null, VenueMap.empty()));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    // UAT-42: Missing Required Fields
    // Reject event creation when venue map is null.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void createDraft_nullVenueMap_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.createDraft(ACTOR_ID, COMPANY_ID, defaultDetails(), null));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    // ── updateDetails ───────────────────────────────────────────────────────

    // UAT-61: Authorized Action Success
    // Allow an authorized actor to update event details and save the event.
    // ─────────────────────────────────────────────────────────────────────
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

    // UAT-62: Unauthorized Action Denied
    // Prevent an unauthorized actor from updating event details.
    // ─────────────────────────────────────────────────────────────────────
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

    // Supporting test for UC15 / UC20
    // Reject update when the event does not exist.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void updateDetails_eventNotFound_throws() {
        EventId unknownId = EventId.random();
        when(eventRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.updateDetails(ACTOR_ID, unknownId, defaultDetails()));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    // UAT-42: Missing Required Fields
    // Reject event-details update when actor ID is null.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void updateDetails_nullActorId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.updateDetails(null, EventId.random(), defaultDetails()));

        verify(eventRepository, never()).save(any());
    }

    // UAT-42: Missing Required Fields
    // Reject event-details update when event ID is null.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void updateDetails_nullEventId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.updateDetails(ACTOR_ID, null, defaultDetails()));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    // UAT-42: Missing Required Fields
    // Reject event-details update when new details are null.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void updateDetails_nullNewDetails_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.updateDetails(ACTOR_ID, EventId.random(), null));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    // ── updateVenueMap ──────────────────────────────────────────────────────

    // UAT-61: Authorized Action Success
    // Allow an authorized actor to update the event venue map and save the event.
    // ─────────────────────────────────────────────────────────────────────
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

    // UAT-62: Unauthorized Action Denied
    // Prevent an unauthorized actor from updating the venue map.
    // ─────────────────────────────────────────────────────────────────────
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

    // Supporting test for UC15 / UC20
    // Reject venue-map update when the event does not exist.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void updateVenueMap_eventNotFound_throws() {
        EventId unknownId = EventId.random();
        when(eventRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.updateVenueMap(ACTOR_ID, unknownId, VenueMap.empty()));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    // UAT-42: Missing Required Fields
    // Reject venue-map update when actor ID is null.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void updateVenueMap_nullActorId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.updateVenueMap(null, EventId.random(), VenueMap.empty()));

        verify(eventRepository, never()).save(any());
    }

    // UAT-42: Missing Required Fields
    // Reject venue-map update when event ID is null.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void updateVenueMap_nullEventId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.updateVenueMap(ACTOR_ID, null, VenueMap.empty()));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    // UAT-42: Missing Required Fields
    // Reject venue-map update when new venue map is null.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void updateVenueMap_nullNewVenueMap_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.updateVenueMap(ACTOR_ID, EventId.random(), null));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    // ── addZone / removeZone ────────────────────────────────────────────────

    // UAT-41: Successful Event Creation
    // Add a zone to a draft event as part of event configuration.
    // ─────────────────────────────────────────────────────────────────────
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

    // UAT-62: Unauthorized Action Denied
    // Prevent an unauthorized actor from adding a zone to an event.
    // ─────────────────────────────────────────────────────────────────────
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

    // Supporting test for UC15
    // Reject adding a zone when the event does not exist.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void addZone_eventNotFound_throws() {
        EventId unknownId = EventId.random();

        when(eventRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.addZone(ACTOR_ID, unknownId, ZoneId.random()));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    // UAT-42: Missing Required Fields
    // Reject adding a zone when actor ID is null.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void addZone_nullActorId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.addZone(null, EventId.random(), ZoneId.random()));

        verify(eventRepository, never()).save(any());
    }

    // UAT-42: Missing Required Fields
    // Reject adding a zone when event ID is null.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void addZone_nullEventId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.addZone(ACTOR_ID, null, ZoneId.random()));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    // UAT-42: Missing Required Fields
    // Reject adding a zone when zone ID is null.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void addZone_nullZoneId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.addZone(ACTOR_ID, EventId.random(), null));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    // UAT-42: Missing Required Fields / Invalid Venue Map Configuration
    // Reject duplicate zone linkage during event configuration.
    // ─────────────────────────────────────────────────────────────────────
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

    // UAT-61: Authorized Action Success
    // Allow an authorized actor to remove a zone from an event.
    // ─────────────────────────────────────────────────────────────────────
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

    // UAT-62: Unauthorized Action Denied
    // Prevent an unauthorized actor from removing a zone from an event.
    // ─────────────────────────────────────────────────────────────────────
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

    // Supporting test for UC15
    // Reject removing a zone when the event does not exist.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void removeZone_eventNotFound_throws() {
        EventId unknownId = EventId.random();

        when(eventRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.removeZone(ACTOR_ID, unknownId, ZoneId.random()));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    // UAT-42: Missing Required Fields
    // Reject zone removal when actor ID is null.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void removeZone_nullActorId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.removeZone(null, EventId.random(), ZoneId.random()));

        verify(eventRepository, never()).save(any());
    }

    // UAT-42: Missing Required Fields
    // Reject zone removal when event ID is null.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void removeZone_nullEventId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.removeZone(ACTOR_ID, null, ZoneId.random()));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    // UAT-42: Missing Required Fields
    // Reject zone removal when zone ID is null.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void removeZone_nullZoneId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.removeZone(ACTOR_ID, EventId.random(), null));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    // UAT-42: Missing Required Fields / Invalid Venue Map Configuration
    // Reject removal of a zone that does not belong to the event.
    // ─────────────────────────────────────────────────────────────────────
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

    // UAT-41: Successful Event Creation
    // Publish a properly configured draft event.
    // ─────────────────────────────────────────────────────────────────────
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

    // UAT-62: Unauthorized Action Denied
    // Prevent unauthorized publishing of an event.
    // ─────────────────────────────────────────────────────────────────────
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

    // Supporting test for UC15
    // Reject publishing when the event does not exist.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void publish_eventNotFound_throws() {
        EventId unknownId = EventId.random();

        when(eventRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.publish(ACTOR_ID, unknownId));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    // UAT-42: Missing Required Fields
    // Reject publishing when actor ID is null.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void publish_nullActorId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.publish(null, EventId.random()));

        verify(eventRepository, never()).save(any());
    }

    // UAT-42: Missing Required Fields
    // Reject publishing when actor ID is blank.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void publish_blankActorId_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.publish("   ", EventId.random()));

        verifyNoInteractions(eventRepository);
        verifyNoInteractions(permissionChecker);
    }

    // UAT-42: Missing Required Fields
    // Reject publishing when event ID is null.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void publish_nullEventId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.publish(ACTOR_ID, null));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    // UAT-42: Missing Required Fields / Invalid Venue Map Configuration
    // Reject publishing when the event has no configured zones.
    // ─────────────────────────────────────────────────────────────────────
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

    // Supporting test for UC15
    // Reject publishing an event that was already cancelled.
    // ─────────────────────────────────────────────────────────────────────
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

    // UAT-61: Authorized Action Success
    // Allow an authorized actor to cancel an event.
    // ─────────────────────────────────────────────────────────────────────
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

    // UAT-62: Unauthorized Action Denied
    // Prevent an unauthorized actor from cancelling an event.
    // ─────────────────────────────────────────────────────────────────────
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

    // Supporting test for UC15
    // Reject cancelling when the event does not exist.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void cancel_eventNotFound_throws() {
        EventId unknownId = EventId.random();

        when(eventRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.cancel(ACTOR_ID, unknownId));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    // UAT-42: Missing Required Fields
    // Reject cancelling when actor ID is null.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void cancel_nullActorId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.cancel(null, EventId.random()));

        verify(eventRepository, never()).save(any());
    }

    // UAT-42: Missing Required Fields
    // Reject cancelling when event ID is null.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void cancel_nullEventId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.cancel(ACTOR_ID, null));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    // ── queries ─────────────────────────────────────────────────────────────

    // UAT-14: Search With Results
    // Find and return an existing event by ID.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void findById_returnsEvent() {
        Event event = createDraftEvent();

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));

        Event result = service.findById(event.id());

        assertThat(result).isSameAs(event);
        verifyNoInteractions(permissionChecker);
    }

    // UAT-15: Search Empty Results
    // Reject lookup when an event ID does not exist.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void findById_eventNotFound_throws() {
        EventId unknownId = EventId.random();

        when(eventRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.findById(unknownId));

        verifyNoInteractions(permissionChecker);
    }

    // Supporting validation test for UC6
    // Reject event lookup when event ID is null.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void findById_nullEventId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.findById(null));

        verifyNoInteractions(permissionChecker);
    }

    // UAT-14: Search With Results
    // Find all events that belong to a specific company.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void findByCompany_delegatesToRepository() {
        Event event = createDraftEvent();

        when(eventRepository.findByCompany(COMPANY_ID)).thenReturn(List.of(event));

        List<Event> result = service.findByCompany(COMPANY_ID);

        assertThat(result).containsExactly(event);
        verify(eventRepository).findByCompany(COMPANY_ID);
        verifyNoInteractions(permissionChecker);
    }

    // Supporting validation test for UC6
    // Reject company-event lookup when company ID is null.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void findByCompany_nullCompanyId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.findByCompany(null));

        verifyNoInteractions(permissionChecker);
    }

    // UAT-14: Search With Results
    // Return only published events for a company.
    // ─────────────────────────────────────────────────────────────────────
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

    // UAT-15: Search Empty Results
    // Return an empty result when the company has no published events.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void findPublishedByCompany_whenNoEvents_returnsEmptyList() {
        when(eventRepository.findByCompany(COMPANY_ID)).thenReturn(List.of());

        List<Event> result = service.findPublishedByCompany(COMPANY_ID);

        assertThat(result).isEmpty();
        verifyNoInteractions(permissionChecker);
    }

    // Supporting validation test for UC6
    // Reject published-event lookup when company ID is null.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void findPublishedByCompany_nullCompanyId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.findPublishedByCompany(null));

        verifyNoInteractions(permissionChecker);
    }

    // ── sales method ───────────────────────────────────────────────────────────

    // UAT-61: Authorized Action Success
    // Allow an authorized actor to configure the sales method of an event.
    // ─────────────────────────────────────────────────────────────────────
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

    // UAT-62: Unauthorized Action Denied
    // Prevent an unauthorized actor from changing the event sales method.
    // ─────────────────────────────────────────────────────────────────────
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

    // Supporting test for UC15
    // Reject sales-method update when the event does not exist.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void setSalesMethod_eventNotFound_throws() {
        EventId unknownId = EventId.random();

        when(eventRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.setSalesMethod(ACTOR_ID, unknownId, SalesMethod.LOTTERY));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    // UAT-42: Missing Required Fields
    // Reject sales-method update when actor ID is null.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void setSalesMethod_nullActorId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.setSalesMethod(null, EventId.random(), SalesMethod.LOTTERY));

        verifyNoInteractions(eventRepository);
        verifyNoInteractions(permissionChecker);
    }

    // UAT-42: Missing Required Fields
    // Reject sales-method update when actor ID is blank.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void setSalesMethod_blankActorId_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.setSalesMethod("   ", EventId.random(), SalesMethod.LOTTERY));

        verifyNoInteractions(eventRepository);
        verifyNoInteractions(permissionChecker);
    }

    // UAT-42: Missing Required Fields
    // Reject sales-method update when event ID is null.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void setSalesMethod_nullEventId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.setSalesMethod(ACTOR_ID, null, SalesMethod.LOTTERY));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    // UAT-42: Missing Required Fields
    // Reject sales-method update when sales method is null.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void setSalesMethod_nullSalesMethod_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.setSalesMethod(ACTOR_ID, EventId.random(), null));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    // UAT-16: Successful Reservation
    // Configure an event to use regular ticket sale method.
    // ─────────────────────────────────────────────────────────────────────
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

    // UAT-20: Threshold Exceeded Queue Created
    // Configure an event to use virtual queue sales method.
    // ─────────────────────────────────────────────────────────────────────
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

    // UAT-17: Successful Lottery Reservation
    // Configure an event to use lottery sales method.
    // ─────────────────────────────────────────────────────────────────────
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

    // UAT-61: Authorized Action Success
    // Allow an authorized actor to mark a published event as over.
    // ─────────────────────────────────────────────────────────────────────
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

    // UAT-62: Unauthorized Action Denied
    // Prevent an unauthorized actor from marking an event as over.
    // ─────────────────────────────────────────────────────────────────────
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

    // Supporting test for UC15
    // Reject marking event as over when the event does not exist.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void eventOver_eventNotFound_throws() {
        EventId unknownId = EventId.random();

        when(eventRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.eventOver(ACTOR_ID, unknownId));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    // Supporting test for event lifecycle rules
    // Prevent marking a draft event as over.
    // ─────────────────────────────────────────────────────────────────────
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

    // UAT-42: Missing Required Fields
    // Reject marking event as over when actor ID is null.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void eventOver_nullActorId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.eventOver(null, EventId.random()));

        verifyNoInteractions(eventRepository);
        verifyNoInteractions(permissionChecker);
    }

    // UAT-42: Missing Required Fields
    // Reject marking event as over when actor ID is blank.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void eventOver_blankActorId_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.eventOver("   ", EventId.random()));

        verifyNoInteractions(eventRepository);
        verifyNoInteractions(permissionChecker);
    }

    // UAT-42: Missing Required Fields
    // Reject marking event as over when event ID is null.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void eventOver_nullEventId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.eventOver(ACTOR_ID, null));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    // UAT-42: Missing Required Fields
    // Reject draft creation when required event details are missing.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void createDraft_missingRequiredDetails_throwsAndDoesNotSave() {
        assertThatThrownBy(() -> service.createDraft(ACTOR_ID, COMPANY_ID, null, VenueMap.empty()))
                .isInstanceOf(NullPointerException.class);

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    // UAT-63: Permission Revoked Mid-Action
    // Re-check permission before submission and deny the update if permission was revoked.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void updateDetails_permissionRevokedBeforeSubmission_throwsAndDoesNotSave() {
        Event event = createDraftEvent();

        EventDetails newDetails = new EventDetails(
                "Updated Event",
                List.of(LocalDateTime.now().plusDays(20)),
                "Music",
                "Tel Aviv",
                "Updated description"
        );

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));
        when(permissionChecker.canManageEvents(ACTOR_ID, COMPANY_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.updateDetails(ACTOR_ID, event.id(), newDetails))
                .isInstanceOf(SecurityException.class);

        verify(eventRepository, never()).save(any());
        verify(permissionChecker).canManageEvents(ACTOR_ID, COMPANY_ID);
    }

    // UAT-41: Successful Event Creation
    // Publish a fully configured event successfully.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void publish_successfulConfiguredEvent_savesPublishedEvent() {
        Event event = createPublishableDraftEvent();

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));
        allowManageEvents();

        service.publish(ACTOR_ID, event.id());

        assertThat(event.status()).isEqualTo(EventStatus.PUBLISHED);
        verify(eventRepository).save(event);
    }

    // UAT-42: Missing Required Fields / Invalid Venue Map Configuration
    // Reject publishing an event without venue zones.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void publish_missingVenueZones_throwsAndDoesNotSave() {
        Event event = createDraftEvent();

        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));
        allowManageEvents();

        assertThatThrownBy(() -> service.publish(ACTOR_ID, event.id()))
                .isInstanceOf(EventDomainException.class);

        verify(eventRepository, never()).save(any());
    }
}