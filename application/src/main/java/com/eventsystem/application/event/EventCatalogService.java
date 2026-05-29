package com.eventsystem.application.event;

import com.eventsystem.application.company.IProductionCompanyRepository;
import com.eventsystem.domain.company.ProductionCompany;
import com.eventsystem.domain.event.Event;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.zone.Zone;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Service
public class EventCatalogService {

    private final IEventRepository eventRepository;
    private final IZoneRepository zoneRepository;
    private final IProductionCompanyRepository productionCompanyRepository;

    public EventCatalogService(IEventRepository eventRepository,
                               IZoneRepository zoneRepository,
                               IProductionCompanyRepository productionCompanyRepository) {
        this.eventRepository = Objects.requireNonNull(eventRepository, "eventRepository must not be null");
        this.zoneRepository = Objects.requireNonNull(zoneRepository, "zoneRepository must not be null");
        this.productionCompanyRepository = Objects.requireNonNull(productionCompanyRepository, "productionCompanyRepository must not be null");
    }

    public List<Event> search(String artist, LocalDate date, PriceRange priceRange) {
        return eventRepository.findAll().stream()
                .filter(Event::isPublished)
                .filter(event -> matchesArtist(event, artist))
                .filter(event -> matchesDate(event, date))
                .filter(event -> matchesPriceRange(event, priceRange))
                .toList();
    }

    public Event findById(EventId eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("event not found: " + eventId.value()));
    }

    public String resolveArtistName(Event event) {
        return productionCompanyRepository.findById(event.companyId())
                .map(company -> company.companyDetails().name())
                .orElse(event.companyId().value());
    }

    public List<Zone> getZones(EventId eventId) {
        return zoneRepository.findByEventId(eventId);
    }

    private boolean matchesArtist(Event event, String artist) {
        if (artist == null || artist.isBlank()) {
            return true;
        }
        String artistLower = artist.trim().toLowerCase();
        String companyName = resolveArtistName(event).toLowerCase();
        return companyName.contains(artistLower);
    }

    private boolean matchesDate(Event event, LocalDate date) {
        if (date == null) {
            return true;
        }
        return event.details().dates().stream()
                .anyMatch(eventDate -> eventDate.toLocalDate().equals(date));
    }

    private boolean matchesPriceRange(Event event, PriceRange priceRange) {
        if (priceRange == null) {
            return true;
        }
        List<Zone> zones = zoneRepository.findByEventId(event.id());
        if (zones.isEmpty()) {
            return false;
        }
        return zones.stream().anyMatch(zone -> {
            BigDecimal amount = zone.pricePerTicket().amount();
            return amount.compareTo(priceRange.min()) >= 0 && amount.compareTo(priceRange.max()) <= 0;
        });
    }

    public record PriceRange(BigDecimal min, BigDecimal max) {
        public PriceRange {
            Objects.requireNonNull(min, "min must not be null");
            Objects.requireNonNull(max, "max must not be null");
            if (min.compareTo(max) > 0) {
                throw new IllegalArgumentException("min must be less than or equal to max");
            }
        }
    }
}
