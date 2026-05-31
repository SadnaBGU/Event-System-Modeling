package com.eventsystem.domain.event;

import com.eventsystem.domain.domainexceptions.EventDomainException;
import com.eventsystem.domain.zone.ZoneId;
import com.eventsystem.domain.company.CompanyId;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UC15: Create and Configure Event
 *
 * Tests for UATs:
 * - UAT-41: Successful Event Creation
 * - UAT-42: Missing Required Fields
 *
 * UC7: Ticket Selection and Reservation
 *
 * Tests for UATs:
 * - UAT-16: Successful Reservation (Regular Event)
 * - UAT-17: Successful Lottery Reservation
 *
 * UC3: Virtual Queue and Load Management
 *
 * Tests for UATs:
 * - UAT-09: Sold Out While Queued
 */

class EventTest {

    private EventDetails defaultDetails() {
        return new EventDetails(
                "Test Event",
                List.of(LocalDateTime.now().plusDays(10)),
                "Test Category",
                "Here",
                "A test event"
        );
    }

    private Event createDraftEvent() {
        return Event.createDraft(
                new CompanyId("company-1"),
                defaultDetails(),
                VenueMap.empty()
        );
    }

    private Event createDraftEventWithZone(String zoneIdstr) {
        Event testEvent = createDraftEvent();
        ZoneId testZone = new ZoneId(zoneIdstr);
        testEvent.addZone(testZone);
        return testEvent;
    }

    @Test // UAT-41: Successful Event Creation
    void newEventStartsAsDraft() {
        Event event = createDraftEvent();

        assertThat(event.status()).isEqualTo(EventStatus.DRAFT);
        assertThat(event.isDraft()).isTrue();
    }

    @Test // UAT-42: Missing Required Fields / Invalid Venue Map Configuration
    void draftEventRequiresNonEmptyZonesToPublish() {
        Event event = createDraftEvent();

        assertThatThrownBy(event::publish)
                .isInstanceOf(EventDomainException.class);
    }

    @Test // UAT-41: Successful Event Creation
    void draftEventCanBePublishedAndCancelled() {
        Event event = createDraftEventWithZone("testZoneId");

        event.publish();

        assertThat(event.status()).isEqualTo(EventStatus.PUBLISHED);
        assertThat(event.isPublished()).isTrue();

        event.cancel();

        assertThat(event.status()).isEqualTo(EventStatus.CANCELLED);
        assertThat(event.isCancelled()).isTrue();
    }

    @Test // Supporting test for event lifecycle after publication
    void publishedEventCanBeMarkedOver() {
        Event event = createDraftEventWithZone("testZoneId");

        event.publish();
        event.over();

        assertThat(event.status()).isEqualTo(EventStatus.OVER);
        assertThat(event.isOver()).isTrue();
    }

    @Test // UAT-09: Sold Out While Queued
    void draftEventCanBePublishedThenSoldOutThenOver() {
        Event event = createDraftEventWithZone("testZoneId");

        event.publish();

        assertThat(event.status()).isEqualTo(EventStatus.PUBLISHED);
        assertThat(event.isPublished()).isTrue();

        event.markSoldOut();

        assertThat(event.status()).isEqualTo(EventStatus.SOLD_OUT);
        assertThat(event.isSoldOut()).isTrue();

        event.over();

        assertThat(event.status()).isEqualTo(EventStatus.OVER);
        assertThat(event.isOver()).isTrue();
    }

    @Test // Supporting test for UC15 / event lifecycle rules
    void cannotPublishPublishedOrCancelledEvent() {
        Event event = createDraftEventWithZone("testZoneId");

        event.publish();

        assertThatThrownBy(event::publish)
                .isInstanceOf(EventDomainException.class);
        
        event.cancel();

        assertThatThrownBy(event::publish)
                .isInstanceOf(EventDomainException.class);
    }

    @Test // Supporting test for UC15 / event lifecycle rules
    void cannotPublishPublishedOrOverEvent() {
        Event event = createDraftEventWithZone("testZoneId");

        event.publish();

        assertThatThrownBy(event::publish)
                .isInstanceOf(EventDomainException.class);
        
        event.over();

        assertThatThrownBy(event::publish)
                .isInstanceOf(EventDomainException.class);
    }

    @Test // UAT-61: Authorized Action Success — edit event details before publish
    void cannotUpdateDetailsAfterPublish() {
        Event event = createDraftEventWithZone("testZoneId");
        event.publish();

        EventDetails newDetails = new EventDetails(
                "Updated Concert",
                List.of(LocalDateTime.now().plusDays(20)),
                "Music",
                "Haifa",
                "Updated description"
        );

        assertThatThrownBy(() -> event.updateDetails(newDetails))
                .isInstanceOf(EventDomainException.class);
    }

    @Test // UAT-61: Authorized Action Success — edit venue map before publish
    void cannotUpdateVenueMapAfterPublish() {
        Event event = createDraftEventWithZone("testZoneId");
        event.publish();

        VenueMap newMap = VenueMap.empty().addElement(
                new MapElement("STAGE", "Main stage", 10, 20, null)
        );

        assertThatThrownBy(() -> event.updateVenueMap(newMap))
                .isInstanceOf(EventDomainException.class);
    }

    @Test // UAT-41: Successful Event Creation — configure venue zones
    void canAddAndRemoveZoneWhileDraft() {
        Event event = createDraftEvent();
        ZoneId zoneId = ZoneId.random();

        event.addZone(zoneId);

        assertThat(event.zoneIds()).containsExactly(zoneId);

        event.removeZone(zoneId);

        assertThat(event.zoneIds()).isEmpty();
    }

    @Test // Supporting test for UC15 — prevent invalid post-publication configuration
    void cannotAddZoneAfterPublish() {
        Event event = createDraftEventWithZone("testZoneId");
        event.publish();

        assertThatThrownBy(() -> event.addZone(ZoneId.random()))
                .isInstanceOf(EventDomainException.class);
    }

    @Test // Supporting test for UC15 — prevent invalid post-publication configuration
    void cannotRemoveZoneAfterPublish() {
        Event event = createDraftEvent();
        ZoneId zoneId = ZoneId.random();

        event.addZone(zoneId);
        event.publish();

        assertThatThrownBy(() -> event.removeZone(zoneId))
                .isInstanceOf(EventDomainException.class);
    }

    @Test // UAT-42: Missing/Invalid Venue Map Configuration
    void cannotAddSameZoneTwice() {
        Event event = createDraftEvent();
        ZoneId zoneId = ZoneId.random();

        event.addZone(zoneId);

        assertThatThrownBy(() -> event.addZone(zoneId))
                .isInstanceOf(EventDomainException.class);
    }

    @Test // UAT-42: Missing/Invalid Venue Map Configuration
    void cannotRemoveZoneThatDoesNotBelongToEvent() {
        Event event = createDraftEvent();

        assertThatThrownBy(() -> event.removeZone(ZoneId.random()))
                .isInstanceOf(EventDomainException.class);
    }

    @Test // UAT-09: Sold Out While Queued
    void onlyPublishedEventCanBeMarkedSoldOut() {
        Event event = createDraftEventWithZone("testZoneId");

        assertThatThrownBy(event::markSoldOut)
                .isInstanceOf(EventDomainException.class);

        event.publish();
        event.markSoldOut();

        assertThat(event.status()).isEqualTo(EventStatus.SOLD_OUT);
        assertThat(event.isSoldOut()).isTrue();
    }

    @Test // Supporting test for event lifecycle rules
    void cannotMarkDraftOrCancelledEventAsOver() {
        Event event = createDraftEventWithZone("testZoneId");

        assertThatThrownBy(event::over)
                .isInstanceOf(EventDomainException.class);
        
        event.cancel();

        assertThatThrownBy(event::over)
                .isInstanceOf(EventDomainException.class);
    }

    @Test // UAT-16: Successful Reservation (Regular Event)
    void publishedEventIsPurchasable() {
        Event event = createDraftEvent();
        event.addZone(ZoneId.random());
        event.publish();

        assertThat(event.isPurchasable()).isTrue();
    }

    @Test // UAT-16: Successful Reservation — event must be active/published
    void draftEventIsNotPurchasable() {
        Event event = createDraftEvent();

        assertThat(event.isPurchasable()).isFalse();
    }

    @Test // UAT-16: Successful Reservation — event must be active/published
    void cancelledEventIsNotPurchasable() {
        Event event = createDraftEvent();
        event.cancel();

        assertThat(event.isPurchasable()).isFalse();
    }

    @Test // UAT-16: Successful Reservation (Regular Event)
    void requirePurchasable_whenPublished_doesNotThrow() {
        Event event = createDraftEvent();
        event.addZone(ZoneId.random());
        event.publish();

        assertThatCode(event::requirePurchasable)
                .doesNotThrowAnyException();
    }

    @Test // UAT-16: Successful Reservation — reject non-purchasable event
    void requirePurchasable_whenDraft_throws() {
        Event event = createDraftEvent();

        assertThatThrownBy(event::requirePurchasable)
                .isInstanceOf(EventDomainException.class);
    }

    @Test // UAT-16: Successful Reservation (Regular Event)
    void newEventDefaultsToRegularSalesMethod() {
        Event event = createDraftEvent();

        assertThat(event.salesMethod()).isEqualTo(SalesMethod.REGULAR);
        assertThat(event.isMethodSale()).isTrue();
        assertThat(event.isMethodQueue()).isFalse();
        assertThat(event.isMethodLottery()).isFalse();
    }

    @Test // UC3 / UAT-20: Threshold Exceeded Queue Created
    void draftEventCanSetSalesMethodToQueue() {
        Event event = createDraftEvent();

        event.setSalesMethod(SalesMethod.VIRTUAL_QUEUE);

        assertThat(event.salesMethod()).isEqualTo(SalesMethod.VIRTUAL_QUEUE);
        assertThat(event.isMethodQueue()).isTrue();
        assertThat(event.isMethodSale()).isFalse();
        assertThat(event.isMethodLottery()).isFalse();
    }

    @Test // UAT-17: Successful Lottery Reservation
    void draftEventCanSetSalesMethodToLottery() {
        Event event = createDraftEvent();

        event.setSalesMethod(SalesMethod.LOTTERY);

        assertThat(event.salesMethod()).isEqualTo(SalesMethod.LOTTERY);
        assertThat(event.isMethodLottery()).isTrue();
        assertThat(event.isMethodSale()).isFalse();
        assertThat(event.isMethodQueue()).isFalse();
    }

    @Test // UAT-16: Successful Reservation (Regular Event)
    void draftEventCanSetSalesMethodBackToRegular() {
        Event event = createDraftEvent();

        event.setSalesMethod(SalesMethod.LOTTERY);
        event.setSalesMethod(SalesMethod.REGULAR);

        assertThat(event.salesMethod()).isEqualTo(SalesMethod.REGULAR);
        assertThat(event.isMethodSale()).isTrue();
        assertThat(event.isMethodQueue()).isFalse();
        assertThat(event.isMethodLottery()).isFalse();
    }

    @Test // UAT-16: Successful Reservation (Regular Event)
    void setMethodRegularSetsRegularSalesMethod() {
        Event event = createDraftEvent();

        event.setMethodLottery();
        event.setMethodRegular();

        assertThat(event.salesMethod()).isEqualTo(SalesMethod.REGULAR);
        assertThat(event.isMethodSale()).isTrue();
    }

    @Test // UC3 / UAT-20: Threshold Exceeded Queue Created
    void setMethodQueueSetsVirtualQueueSalesMethod() {
        Event event = createDraftEvent();

        event.setMethodQueue();

        assertThat(event.salesMethod()).isEqualTo(SalesMethod.VIRTUAL_QUEUE);
        assertThat(event.isMethodQueue()).isTrue();
    }

    @Test // UAT-17: Successful Lottery Reservation
    void setMethodLotterySetsLotterySalesMethod() {
        Event event = createDraftEvent();

        event.setMethodLottery();

        assertThat(event.salesMethod()).isEqualTo(SalesMethod.LOTTERY);
        assertThat(event.isMethodLottery()).isTrue();
    }

    @Test // Supporting test for UC15 — invalid sales method configuration
    void salesMethodCannotBeNull() {
        Event event = createDraftEvent();

        assertThatThrownBy(() -> event.setSalesMethod(null))
                .isInstanceOf(NullPointerException.class);
    }
}