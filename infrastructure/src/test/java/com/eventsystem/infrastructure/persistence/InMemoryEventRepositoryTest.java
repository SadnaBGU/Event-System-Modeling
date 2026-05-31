package com.eventsystem.infrastructure.persistence;

import com.eventsystem.domain.event.Event;
import com.eventsystem.domain.event.EventDetails;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.zone.ZoneId;

import com.eventsystem.domain.event.VenueMap;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryEventRepositoryTest {

    private CompanyId compId = new CompanyId("company-1");

    private EventDetails defaultDetails() {
        return new EventDetails(
                "Test Event",
                List.of(LocalDateTime.now().plusDays(10)),
                "Test Category",
                "Here",
                "A test event"
        );
    }

    private Event createEvent(String eventId, String companyId) {
        return new Event(
                new EventId(eventId),
                companyId,
                defaultDetails(),
                VenueMap.empty()
        );
    }

    @Test
    void saveAndFindByIdReturnsSavedEvent() {
        InMemoryEventRepository repository = new InMemoryEventRepository();
        Event event = createEvent("event-1", "company-1");

        repository.save(event);

        Optional<Event> found = repository.findById(event.id());

        assertThat(found).isPresent();
        assertThat(found.get()).isSameAs(event);
    }

    @Test
    void findByIdReturnsEmptyWhenEventDoesNotExist() {
        InMemoryEventRepository repository = new InMemoryEventRepository();

        Optional<Event> found = repository.findById(new EventId("missing-event"));

        assertThat(found).isEmpty();
    }

    @Test
    void findByCompanyReturnsOnlyEventsOfThatCompany() {
        InMemoryEventRepository repository = new InMemoryEventRepository();

        Event companyOneEvent1 = createEvent("event-1", "company-1");
        Event companyOneEvent2 = createEvent("event-2", "company-1");
        Event companyTwoEvent = createEvent("event-3", "company-2");

        repository.save(companyOneEvent1);
        repository.save(companyOneEvent2);
        repository.save(companyTwoEvent);

        List<Event> found = repository.findByCompany(compId);

        assertThat(found)
                .containsExactlyInAnyOrder(companyOneEvent1, companyOneEvent2)
                .doesNotContain(companyTwoEvent);
    }

    @Test
    void findByCompanyReturnsEmptyListWhenCompanyHasNoEvents() {
        InMemoryEventRepository repository = new InMemoryEventRepository();

        repository.save(createEvent("event-1", "company-1"));

        List<Event> found = repository.findByCompany(new CompanyId("company-2"));

        assertThat(found).isEmpty();
    }

    @Test
    void savingEventWithSameIdOverridesPreviousEvent() {
        InMemoryEventRepository repository = new InMemoryEventRepository();

        Event original = createEvent("event-1", "company-1");
        Event replacement = createEvent("event-1", "company-2");

        repository.save(original);
        repository.save(replacement);

        Optional<Event> found = repository.findById(new EventId("event-1"));

        assertThat(found).isPresent();
        assertThat(found.get()).isSameAs(replacement);
        assertThat(repository.findByCompany(compId)).isEmpty();
        assertThat(repository.findByCompany(new CompanyId("company-2"))).containsExactly(replacement);
    }

    @Test
    void saveRequiresNonNullEvent() {
        InMemoryEventRepository repository = new InMemoryEventRepository();

        assertThatThrownBy(() -> repository.save(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void findByIdRequiresNonNullId() {
        InMemoryEventRepository repository = new InMemoryEventRepository();

        assertThatThrownBy(() -> repository.findById(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void findByCompanyRequiresNonNullCompanyId() {
        InMemoryEventRepository repository = new InMemoryEventRepository();

        assertThatThrownBy(() -> repository.findByCompany(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void findPublishedEvents_returnsOnlyPublishedEvents() {
        InMemoryEventRepository repository = new InMemoryEventRepository();

        Event publishedEvent1 = createPublishableEvent("published-1", "company-1");
        Event publishedEvent2 = createPublishableEvent("published-2", "company-2");
        Event draftEvent = createEvent("draft-1", "company-1");
        Event cancelledEvent = createPublishableEvent("cancelled-1", "company-1");
        Event soldOutEvent = createPublishableEvent("sold-out-1", "company-1");

        publishedEvent1.publish();
        publishedEvent2.publish();

        cancelledEvent.publish();
        cancelledEvent.cancel();

        soldOutEvent.publish();
        soldOutEvent.markSoldOut();

        repository.save(publishedEvent1);
        repository.save(publishedEvent2);
        repository.save(draftEvent);
        repository.save(cancelledEvent);
        repository.save(soldOutEvent);

        List<Event> found = repository.findPublishedEvents();

        assertThat(found)
                .containsExactlyInAnyOrder(publishedEvent1, publishedEvent2)
                .doesNotContain(draftEvent, cancelledEvent, soldOutEvent);
    }

    @Test
    void findPublishedEvents_returnsEmptyListWhenRepositoryIsEmpty() {
        InMemoryEventRepository repository = new InMemoryEventRepository();

        List<Event> found = repository.findPublishedEvents();

        assertThat(found).isEmpty();
    }

    @Test
    void findPublishedEvents_returnsEmptyListWhenNoEventIsPublished() {
        InMemoryEventRepository repository = new InMemoryEventRepository();

        Event draftEvent = createEvent("draft-1", "company-1");
        Event cancelledEvent = createPublishableEvent("cancelled-1", "company-1");
        cancelledEvent.publish();
        cancelledEvent.cancel();

        repository.save(draftEvent);
        repository.save(cancelledEvent);

        List<Event> found = repository.findPublishedEvents();

        assertThat(found).isEmpty();
    }

    private Event createPublishableEvent(String eventId, String companyId) {
        Event event = createEvent(eventId, companyId);
        event.addZone(new ZoneId(eventId + "-zone"));
        return event;
    }
}