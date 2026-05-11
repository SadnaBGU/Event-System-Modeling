/**
 * Event aggregate.
 *
 * <p>Owns: {@code Event} aggregate root + {@code VenueMap} + {@code MapElement} + {@code EventStatus} +
 * {@code EventRepository} port. Cross-aggregate references to {@code Zone} are by ID.
 *
 * <p>See: {@code docs/diagrams/class diagrams/3_Event_Zone.mmd}.
 */
package com.eventsystem.domain.event;
