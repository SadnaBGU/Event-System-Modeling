package com.eventsystem.application.event;

import com.eventsystem.domain.event.Event;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.event.EventRepository;
import com.eventsystem.domain.zone.Zone;
import com.eventsystem.domain.zone.ZoneId;
import com.eventsystem.domain.zone.ZoneRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class EventPurchaseSupportService {

    private final EventRepository eventRepository;
    private final ZoneRepository zoneRepository;

    public EventPurchaseSupportService( EventRepository eventRepository, ZoneRepository zoneRepository) {
        this.eventRepository = Objects.requireNonNull(eventRepository, "eventRepository must not be null");
        this.zoneRepository = Objects.requireNonNull(zoneRepository, "zoneRepository must not be null" );
    }

    public boolean isPurchasable(EventId eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");

        Event event = loadEvent(eventId);

        return event.isPurchasable();
    }

    public void requirePurchasable(EventId eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");

        Event event = loadEvent(eventId);

        event.requirePurchasable();
    }

    public boolean areAllZonesFull(EventId eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");

        List<Zone> zones = zoneRepository.findByEventId(eventId);

        return !zones.isEmpty()
                && zones.stream().allMatch(zone -> zone.getAvailableCount() == 0);
    }

    public void markSoldOutIfAllZonesFull(EventId eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");

        Event event = loadEvent(eventId);

        if (!event.isPublished()) {
            return;
        }

        List<Zone> zones = zoneRepository.findByEventId(eventId);

        boolean allZonesFull = !zones.isEmpty()
                && zones.stream().allMatch(zone -> zone.getAvailableCount() == 0);

        if (allZonesFull) {
            event.markSoldOut();
            eventRepository.save(event);
        }
    }

    public Event getEventForSnapshot(EventId eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");

        return loadEvent(eventId);
    }

    public void requireZoneBelongsToEvent(EventId eventId, ZoneId zoneId) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(zoneId, "zoneId must not be null");

        Event event = loadEvent(eventId);

        event.requireZoneBelongsToEvent(zoneId);
    }

    private Event loadEvent(EventId eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("event not found: " + eventId.value()));
    }
}