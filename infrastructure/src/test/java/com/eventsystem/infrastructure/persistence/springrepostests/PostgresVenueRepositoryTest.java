package com.eventsystem.infrastructure.persistence.springrepostests;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.shared.Money;
import com.eventsystem.domain.venue.Venue;
import com.eventsystem.domain.venue.VenueId;
import com.eventsystem.domain.venue.VenueZone;
import com.eventsystem.domain.zone.ZoneId;
import com.eventsystem.domain.zone.ZoneType;
import com.eventsystem.infrastructure.persistence.springrepos.PostgresVenueRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EntityScan(basePackages = "com.eventsystem.domain")
@Import(PostgresVenueRepository.class)
class PostgresVenueRepositoryTest extends BasePostgresTest {

    @Autowired
    private PostgresVenueRepository repository;

    @Autowired
    private EntityManager em;

    private Venue venue;
    private VenueId venueId;
    private CompanyId companyId;

    @BeforeEach
    void setUp() {
        venueId = VenueId.generate();
        companyId = new CompanyId("COMP-123");
        venue = new Venue(venueId, companyId, "Barby Tel Aviv");
    }

    @Test
    void saveAndFindById_savesVenueAndAllNestedZones() {
        // Arrange
        ZoneId standingZoneId = ZoneId.random();
        VenueZone standingZone = new VenueZone(
                standingZoneId, 
                "Golden Ring", 
                ZoneType.STANDING, 
                new Money(new BigDecimal("350.00"), "ILS"), 
                500
        );
        
        // Arrange
        ZoneId seatedZoneId = ZoneId.random();
        VenueZone seatedZone = new VenueZone(
                seatedZoneId, 
                "Balcony", 
                ZoneType.SEATED, 
                new Money(new BigDecimal("500.00"), "ILS"), 
                100
        );

        venue.addZone(standingZone);
        venue.addZone(seatedZone);

        // Act
        repository.save(venue);
        em.flush();
        em.clear();

        Optional<Venue> foundOpt = repository.findById(venueId);

        // Assert
        assertThat(foundOpt).isPresent();
        Venue found = foundOpt.get();

        assertThat(found.getVenueName()).isEqualTo("Barby Tel Aviv");
        assertThat(found.getCompanyId()).isEqualTo(companyId);
        
        assertThat(found.getZones()).hasSize(2);
        
        assertThat(found.getTotalCapacity()).isEqualTo(600);
    }

    @Test
    void delete_removesVenueSuccessfully() {
        // Arrange
        repository.save(venue);
        em.flush();
        em.clear();

        // Act
        repository.delete(venueId);
        em.flush();
        em.clear();

        // Assert
        Optional<Venue> foundOpt = repository.findById(venueId);
        assertThat(foundOpt).isEmpty();
    }
}