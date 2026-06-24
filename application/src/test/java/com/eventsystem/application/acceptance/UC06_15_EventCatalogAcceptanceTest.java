package com.eventsystem.application.acceptance;

import com.eventsystem.application.event.EventCatalogService;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.event.Event;
import com.eventsystem.domain.event.EventDetails;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.event.EventStatus;
import com.eventsystem.domain.event.VenueMap;
import com.eventsystem.domain.member.MemberId;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Acceptance tests for:
 *   UC 6  - Search and View Event Information
 *   UC 15 - Create and Configure Event
 *
 * Uses the real {@link com.eventsystem.application.event.EventService} and
 * {@link EventCatalogService} with the fixture's fake repositories and the real
 * {@link com.eventsystem.application.company.ProductionCompanyService} as the
 * permission checker.
 */
class UC06_15_EventCatalogAcceptanceTest {

    private static EventDetails validDetails(String name) {
        return new EventDetails(
                name,
                List.of(LocalDateTime.now().plusDays(30)),
                "Concert",
                "Big Arena",
                "An evening to remember");
    }

    /** Creates a published event (draft -> add zone -> publish) owned by the given company. */
    private EventId createPublishedEvent(
            ApplicationAcceptanceFixture app,
            MemberId actor,
            CompanyId companyId,
            String eventName) {
        EventId eventId = app.eventService.createDraft(actor, companyId, validDetails(eventName), VenueMap.empty());
        app.eventService.addZone(actor, eventId, app.zoneId("zone-" + eventId.value()));
        app.eventService.publish(actor, eventId);
        return eventId;
    }

    // REQ: EVT-01
    // UC: UC 15 - Create and Configure Event
    // UAT: UAT-41 - Successful Event Creation
    @Test
    void ownerCreatesAndPublishesEvent_eventIsPersistedAndPublished() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();
        MemberId founder = app.memberId("founder-1");
        CompanyId companyId = app.createCompanyWithFounder(founder.value());

        EventId eventId = createPublishedEvent(app, founder, companyId, "Spring Festival");

        Event created = app.eventService.getEvent(eventId);
        assertThat(created.status()).isEqualTo(EventStatus.PUBLISHED);
        assertThat(created.companyId()).isEqualTo(companyId);
        assertThat(created.details().name()).isEqualTo("Spring Festival");
        assertThat(app.eventService.findByCompany(companyId)).hasSize(1);
    }

    // REQ: EVT-01
    // UC: UC 15 - Create and Configure Event
    // UAT: UAT-42 - Missing Required Fields
    @Test
    void createEventWithMissingMandatoryFields_isRejectedAndNothingPersisted() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();
        MemberId founder = app.memberId("founder-1");
        CompanyId companyId = app.createCompanyWithFounder(founder.value());

        // Missing venue map.
        assertThatThrownBy(() ->
                app.eventService.createDraft(founder, companyId, validDetails("No Map Event"), null))
                .isInstanceOf(NullPointerException.class);

        // Missing date is rejected at event-details construction.
        assertThatThrownBy(() ->
                new EventDetails("No Date Event", List.of(), "Concert", "Arena", "desc"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(app.eventService.findByCompany(companyId)).isEmpty();
    }

    // REQ: EVT-01, PRD-12
    // UC: UC 15 - Create and Configure Event
    // UAT: UAT-43 - Inactive Company
    @Test
    void createEventUnderSuspendedCompany_isBlocked() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();
        MemberId founder = app.memberId("founder-1");
        CompanyId companyId = app.createCompanyWithFounder(founder.value());

        app.companyService.suspendCompany(companyId);

        assertThatThrownBy(() ->
                app.eventService.createDraft(founder, companyId, validDetails("Blocked Event"), VenueMap.empty()))
                .isInstanceOf(SecurityException.class);

        assertThat(app.eventService.findByCompany(companyId)).isEmpty();
    }

    // REQ: SRCH-01
    // UC: UC 6 - Search and View Event Information
    // UAT: UAT-14 - Search With Results
    @Test
    void searchByArtist_returnsMatchingPublishedEvent() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();
        MemberId founder = app.memberId("founder-1");
        app.saveMember(founder.value());
        CompanyId companyId = app.companyService.createCompany(founder, "Radiohead Live", "Touring company", 5.0);

        EventId eventId = createPublishedEvent(app, founder, companyId, "OK Computer Tour");

        List<Event> results = app.eventCatalogService.search("radiohead", null, null);

        assertThat(results).extracting(Event::id).contains(eventId);
    }

    // REQ: SRCH-01
    // UC: UC 6 - Search and View Event Information
    // UAT: UAT-15 - Search Empty Results
    @Test
    void searchWithNoMatchingCriteria_returnsEmptyResult() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();
        MemberId founder = app.memberId("founder-1");
        app.saveMember(founder.value());
        CompanyId companyId = app.companyService.createCompany(founder, "Radiohead Live", "Touring company", 5.0);
        createPublishedEvent(app, founder, companyId, "OK Computer Tour");

        List<Event> results = app.eventCatalogService.search("nonexistent-artist", null, null);

        assertThat(results).isEmpty();
    }
}
