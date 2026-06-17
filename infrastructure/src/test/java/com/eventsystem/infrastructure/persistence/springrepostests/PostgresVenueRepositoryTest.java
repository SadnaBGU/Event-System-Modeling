package com.eventsystem.infrastructure.persistence.springrepostests;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.venue.Venue;
import com.eventsystem.domain.venue.VenueId;
import com.eventsystem.infrastructure.persistence.springrepos.PostgresVenueRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EntityScan(basePackages = "com.eventsystem.domain")
@Import(PostgresVenueRepository.class)
class PostgresVenueRepositoryTest extends BasePostgresTest {

    @Autowired
    private PostgresVenueRepository repository;
    @Autowired
    private EntityManager em;

    @Test
    void crudOperations_workCorrectly() {
        CompanyId companyId = new CompanyId("COMP-1");
        Venue venue = new Venue(VenueId.generate(), companyId, "Main Arena");
        
        // Save
        repository.save(venue);
        em.flush();
        em.clear();

        // Find By Id
        assertThat(repository.findById(venue.getId())).isPresent();
        
        // Find All
        assertThat(repository.findAll()).hasSize(1);
        
        // Find By Company
        assertThat(repository.findByCompanyId(companyId)).hasSize(1);
        assertThat(repository.findByCompanyId(new CompanyId("OTHER"))).isEmpty();

        // Delete
        repository.delete(venue.getId());
        em.flush();
        em.clear();
        assertThat(repository.findById(venue.getId())).isEmpty();
    }

    @Test
    void nullValidations_throwExceptions() {
        assertThatThrownBy(() -> repository.save(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.findById(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.delete(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.findByCompanyId(null)).isInstanceOf(NullPointerException.class);
    }
}