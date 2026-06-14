package com.eventsystem.infrastructure.api.event;

import com.eventsystem.application.event.EventCatalogService;
import com.eventsystem.domain.event.Event;
import com.eventsystem.domain.event.EventDetails;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.event.MapElement;
import com.eventsystem.domain.event.VenueMap;
import com.eventsystem.application.security.ITokenService;
import com.eventsystem.domain.shared.Money;
import com.eventsystem.domain.zone.Zone;
import com.eventsystem.domain.zone.ZoneId;
import com.eventsystem.infrastructure.api.exceptions.GlobalExceptionHandler;
import com.eventsystem.infrastructure.security.AuthenticationInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = EventCatalogController.class, properties = "spring.main.web-application-type=servlet")
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class EventCatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    @org.springframework.boot.test.mock.mockito.MockBean
    private EventCatalogService catalogService;

    @org.springframework.boot.test.mock.mockito.MockBean
    private ITokenService tokenService;

    @org.springframework.boot.test.mock.mockito.MockBean
    private AuthenticationInterceptor authenticationInterceptor;

    @SuppressWarnings("null")
    @BeforeEach
    void allowMvcRequests() throws Exception {
        when(authenticationInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    private Event sampleEvent() {
        EventDetails details = new EventDetails(
                "Desert Rock Festival",
                List.of(LocalDateTime.parse("2026-06-20T18:00:00")),
                "Music",
                "Beer Sheva",
                "Outdoor live music event");
                
        VenueMap map = new VenueMap(List.of(
            new MapElement("STAGE", "Main Stage", 50, 50, null),
            new MapElement("AREA", "VIP Area", 100, 100, new ZoneId("VIP-ZONE"))
        ));
        
        Event event = new Event(new EventId("EVT-1"), "COMP-1", details, map);
        event.addZone(new ZoneId("VIP-ZONE"));
        event.publish();
        return event;
    }

    private Zone sampleZone() {
        return Zone.createStanding(
                new ZoneId("VIP-ZONE"),
                new EventId("EVT-1"),
                "VIP",
                Money.of(new BigDecimal("250.00"), "ILS"),
                50);
    }

    // =========================================================
    // GET /api/events (Search & Pagination Branches)
    // =========================================================

    @Test
    @DisplayName("Search: Valid inputs with standard pagination")
    void searchEvents_standard() throws Exception {
        Event event = sampleEvent();
        when(catalogService.search(any(), any(), any())).thenReturn(List.of(event));
        when(catalogService.resolveArtistName(event)).thenReturn("Desert Rockers");
        when(catalogService.getZones(event.id())).thenReturn(List.of(sampleZone()));

        mockMvc.perform(get("/api/events")
                        .param("artist", "Desert")
                        .param("date", "2026-06-20")
                        .param("priceRange", "100,300")
                        .param("page", "0")
                        .param("size", "20")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].eventId").value("EVT-1"));
    }

    @Test
    @DisplayName("Search: Empty parameters (tests null priceRange branch)")
    void searchEvents_emptyParams() throws Exception {
        when(catalogService.search(any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/events")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    @DisplayName("Search: Pagination out of bounds (from >= all.size)")
    void searchEvents_outOfBoundsPagination() throws Exception {
        Event event = sampleEvent();
        when(catalogService.search(any(), any(), any())).thenReturn(List.of(event));
        
        mockMvc.perform(get("/api/events")
                        .param("page", "5")
                        .param("size", "10")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    @DisplayName("Search: Size <= 0")
    void searchEvents_invalidSize() throws Exception {
        Event event = sampleEvent();
        when(catalogService.search(any(), any(), any())).thenReturn(List.of(event));

        mockMvc.perform(get("/api/events")
                        .param("size", "0")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    // =========================================================
    // parsePriceRange Branches
    // =========================================================

    @Test
    @DisplayName("PriceRange: Hyphen format instead of comma")
    void searchEvents_hyphenPriceRange() throws Exception {
        when(catalogService.search(any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/events")
                        .param("priceRange", "100-300")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PriceRange: Invalid format throws Exception")
    void searchEvents_invalidPriceRange() throws Exception {
        mockMvc.perform(get("/api/events")
                        .param("priceRange", "100")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    // =========================================================
    // GET /api/events/{eventId} (Detail & Edge Cases)
    // =========================================================

    @Test
    @DisplayName("Get Event: Full detail with zones and linked/unlinked map elements")
    void getEvent_returnsFullDetail() throws Exception {
        Event event = sampleEvent();
        when(catalogService.findById(new EventId("EVT-1"))).thenReturn(event);
        when(catalogService.resolveArtistName(event)).thenReturn("Desert Rockers");
        when(catalogService.getZones(event.id())).thenReturn(List.of(sampleZone()));

        mockMvc.perform(get("/api/events/EVT-1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("EVT-1"))
                .andExpect(jsonPath("$.venueMap[0].linkedZoneId").isEmpty())
                .andExpect(jsonPath("$.venueMap[1].linkedZoneId").value("VIP-ZONE"))
                .andExpect(jsonPath("$.priceSummary.minPrice").value(250.00));
    }

    @Test
    @DisplayName("Get Event: Event with NO zones (tests eventPriceSummary empty branch)")
    void getEvent_noZones() throws Exception {
        Event event = sampleEvent();
        when(catalogService.findById(new EventId("EVT-1"))).thenReturn(event);
        when(catalogService.resolveArtistName(event)).thenReturn("Desert Rockers");
        when(catalogService.getZones(event.id())).thenReturn(List.of());

        mockMvc.perform(get("/api/events/EVT-1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zones").isEmpty())
                .andExpect(jsonPath("$.priceSummary.minPrice").value(0))
                .andExpect(jsonPath("$.priceSummary.maxPrice").value(0))
                .andExpect(jsonPath("$.priceSummary.currency").value(""));
    }
}