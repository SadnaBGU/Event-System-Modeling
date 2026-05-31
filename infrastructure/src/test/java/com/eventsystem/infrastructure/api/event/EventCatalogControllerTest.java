package com.eventsystem.infrastructure.api.event;

import com.eventsystem.application.event.EventCatalogService;
import com.eventsystem.domain.event.Event;
import com.eventsystem.domain.event.EventDetails;
import com.eventsystem.domain.event.EventId;
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
        Event event = new Event(new EventId("EVT-1"), "COMP-1", details, VenueMap.empty());
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

    @Test
    @DisplayName("GET /api/events returns paginated catalog results")
    void searchEvents_returnsPaginationWrapper() throws Exception {
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
                .andExpect(jsonPath("$.currentPage").value(0))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.items[0].eventId").value("EVT-1"))
                .andExpect(jsonPath("$.items[0].eventName").value("Desert Rock Festival"))
                .andExpect(jsonPath("$.items[0].artist").value("Desert Rockers"))
                .andExpect(jsonPath("$.items[0].zones[0].zoneId").value("VIP-ZONE"))
                .andExpect(jsonPath("$.items[0].priceSummary.minPrice").value(250.00));
    }

    @Test
    @DisplayName("GET /api/events/{eventId} returns full event detail")
    void getEvent_returnsFullDetail() throws Exception {
        Event event = sampleEvent();
        when(catalogService.findById(new EventId("EVT-1"))).thenReturn(event);
        when(catalogService.resolveArtistName(event)).thenReturn("Desert Rockers");
        when(catalogService.getZones(event.id())).thenReturn(List.of(sampleZone()));

        mockMvc.perform(get("/api/events/EVT-1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("EVT-1"))
                .andExpect(jsonPath("$.eventName").value("Desert Rock Festival"))
                .andExpect(jsonPath("$.artist").value("Desert Rockers"))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.salesMethod").value("REGULAR"))
                .andExpect(jsonPath("$.zones[0].price").value(250.00))
                .andExpect(jsonPath("$.venueMap").isArray());
    }
}
