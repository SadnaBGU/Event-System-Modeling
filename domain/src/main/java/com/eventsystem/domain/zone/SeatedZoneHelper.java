package com.eventsystem.domain.zone;

import java.util.List;
import java.util.function.Function;

import com.eventsystem.domain.domainexceptions.ZoneDomainException;

/**
 * Helper utility for common seated zone operations.
 * Encapsulates duplicate logic for reserving, releasing, and marking seats as sold
 * across Zone and VenueZone implementations.
 */
public class SeatedZoneHelper {

    /**
     * Reserves a seat given a seat finder function.
     *
     * @param seatFinder function that finds a seat by SeatId
     * @param seatId the seat to reserve
     * @throws Exception if seat is not found or cannot be reserved
     */
    public static void reserveSeat(Function<SeatId, Seat> seatFinder, SeatId seatId) {
        seatFinder.apply(seatId).reserve();
    }

    /**
     * Releases a seat given a seat finder function.
     *
     * @param seatFinder function that finds a seat by SeatId
     * @param seatId the seat to release
     * @throws Exception if seat is not found or cannot be released
     */
    public static void releaseSeat(Function<SeatId, Seat> seatFinder, SeatId seatId) {
        seatFinder.apply(seatId).release();
    }

    /**
     * Marks a seat as sold given a seat finder function.
     *
     * @param seatFinder function that finds a seat by SeatId
     * @param seatId the seat to mark as sold
     * @throws Exception if seat is not found or cannot be marked sold
     */
    public static void markSeatSold(Function<SeatId, Seat> seatFinder, SeatId seatId) {
        seatFinder.apply(seatId).markSold();
    }

    /**
     * Finds a seat in a flat list of seats.
     *
     * @param seats list of seats to search
     * @param seatId the seat id to find
     * @return the seat if found
     * @throws ZoneDomainException if seat is not found
     */
    public static Seat findSeatInList(List<Seat> seats, SeatId seatId) {
        return seats.stream()
                .filter(s -> s.seatId().equals(seatId))
                .findFirst()
                .orElseThrow(() -> new ZoneDomainException("seat not found: " + seatId.value()));
    }

    /**
     * Finds a seat across rows.
     *
     * @param rows list of rows to search
     * @param seatId the seat id to find
     * @return the seat if found
     * @throws ZoneDomainException if seat is not found
     */
    public static Seat findSeatInRows(List<Row> rows, SeatId seatId) {
        return rows.stream()
                .flatMap(r -> r.seats().stream())
                .filter(s -> s.seatId().equals(seatId))
                .findFirst()
                .orElseThrow(() -> new ZoneDomainException("seat not found: " + seatId.value()));
    }
}
