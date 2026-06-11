package com.eventsystem.infrastructure.persistence.inmemoryrepostests;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.venue.Venue;
import com.eventsystem.domain.venue.VenueId;
import com.eventsystem.infrastructure.persistence.inmemoryrepos.InMemoryVenueRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryVenueRepositoryTest {

    private InMemoryVenueRepository repository;
    private CompanyId companyId;

    @BeforeEach
    void setUp() {
        repository = new InMemoryVenueRepository();
        companyId = CompanyId.random();
    }

    @Test
    void findById_unknownId_returnsEmpty() {
        assertThat(repository.findById(VenueId.generate())).isEmpty();
    }

    @Test
    void save_nullVenue_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> repository.save(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Venue cannot be null");
    }

    @Test
    void save_thenFindById_returnsVenue() {
        Venue venue = new Venue(VenueId.generate(), companyId, "Hall A");
        repository.save(venue);

        assertThat(repository.findById(venue.getVenueId())).contains(venue);
    }

    @Test
    void findByCompanyId_returnsOnlyCompanyVenues() {
        Venue v1 = new Venue(VenueId.generate(), companyId, "Hall A");
        Venue v2 = new Venue(VenueId.generate(), companyId, "Hall B");
        Venue other = new Venue(VenueId.generate(), CompanyId.random(), "Other");

        repository.save(v1);
        repository.save(v2);
        repository.save(other);

        List<Venue> found = repository.findByCompanyId(companyId);
        assertThat(found).containsExactlyInAnyOrder(v1, v2);
    }

    @Test
    void save_sameVenueTwice_doesNotDuplicateInCompanyIndex() {
        Venue venue = new Venue(VenueId.generate(), companyId, "Main");

        repository.save(venue);
        repository.save(venue);

        assertThat(repository.findByCompanyId(companyId)).hasSize(1).containsExactly(venue);
    }

    @Test
    void delete_existingVenue_removesFromStorageAndCompanyIndex() {
        Venue venue = new Venue(VenueId.generate(), companyId, "Arena");
        repository.save(venue);

        repository.delete(venue.getVenueId());

        assertThat(repository.findById(venue.getVenueId())).isEmpty();
        assertThat(repository.findByCompanyId(companyId)).isEmpty();
    }

    @Test
    void delete_unknownVenue_isNoOp() {
        assertThatCode(() -> repository.delete(VenueId.generate())).doesNotThrowAnyException();
    }
}
