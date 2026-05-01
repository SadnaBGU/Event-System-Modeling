/**
 * Zone aggregate (separate from Event for concurrency).
 *
 * <p>Owns: {@code Zone} aggregate root + {@code Row} + {@code Seat} + {@code SeatStatus} +
 * {@code ZoneRepository} port. Independent locking unit so concurrent purchases for
 * different zones don't contend.
 *
 * <p>See: {@code docs/3_Event_Zone.mmd}.
 */
package com.eventsystem.domain.zone;
