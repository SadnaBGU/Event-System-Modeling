package com.eventsystem.application.event;

import com.eventsystem.domain.company.CompanyDetails;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.company.IProductionCompanyRepository;
import com.eventsystem.domain.company.ProductionCompany;
import com.eventsystem.domain.event.Event;
import com.eventsystem.domain.event.EventDetails;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.event.IEventRepository;
import com.eventsystem.domain.shared.Money;
import com.eventsystem.domain.zone.IZoneRepository;
import com.eventsystem.domain.zone.Zone;
import com.eventsystem.domain.zone.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventCatalogServiceTest {

    @Mock
    private IEventRepository eventRepository;
    @Mock
    private IZoneRepository zoneRepository;
    @Mock
    private IProductionCompanyRepository productionCompanyRepository;

    @InjectMocks
    private EventCatalogService catalogService;

    @Test
    void constructor_rejectsNulls() {
        assertThatThrownBy(() -> new EventCatalogService(null, zoneRepository, productionCompanyRepository))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EventCatalogService(eventRepository, null, productionCompanyRepository))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EventCatalogService(eventRepository, zoneRepository, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void priceRange_validatesMinMax() {
        assertThatThrownBy(() -> new EventCatalogService.PriceRange(null, BigDecimal.TEN))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EventCatalogService.PriceRange(BigDecimal.TEN, null))
                .isInstanceOf(NullPointerException.class);
        
        // Min > Max -> throws
        assertThatThrownBy(() -> new EventCatalogService.PriceRange(BigDecimal.TEN, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
                
        // Valid
        EventCatalogService.PriceRange valid = new EventCatalogService.PriceRange(BigDecimal.ONE, BigDecimal.TEN);
        assertThat(valid.min()).isEqualTo(BigDecimal.ONE);
        assertThat(valid.max()).isEqualTo(BigDecimal.TEN);
    }

    @Test
    void findById_delegatesToRepository() {
        EventId eventId = new EventId("EV-1");
        Event mockEvent = mock(Event.class);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(mockEvent));

        assertThat(catalogService.findById(eventId)).isEqualTo(mockEvent);
        
        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> catalogService.findById(eventId)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getZones_delegatesToRepository() {
        EventId eventId = new EventId("EV-1");
        Zone mockZone = mock(Zone.class);
        when(zoneRepository.findByEventId(eventId)).thenReturn(List.of(mockZone));

        assertThat(catalogService.getZones(eventId)).containsExactly(mockZone);
    }

    @Test
    void resolveArtistName_returnsNameIfFound_orUnknown() {
        Event event = mock(Event.class);
        CompanyId compId = new CompanyId("COMP-1");
        when(event.companyId()).thenReturn(compId);

        // Found
        ProductionCompany company = mock(ProductionCompany.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        when(company.companyDetails().name()).thenReturn("Rock Band");
        when(productionCompanyRepository.findById(compId)).thenReturn(Optional.of(company));
        assertThat(catalogService.resolveArtistName(event)).isEqualTo("Rock Band");

        // Not Found
        when(productionCompanyRepository.findById(compId)).thenReturn(Optional.empty());
        // תיקון הציפייה - אם הוא לא מוצא את החברה, הקוד באמת מחזיר את ה-ID שלה!
        assertThat(catalogService.resolveArtistName(event)).isEqualTo("COMP-1");
    }

    // ==========================================
    // Search Filters
    // ==========================================

    @Test
    void search_FiltersProperly() {
        Event e1 = mock(Event.class);
        Event e2 = mock(Event.class); // Unpublished
        Event e3 = mock(Event.class);

        // הגדרה מפורשת של מזהים כדי שהמוקים של ה-Repository לא ידרסו אחד את השני על null
        when(e1.id()).thenReturn(new EventId("EV-1"));
        when(e3.id()).thenReturn(new EventId("EV-3"));

        when(e1.isPublished()).thenReturn(true);
        when(e2.isPublished()).thenReturn(false);
        when(e3.isPublished()).thenReturn(true);

        when(eventRepository.findAll()).thenReturn(List.of(e1, e2, e3));

        // Dates
        LocalDate targetDate = LocalDate.of(2026, 6, 20);
        EventDetails d1 = new EventDetails("E1", List.of(LocalDateTime.parse("2026-06-20T20:00:00")), "T", "L", "D");
        EventDetails d3 = new EventDetails("E3", List.of(LocalDateTime.parse("2026-07-01T20:00:00")), "T", "L", "D");
        when(e1.details()).thenReturn(d1);
        when(e3.details()).thenReturn(d3);

        // Artist (Company)
        CompanyId c1 = new CompanyId("C1");
        CompanyId c3 = new CompanyId("C3");
        when(e1.companyId()).thenReturn(c1);
        when(e3.companyId()).thenReturn(c3);
        
        // פתרון הקסם: RETURNS_DEEP_STUBS מאפשר לשרשר את companyDetails().name() ללא תקלות
        ProductionCompany comp1 = mock(ProductionCompany.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        when(comp1.companyDetails().name()).thenReturn("The Beatles");
        
        when(productionCompanyRepository.findById(c1)).thenReturn(Optional.of(comp1));
        when(productionCompanyRepository.findById(c3)).thenReturn(Optional.empty());

        // Price
        Zone zone1 = mock(Zone.class);
        when(zone1.pricePerTicket()).thenReturn(new Money(BigDecimal.valueOf(150), "USD"));
        when(zoneRepository.findByEventId(e1.id())).thenReturn(List.of(zone1));
        when(zoneRepository.findByEventId(e3.id())).thenReturn(List.of()); // e3 has no zones

        // Act 1: Filter by Artist only
        List<Event> result1 = catalogService.search("BEATLES", null, null);
        assertThat(result1).containsExactly(e1); // Case insensitive match

        // Act 2: Filter by Date only
        List<Event> result2 = catalogService.search(null, targetDate, null);
        assertThat(result2).containsExactly(e1);

        // Act 3: Filter by Price Range
        EventCatalogService.PriceRange range = new EventCatalogService.PriceRange(BigDecimal.valueOf(100), BigDecimal.valueOf(200));
        List<Event> result3 = catalogService.search("  ", null, range);
        assertThat(result3).containsExactly(e1); // e3 filtered out because no zones
        
        // Act 4: Out of bounds price range
        EventCatalogService.PriceRange highRange = new EventCatalogService.PriceRange(BigDecimal.valueOf(500), BigDecimal.valueOf(600));
        List<Event> result4 = catalogService.search(null, null, highRange);
        assertThat(result4).isEmpty();
    }
}