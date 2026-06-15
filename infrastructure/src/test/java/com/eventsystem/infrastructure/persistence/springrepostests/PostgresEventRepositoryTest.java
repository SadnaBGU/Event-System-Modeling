package com.eventsystem.infrastructure.persistence.springrepostests;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.event.*;
import com.eventsystem.domain.zone.ZoneId;
import com.eventsystem.infrastructure.persistence.springrepos.PostgresEventRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import jakarta.persistence.EntityManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@EntityScan(basePackages = "com.eventsystem.domain")
@Import(PostgresEventRepository.class)
class PostgresEventRepositoryTest extends BasePostgresTest {

    @Autowired
    private PostgresEventRepository eventRepository;

    @Autowired
    private EntityManager em;

    private Event testEvent;
    private CompanyId companyId;

    @BeforeEach
    void setUp() {
        companyId = new CompanyId("C-999");
        
        EventDetails details = new EventDetails(
                "Rock Festival",
                List.of(LocalDateTime.of(2026, 8, 15, 20, 0), LocalDateTime.of(2026, 8, 16, 20, 0)),
                "Music",
                "Park HaYarkon",
                "Biggest rock festival of the year"
        );

        VenueMap map = new VenueMap(List.of(
                new MapElement("polygon", "Stage", 0, 0, null),
                new MapElement("point", "VIP Gate", 5, 10, null)
        ));

        testEvent = new Event(new EventId("E-100"), companyId, details, map);
        testEvent.addZone(new ZoneId("Z-1"));
        testEvent.addZone(new ZoneId("Z-2"));
        testEvent.publish();
    }

    @Test
    void saveAndFindById_savesAllNestedCollectionsAndJson() {
        // Arrange
        eventRepository.save(testEvent);
        
        em.flush();
        em.clear();

        // Act
        Optional<Event> foundOpt = eventRepository.findById(new EventId("E-100"));

        // Assert
        assertThat(foundOpt).isPresent();
        Event found = foundOpt.get();

        assertThat(found.details().name()).isEqualTo("Rock Festival");
        assertThat(found.details().dates()).hasSize(2);
        
        assertThat(found.zoneIds()).containsExactlyInAnyOrder(new ZoneId("Z-1"), new ZoneId("Z-2"));
        
        assertThat(found.venueMap()).isNotNull();
    }

    @Test
    void findByCompany_returnsEventsForSpecificCompany() {
        eventRepository.save(testEvent);
        
        Event draftEvent = new Event(
                new EventId("E-101"),
                companyId,
                new EventDetails("Draft Show", List.of(LocalDateTime.now().plusDays(10)), "Comedy", "Club", "Some description"),
                new VenueMap(List.of())
        );
        eventRepository.save(draftEvent);

        Event otherCompanyEvent = new Event(
                new EventId("E-200"),
                new CompanyId("C-888"),
                new EventDetails("Other Show", List.of(LocalDateTime.now().plusDays(15)), "Art", "Gallery", "Another description"),
                new VenueMap(List.of())
        );
        eventRepository.save(otherCompanyEvent);

        em.flush();
        em.clear();

        // Act
        List<Event> companyEvents = eventRepository.findByCompany(companyId);

        // Assert
        assertThat(companyEvents).hasSize(2);
        assertThat(companyEvents)
                .extracting(e -> e.id().value())
                .containsExactlyInAnyOrder("E-100", "E-101");
    }

    @Test
    void findPublishedEvents_returnsOnlyPublished() {
        eventRepository.save(testEvent);
        
        Event draftEvent = new Event(
                new EventId("E-101"),
                companyId,
                new EventDetails("Draft Show", List.of(LocalDateTime.now().plusDays(10)), "Comedy", "Club", "Some description"),
                new VenueMap(List.of())
        );
        eventRepository.save(draftEvent);

        em.flush();
        em.clear();

        // Act
        List<Event> publishedEvents = eventRepository.findPublishedEvents();

        // Assert
        assertThat(publishedEvents).hasSize(1);
        assertThat(publishedEvents.get(0).id().value()).isEqualTo("E-100");
    }
}
