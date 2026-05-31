package com.eventsystem.infrastructure.api.event;

import com.eventsystem.application.event.EventCatalogService;
import com.eventsystem.application.event.EventCatalogService.PriceRange;
import com.eventsystem.domain.event.Event;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.event.EventStatus;
import com.eventsystem.domain.event.MapElement;
import com.eventsystem.domain.event.VenueMap;
import com.eventsystem.domain.zone.Zone;
import com.eventsystem.domain.zone.ZoneType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/events")
public class EventCatalogController {

    private final EventCatalogService catalogService;

    public EventCatalogController(EventCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("")
    public ResponseEntity<Map<String, Object>> searchEvents(
            @RequestParam(name = "artist", required = false) String artist,
            @RequestParam(name = "date", required = false) LocalDate date,
            @RequestParam(name = "priceRange", required = false) String priceRange,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {

        PriceRange range = parsePriceRange(priceRange);
        List<Map<String, Object>> all = catalogService.search(artist, date, range).stream()
                .map(this::toDetailMap)
                .toList();

        int from = Math.max(0, page * size);
        int to = Math.min(all.size(), from + size);
        List<Map<String, Object>> items = from >= all.size() ? List.of() : all.subList(from, to);
        int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) all.size() / size);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("page", page);
        payload.put("size", size);
        payload.put("totalElements", all.size());
        payload.put("totalPages", totalPages);
        payload.put("items", items);

        return ResponseEntity.ok(payload);
    }

    @GetMapping("/{eventId}")
    public ResponseEntity<Map<String, Object>> getEvent(@PathVariable String eventId) {
        Event event = catalogService.findById(new EventId(eventId));
        return ResponseEntity.ok(toDetailMap(event));
    }

    private Map<String, Object> toDetailMap(Event event) {
        String artist = catalogService.resolveArtistName(event);
        List<Map<String, Object>> zones = catalogService.getZones(event.id()).stream()
                .map(this::toZoneDto)
                .toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", event.id().value());
        payload.put("eventName", event.details().name());
        payload.put("artist", artist);
        payload.put("dates", event.details().dates().stream().map(LocalDateTime::toString).toList());
        payload.put("category", event.details().category());
        payload.put("location", event.details().location());
        payload.put("description", event.details().description());
        payload.put("status", event.status().name());
        payload.put("salesMethod", event.salesMethod().name());
        payload.put("companyId", event.companyId().value());
        payload.put("zones", zones);
        payload.put("venueMap", event.venueMap().mapElements().stream().map(this::toMapElementDto).toList());
        payload.put("priceSummary", eventPriceSummary(zones));
        return payload;
    }

    private Map<String, Object> toZoneDto(Zone zone) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("zoneId", zone.zoneId().value());
        payload.put("zoneName", zone.zoneName());
        payload.put("zoneType", zone.zoneType().name());
        payload.put("price", zone.pricePerTicket().amount());
        payload.put("currency", zone.pricePerTicket().currency());
        payload.put("totalCapacity", zone.totalCapacity());
        payload.put("availableCount", zone.getAvailableCount());
        return payload;
    }

    private Map<String, Object> toMapElementDto(MapElement mapElement) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("elementType", mapElement.elementType());
        payload.put("label", mapElement.label());
        payload.put("positionX", mapElement.positionX());
        payload.put("positionY", mapElement.positionY());
        payload.put("linkedZoneId", mapElement.linkedZoneId() == null ? null : mapElement.linkedZoneId().value());
        return payload;
    }

    private Map<String, Object> eventPriceSummary(List<Map<String, Object>> zones) {
        if (zones.isEmpty()) {
            return Map.of("minPrice", BigDecimal.ZERO, "maxPrice", BigDecimal.ZERO, "currency", "");
        }
        BigDecimal min = zones.stream().map(zone -> (BigDecimal) zone.get("price")).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal max = zones.stream().map(zone -> (BigDecimal) zone.get("price")).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        String currency = (String) zones.get(0).get("currency");
        return Map.of("minPrice", min, "maxPrice", max, "currency", currency);
    }

    private PriceRange parsePriceRange(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().replace("-", ",");
        String[] parts = normalized.split(",");
        if (parts.length != 2) {
            throw new IllegalArgumentException("priceRange must be in the form min,max or min-max");
        }
        return new PriceRange(new BigDecimal(parts[0].trim()), new BigDecimal(parts[1].trim()));
    }

}
