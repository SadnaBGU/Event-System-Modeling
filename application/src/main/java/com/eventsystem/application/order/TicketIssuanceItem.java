package com.eventsystem.application.order;

public record TicketIssuanceItem(
        String zoneId,
        String zoneName,
        int quantity,
        String seatId,
        String rowLabel,
        Integer seatNumber
) {
    public boolean isAssignedSeat() {
        return seatId != null && !seatId.isBlank();
    }
}