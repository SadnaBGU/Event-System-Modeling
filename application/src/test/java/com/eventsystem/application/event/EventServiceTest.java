package com.eventsystem.application.event;

import com.eventsystem.application.company.ICompanyPermissionServicePort;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.domainexceptions.EventDomainException;
import com.eventsystem.domain.event.Event;
import com.eventsystem.domain.event.EventDetails;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.event.EventStatus;
import com.eventsystem.domain.event.SalesMethod;
import com.eventsystem.domain.event.VenueMap;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.order.OrderItem;
import com.eventsystem.domain.shared.Money;
import com.eventsystem.domain.zone.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * UC15: Create and Configure Event
 *
 * Tests for UATs:
 * - UAT-41: Successful event creation with event details and venue map
 * - UAT-42: Missing required fields / invalid event configuration
 *
 * UC20: Perform Authorized Management Action
 *
 * Tests for UATs:
 * - UAT-61: Authorized manager/owner edits event configuration
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
 * - UAT-16: Successful Reservation support data: event-zone validation and zone expansion
 *
 * UC3: Virtual Queue and Load Management
 *
 * Tests for UATs:
 * - UAT-20: Threshold Exceeded Queue Created / event sales method can be configured as queue

 */
@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    private static final MemberId ACTOR_ID = new MemberId("actor-1");
    //private static final MemberId OTHER_ACTOR_ID = new MemberId("actor-2");

    private static final CompanyId COMPANY_ID = new CompanyId("company-1");
    private static final CompanyId OTHER_COMPANY_ID = new CompanyId("company-2");

    private static final ZoneId REGULAR_ZONE = new ZoneId("regular-zone");
    private static final ZoneId VIP_ZONE = new ZoneId("vip-zone");

    @Mock
    private IEventRepository eventRepository;

    @Mock
    private ICompanyPermissionServicePort permissionChecker;

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

    private EventDetails updatedDetails() {
        return new EventDetails(
                "Updated Event",
                List.of(LocalDateTime.now().plusDays(20)),
                "Updated Category",
                "Updated Location",
                "Updated description"
        );
    }

    private Event createDraftEvent() {
        return Event.createDraft(COMPANY_ID, defaultDetails(), VenueMap.empty());
    }

    private Event createPublishableDraftEvent() {
        Event event = createDraftEvent();
        event.addZone(REGULAR_ZONE);
        return event;
    }

    private Event createPublishedEvent() {
        Event event = createPublishableDraftEvent();
        event.publish();
        return event;
    }

    private void allowManageEvents() {
        when(permissionChecker.canManageEvents(ACTOR_ID, COMPANY_ID)).thenReturn(true);
    }

    private void denyManageEvents() {
        when(permissionChecker.canManageEvents(ACTOR_ID, COMPANY_ID)).thenReturn(false);
    }

    private void stubExisting(Event event) {
        when(eventRepository.findById(event.id())).thenReturn(Optional.of(event));
    }

    // ─────────────────────────────────────────────────────────────────────
    // UC15 / UAT-41, UAT-42: Create event draft
    // ─────────────────────────────────────────────────────────────────────

    // UAT-41: Successful event creation.
    @Test
    void createDraft_actorHasPermission_savesEventAndReturnsId_UAT41() {
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
        assertThat(savedEvent.salesMethod()).isEqualTo(SalesMethod.REGULAR);

        verify(permissionChecker).canManageEvents(ACTOR_ID, COMPANY_ID);
    }

    // UAT-41: Successful event creation with default empty venue map overload.
    @Test
    void createDraft_withoutVenueMap_usesEmptyVenueMapAndSaves_UAT41() {
        EventDetails details = defaultDetails();
        allowManageEvents();

        EventId eventId = service.createDraft(ACTOR_ID, COMPANY_ID, details);

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository).save(eventCaptor.capture());

        Event savedEvent = eventCaptor.getValue();
        assertThat(savedEvent.id()).isEqualTo(eventId);
        assertThat(savedEvent.venueMap().mapElements()).isEmpty();
    }

    // UAT-62: Unauthorized event creation is denied.
    @Test
    void createDraft_actorWithoutPermission_throwsAndDoesNotSave_UAT62() {
        denyManageEvents();

        assertThatThrownBy(() -> service.createDraft(ACTOR_ID, COMPANY_ID, defaultDetails(), VenueMap.empty()))
                .isInstanceOf(SecurityException.class);

        verify(eventRepository, never()).save(any());
        verify(permissionChecker).canManageEvents(ACTOR_ID, COMPANY_ID);
    }

    // UAT-42: Missing actor ID is rejected.
    @Test
    void createDraft_nullActorId_throws_UAT42() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.createDraft(null, COMPANY_ID, defaultDetails(), VenueMap.empty()));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    // UAT-42: Missing company ID is rejected.
    @Test
    void createDraft_nullCompanyId_throws_UAT42() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.createDraft(ACTOR_ID, null, defaultDetails(), VenueMap.empty()));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    // UAT-42: Missing details are rejected.
    @Test
    void createDraft_nullDetails_throws_UAT42() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.createDraft(ACTOR_ID, COMPANY_ID, null, VenueMap.empty()));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    // UAT-42: Missing venue map is rejected.
    @Test
    void createDraft_nullVenueMap_throws_UAT42() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.createDraft(ACTOR_ID, COMPANY_ID, defaultDetails(), null));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    // ─────────────────────────────────────────────────────────────────────
    // UC20 / UAT-61, UAT-62, UAT-63: Update event details
    // ─────────────────────────────────────────────────────────────────────

    // UAT-61: Authorized manager updates event details.
    @Test
    void updateDetails_actorHasPermission_updatesAndSaves_UAT61() {
        Event event = createDraftEvent();
        EventDetails newDetails = updatedDetails();

        stubExisting(event);
        allowManageEvents();

        service.updateDetails(ACTOR_ID, event.id(), newDetails);

        assertThat(event.details()).isEqualTo(newDetails);
        verify(eventRepository).save(event);
        verify(permissionChecker).canManageEvents(ACTOR_ID, COMPANY_ID);
    }

    // UAT-62: Unauthorized manager cannot update event details.
    @Test
    void updateDetails_actorWithoutPermission_throwsAndDoesNotSave_UAT62() {
        Event event = createDraftEvent();
        stubExisting(event);
        denyManageEvents();

        assertThatThrownBy(() -> service.updateDetails(ACTOR_ID, event.id(), updatedDetails()))
                .isInstanceOf(SecurityException.class);

        verify(eventRepository, never()).save(any());
        verify(permissionChecker).canManageEvents(ACTOR_ID, COMPANY_ID);
    }

    // UAT-63: Permission is checked at submission time, not assumed from earlier UI state.
    @Test
    void updateDetails_permissionRevokedBeforeSubmission_throwsAndDoesNotSave_UAT63() {
        Event event = createDraftEvent();
        stubExisting(event);
        when(permissionChecker.canManageEvents(ACTOR_ID, COMPANY_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.updateDetails(ACTOR_ID, event.id(), updatedDetails()))
                .isInstanceOf(SecurityException.class);

        verify(eventRepository, never()).save(any());
    }

    @Test
    void updateDetails_eventNotFound_throwsAndDoesNotSave() {
        EventId unknownId = EventId.random();
        when(eventRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.updateDetails(ACTOR_ID, unknownId, updatedDetails()));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    @Test
    void updateDetails_nullEventId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.updateDetails(ACTOR_ID, null, updatedDetails()));

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

    // ─────────────────────────────────────────────────────────────────────
    // UC20 / UAT-61, UAT-62: Update venue map
    // ─────────────────────────────────────────────────────────────────────

    // UAT-61: Authorized manager updates venue map.
    @Test
    void updateVenueMap_actorHasPermission_updatesAndSaves_UAT61() {
        Event event = createDraftEvent();
        VenueMap newVenueMap = VenueMap.empty();

        stubExisting(event);
        allowManageEvents();

        service.updateVenueMap(ACTOR_ID, event.id(), newVenueMap);

        assertThat(event.venueMap()).isEqualTo(newVenueMap);
        verify(eventRepository).save(event);
    }

    // UAT-62: Unauthorized manager cannot update venue map.
    @Test
    void updateVenueMap_actorWithoutPermission_throwsAndDoesNotSave_UAT62() {
        Event event = createDraftEvent();
        stubExisting(event);
        denyManageEvents();

        assertThatThrownBy(() -> service.updateVenueMap(ACTOR_ID, event.id(), VenueMap.empty()))
                .isInstanceOf(SecurityException.class);

        verify(eventRepository, never()).save(any());
    }

    @Test
    void updateVenueMap_eventNotFound_throwsAndDoesNotSave() {
        EventId unknownId = EventId.random();
        when(eventRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.updateVenueMap(ACTOR_ID, unknownId, VenueMap.empty()));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
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

    // ─────────────────────────────────────────────────────────────────────
    // UC15 / UAT-41, UAT-42: Zone linking
    // ─────────────────────────────────────────────────────────────────────

    // UAT-41: Authorized manager links a zone to the draft event.
    @Test
    void addZone_actorHasPermission_addsZoneAndSaves_UAT41() {
        Event event = createDraftEvent();
        stubExisting(event);
        allowManageEvents();

        service.addZone(ACTOR_ID, event.id(), REGULAR_ZONE);

        assertThat(event.zoneIds()).contains(REGULAR_ZONE);
        verify(eventRepository).save(event);
    }

    // UAT-62: Unauthorized manager cannot link a zone.
    @Test
    void addZone_actorWithoutPermission_throwsAndDoesNotSave_UAT62() {
        Event event = createDraftEvent();
        stubExisting(event);
        denyManageEvents();

        assertThatThrownBy(() -> service.addZone(ACTOR_ID, event.id(), REGULAR_ZONE))
                .isInstanceOf(SecurityException.class);

        verify(eventRepository, never()).save(any());
    }

    // UAT-42: Duplicate zone linkage is rejected by the domain and not saved.
    @Test
    void addZone_duplicateZone_throwsAndDoesNotSave_UAT42() {
        Event event = createDraftEvent();
        event.addZone(REGULAR_ZONE);
        stubExisting(event);
        allowManageEvents();

        assertThatThrownBy(() -> service.addZone(ACTOR_ID, event.id(), REGULAR_ZONE))
                .isInstanceOf(EventDomainException.class);

        verify(eventRepository, never()).save(any());
    }

    @Test
    void addZone_eventNotFound_throwsAndDoesNotSave() {
        EventId unknownId = EventId.random();
        when(eventRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.addZone(ACTOR_ID, unknownId, REGULAR_ZONE));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    @Test
    void addZone_nullEventId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.addZone(ACTOR_ID, null, REGULAR_ZONE));

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

    // UAT-61: Authorized manager removes a zone from the draft event.
    @Test
    void removeZone_actorHasPermission_removesZoneAndSaves_UAT61() {
        Event event = createDraftEvent();
        event.addZone(REGULAR_ZONE);
        stubExisting(event);
        allowManageEvents();

        service.removeZone(ACTOR_ID, event.id(), REGULAR_ZONE);

        assertThat(event.zoneIds()).doesNotContain(REGULAR_ZONE);
        verify(eventRepository).save(event);
    }

    // UAT-62: Unauthorized manager cannot remove a zone.
    @Test
    void removeZone_actorWithoutPermission_throwsAndDoesNotSave_UAT62() {
        Event event = createDraftEvent();
        event.addZone(REGULAR_ZONE);
        stubExisting(event);
        denyManageEvents();

        assertThatThrownBy(() -> service.removeZone(ACTOR_ID, event.id(), REGULAR_ZONE))
                .isInstanceOf(SecurityException.class);

        verify(eventRepository, never()).save(any());
    }

    // UAT-42: Removing a non-linked zone is rejected by the domain and not saved.
    @Test
    void removeZone_missingZone_throwsAndDoesNotSave_UAT42() {
        Event event = createDraftEvent();
        stubExisting(event);
        allowManageEvents();

        assertThatThrownBy(() -> service.removeZone(ACTOR_ID, event.id(), REGULAR_ZONE))
                .isInstanceOf(EventDomainException.class);

        verify(eventRepository, never()).save(any());
    }

    @Test
    void removeZone_nullEventId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.removeZone(ACTOR_ID, null, REGULAR_ZONE));

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

    // ─────────────────────────────────────────────────────────────────────
    // UC15 / UC20: Publish, cancel, event over
    // ─────────────────────────────────────────────────────────────────────

    // UAT-61: Authorized manager publishes a configured event.
    @Test
    void publish_actorHasPermission_publishesAndSaves_UAT61() {
        Event event = createPublishableDraftEvent();
        stubExisting(event);
        allowManageEvents();

        service.publish(ACTOR_ID, event.id());

        assertThat(event.status()).isEqualTo(EventStatus.PUBLISHED);
        verify(eventRepository).save(event);
    }

    // UAT-62: Unauthorized manager cannot publish.
    @Test
    void publish_actorWithoutPermission_throwsAndDoesNotSave_UAT62() {
        Event event = createPublishableDraftEvent();
        stubExisting(event);
        denyManageEvents();

        assertThatThrownBy(() -> service.publish(ACTOR_ID, event.id()))
                .isInstanceOf(SecurityException.class);

        verify(eventRepository, never()).save(any());
    }

    // UAT-42: Publishing without zones is invalid.
    @Test
    void publish_eventHasNoZones_throwsAndDoesNotSave_UAT42() {
        Event event = createDraftEvent();
        stubExisting(event);
        allowManageEvents();

        assertThatThrownBy(() -> service.publish(ACTOR_ID, event.id()))
                .isInstanceOf(EventDomainException.class);

        verify(eventRepository, never()).save(any());
    }

    // UAT-42: Publishing a cancelled event is invalid.
    @Test
    void publish_cancelledEvent_throwsAndDoesNotSave_UAT42() {
        Event event = createPublishableDraftEvent();
        event.cancel();
        stubExisting(event);
        allowManageEvents();

        assertThatThrownBy(() -> service.publish(ACTOR_ID, event.id()))
                .isInstanceOf(EventDomainException.class);

        verify(eventRepository, never()).save(any());
    }

    @Test
    void publish_eventNotFound_throwsAndDoesNotSave() {
        EventId unknownId = EventId.random();
        when(eventRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.publish(ACTOR_ID, unknownId));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    // UAT-61: Authorized manager cancels an event.
    @Test
    void cancel_actorHasPermission_cancelsAndSaves_UAT61() {
        Event event = createPublishedEvent();
        stubExisting(event);
        allowManageEvents();

        service.cancel(ACTOR_ID, event.id());

        assertThat(event.status()).isEqualTo(EventStatus.CANCELLED);
        verify(eventRepository).save(event);
    }

    // UAT-62: Unauthorized manager cannot cancel.
    @Test
    void cancel_actorWithoutPermission_throwsAndDoesNotSave_UAT62() {
        Event event = createPublishedEvent();
        stubExisting(event);
        denyManageEvents();

        assertThatThrownBy(() -> service.cancel(ACTOR_ID, event.id()))
                .isInstanceOf(SecurityException.class);

        verify(eventRepository, never()).save(any());
    }

    @Test
    void cancel_eventNotFound_throwsAndDoesNotSave() {
        EventId unknownId = EventId.random();
        when(eventRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.cancel(ACTOR_ID, unknownId));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    // UAT-61: Authorized manager marks a published event as over.
    @Test
    void eventOver_actorHasPermission_marksOverAndSaves_UAT61() {
        Event event = createPublishedEvent();
        stubExisting(event);
        allowManageEvents();

        service.eventOver(ACTOR_ID, event.id());

        assertThat(event.status()).isEqualTo(EventStatus.OVER);
        verify(eventRepository).save(event);
    }

    // UAT-62: Unauthorized manager cannot mark event as over.
    @Test
    void eventOver_actorWithoutPermission_throwsAndDoesNotSave_UAT62() {
        Event event = createPublishedEvent();
        stubExisting(event);
        denyManageEvents();

        assertThatThrownBy(() -> service.eventOver(ACTOR_ID, event.id()))
                .isInstanceOf(SecurityException.class);

        verify(eventRepository, never()).save(any());
    }

    // UAT-42: Draft event cannot be marked as over.
    @Test
    void eventOver_draftEvent_throwsAndDoesNotSave_UAT42() {
        Event event = createDraftEvent();
        stubExisting(event);
        allowManageEvents();

        assertThatThrownBy(() -> service.eventOver(ACTOR_ID, event.id()))
                .isInstanceOf(EventDomainException.class);

        verify(eventRepository, never()).save(any());
    }

    @Test
    void eventOver_eventNotFound_throwsAndDoesNotSave() {
        EventId unknownId = EventId.random();
        when(eventRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.eventOver(ACTOR_ID, unknownId));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    // ─────────────────────────────────────────────────────────────────────
    // UC6 / UAT-14, UAT-15: Query operations
    // ─────────────────────────────────────────────────────────────────────

    // UAT-14: Find event by id.
    @Test
    void findById_existingEvent_returnsEvent_UAT14() {
        Event event = createDraftEvent();
        stubExisting(event);

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

        verifyNoInteractions(eventRepository);
    }

    // UAT-14: Search company events returns repository result.
    @Test
    void findByCompany_delegatesToRepository_UAT14() {
        Event event1 = createDraftEvent();
        Event event2 = createDraftEvent();
        when(eventRepository.findByCompany(COMPANY_ID)).thenReturn(List.of(event1, event2));

        List<Event> result = service.findByCompany(COMPANY_ID);

        assertThat(result).containsExactly(event1, event2);
    }

    @Test
    void findByCompany_nullCompanyId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.findByCompany(null));

        verifyNoInteractions(eventRepository);
    }

    // UAT-14: Search published company events filters draft/cancelled events out.
    @Test
    void findPublishedByCompany_returnsOnlyPublishedEvents_UAT14() {
        Event draft = createDraftEvent();
        Event published = createPublishedEvent();
        Event cancelled = createPublishedEvent();
        cancelled.cancel();

        when(eventRepository.findByCompany(COMPANY_ID)).thenReturn(List.of(draft, published, cancelled));

        List<Event> result = service.findPublishedByCompany(COMPANY_ID);

        assertThat(result).containsExactly(published);
    }

    // UAT-15: Empty search results are returned as empty list.
    @Test
    void findPublishedByCompany_whenNoEvents_returnsEmptyList_UAT15() {
        when(eventRepository.findByCompany(COMPANY_ID)).thenReturn(List.of());

        List<Event> result = service.findPublishedByCompany(COMPANY_ID);

        assertThat(result).isEmpty();
    }

    // UAT-14: allPublishedEvents returns IDs of published events from repository.
    @Test
    void allPublishedEvents_returnsPublishedEventIds_UAT14() {
        Event published1 = createPublishedEvent();
        Event published2 = createPublishedEvent();
        when(eventRepository.findPublishedEvents()).thenReturn(List.of(published1, published2));

        List<EventId> result = service.allPublishedEvents();

        assertThat(result).containsExactly(published1.id(), published2.id());
    }

    // ─────────────────────────────────────────────────────────────────────
    // UC20 / UC3: Sales method configuration
    // ─────────────────────────────────────────────────────────────────────

    // UAT-61: Authorized manager can set sales method.
    @Test
    void setSalesMethod_actorHasPermission_updatesAndSaves_UAT61() {
        Event event = createDraftEvent();
        stubExisting(event);
        allowManageEvents();

        service.setSalesMethod(ACTOR_ID, event.id(), SalesMethod.LOTTERY);

        assertThat(event.salesMethod()).isEqualTo(SalesMethod.LOTTERY);
        verify(eventRepository).save(event);
    }

    // UAT-62: Unauthorized manager cannot set sales method.
    @Test
    void setSalesMethod_actorWithoutPermission_throwsAndDoesNotSave_UAT62() {
        Event event = createDraftEvent();
        stubExisting(event);
        denyManageEvents();

        assertThatThrownBy(() -> service.setSalesMethod(ACTOR_ID, event.id(), SalesMethod.LOTTERY))
                .isInstanceOf(SecurityException.class);

        verify(eventRepository, never()).save(any());
    }

    // UAT-20: Event can be configured for virtual queue flow.
    @Test
    void setMethodQueue_setsVirtualQueueAndSaves_UAT20() {
        Event event = createDraftEvent();
        stubExisting(event);
        allowManageEvents();

        service.setMethodQueue(ACTOR_ID, event.id());

        assertThat(event.salesMethod()).isEqualTo(SalesMethod.VIRTUAL_QUEUE);
        verify(eventRepository).save(event);
    }

    @Test
    void setMethodRegular_setsRegularAndSaves() {
        Event event = createDraftEvent();
        event.setSalesMethod(SalesMethod.LOTTERY);
        stubExisting(event);
        allowManageEvents();

        service.setMethodRegular(ACTOR_ID, event.id());

        assertThat(event.salesMethod()).isEqualTo(SalesMethod.REGULAR);
        verify(eventRepository).save(event);
    }

    @Test
    void setMethodLottery_setsLotteryAndSaves() {
        Event event = createDraftEvent();
        stubExisting(event);
        allowManageEvents();

        service.setMethodLottery(ACTOR_ID, event.id());

        assertThat(event.salesMethod()).isEqualTo(SalesMethod.LOTTERY);
        verify(eventRepository).save(event);
    }

    @Test
    void setSalesMethod_nullSalesMethod_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.setSalesMethod(ACTOR_ID, EventId.random(), null));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(permissionChecker);
    }

    // ─────────────────────────────────────────────────────────────────────
    // IEventManagementPort behavior used by policy/order services
    // ─────────────────────────────────────────────────────────────────────

    @Test
    void isEventByCompany_whenEventBelongsToCompany_returnsTrue() {
        Event event = createDraftEvent();
        stubExisting(event);

        assertThat(service.isEventByCompany(event.id(), COMPANY_ID)).isTrue();
    }

    @Test
    void isEventByCompany_whenEventBelongsToOtherCompany_returnsFalse() {
        Event event = createDraftEvent();
        stubExisting(event);

        assertThat(service.isEventByCompany(event.id(), OTHER_COMPANY_ID)).isFalse();
    }

    @Test
    void companyOfEvent_returnsOwningCompany() {
        Event event = createDraftEvent();
        stubExisting(event);

        CompanyId result = service.companyOfEvent(event.id());

        assertThat(result).isEqualTo(COMPANY_ID);
    }

    @Test
    void allEventsOfCompany_returnsEventIdsForCompany() {
        Event event1 = createDraftEvent();
        Event event2 = createDraftEvent();
        when(eventRepository.findByCompany(COMPANY_ID)).thenReturn(List.of(event1, event2));

        List<EventId> result = service.allEventsOfCompany(COMPANY_ID);

        assertThat(result).containsExactly(event1.id(), event2.id());
    }

    // UAT-16: Reservation/checkout support expands order item quantities into per-ticket zones.
    @Test
    void getZonesOfTicketsForEvent_whenAllZonesBelongToEvent_returnsZonePerTicket_UAT16() {
        Event event = createDraftEvent();
        event.addZone(REGULAR_ZONE);
        event.addZone(VIP_ZONE);
        stubExisting(event);

        List<OrderItem> items = List.of(
                new OrderItem(REGULAR_ZONE.value(), null, 2, Money.of(BigDecimal.valueOf(100), "ILS")),
                new OrderItem(VIP_ZONE.value(), null, 1, Money.of(BigDecimal.valueOf(200), "ILS"))
        );

        List<ZoneId> result = service.getZonesOfTicketsForEvent(event.id(), items);

        assertThat(result).containsExactly(REGULAR_ZONE, REGULAR_ZONE, VIP_ZONE);
    }

    // UAT-16: Reservation/checkout support rejects a requested zone that is not part of the event.
    @Test
    void getZonesOfTicketsForEvent_whenItemZoneDoesNotBelongToEvent_throws_UAT16() {
        Event event = createDraftEvent();
        event.addZone(REGULAR_ZONE);
        stubExisting(event);

        List<OrderItem> items = List.of(
                new OrderItem(VIP_ZONE.value(), null, 1, Money.of(BigDecimal.valueOf(100), "ILS"))
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.getZonesOfTicketsForEvent(event.id(), items));
    }

    @Test
    void getZonesOfTicketsForEvent_nullItems_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.getZonesOfTicketsForEvent(EventId.random(), null));

        verifyNoInteractions(eventRepository);
    }

    @Test
    void requireZoneBelongsToEvent_whenZoneBelongs_doesNotThrow() {
        Event event = createDraftEvent();
        event.addZone(REGULAR_ZONE);
        stubExisting(event);

        service.requireZoneBelongsToEvent(event.id(), REGULAR_ZONE);
    }

    @Test
    void requireZoneBelongsToEvent_whenZoneDoesNotBelong_throws() {
        Event event = createDraftEvent();
        stubExisting(event);

        assertThatThrownBy(() -> service.requireZoneBelongsToEvent(event.id(), REGULAR_ZONE))
                .isInstanceOf(EventDomainException.class);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Constructor
    // ─────────────────────────────────────────────────────────────────────

    @Test
    void constructorRejectsNullDependencies() {
        assertThatNullPointerException()
                .isThrownBy(() -> new EventService(null, permissionChecker));

        assertThatNullPointerException()
                .isThrownBy(() -> new EventService(eventRepository, null));
    }
}
