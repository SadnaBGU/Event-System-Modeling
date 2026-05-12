package com.eventsystem.domain.event;

import com.eventsystem.domain.zone.ZoneId;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
                "company-1",
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

    @Test
    void newEventStartsAsDraft() {
        Event event = createDraftEvent();

        assertThat(event.status()).isEqualTo(EventStatus.DRAFT);
        assertThat(event.isDraft()).isTrue();
    }

    @Test
    void draftEventRequiresNonEmptyZonesToPublish() {
        Event event = createDraftEvent();

        assertThatThrownBy(event::publish)
                .isInstanceOf(EventDomainException.class);
    }

    @Test
    void draftEventCanBePublishedAndCancelled() {
        Event event = createDraftEventWithZone("testZoneId");

        event.publish();

        assertThat(event.status()).isEqualTo(EventStatus.PUBLISHED);
        assertThat(event.isPublished()).isTrue();

        event.cancel();

        assertThat(event.status()).isEqualTo(EventStatus.CANCELLED);
        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    void publishedEventCanBeMarkedOver() {
        Event event = createDraftEventWithZone("testZoneId");

        event.publish();
        event.over();

        assertThat(event.status()).isEqualTo(EventStatus.OVER);
        assertThat(event.isOver()).isTrue();
    }

    @Test
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

    @Test
    void cannotPublishPublishedOrCancelledEvent() {
        Event event = createDraftEventWithZone("testZoneId");

        event.publish();

        assertThatThrownBy(event::publish)
                .isInstanceOf(EventDomainException.class);
        
        event.cancel();

        assertThatThrownBy(event::publish)
                .isInstanceOf(EventDomainException.class);
    }

    @Test
    void cannotPublishPublishedOrOverEvent() {
        Event event = createDraftEventWithZone("testZoneId");

        event.publish();

        assertThatThrownBy(event::publish)
                .isInstanceOf(EventDomainException.class);
        
        event.over();

        assertThatThrownBy(event::publish)
                .isInstanceOf(EventDomainException.class);
    }

    @Test
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

    @Test
    void cannotUpdateVenueMapAfterPublish() {
        Event event = createDraftEventWithZone("testZoneId");
        event.publish();

        VenueMap newMap = VenueMap.empty().addElement(
                new MapElement("STAGE", "Main stage", 10, 20, null)
        );

        assertThatThrownBy(() -> event.updateVenueMap(newMap))
                .isInstanceOf(EventDomainException.class);
    }

    @Test
    void canAddAndRemoveZoneWhileDraft() {
        Event event = createDraftEvent();
        ZoneId zoneId = ZoneId.random();

        event.addZone(zoneId);

        assertThat(event.zoneIds()).containsExactly(zoneId);

        event.removeZone(zoneId);

        assertThat(event.zoneIds()).isEmpty();
    }

    @Test
    void cannotAddZoneAfterPublish() {
        Event event = createDraftEventWithZone("testZoneId");
        event.publish();

        assertThatThrownBy(() -> event.addZone(ZoneId.random()))
                .isInstanceOf(EventDomainException.class);
    }

    @Test
    void cannotRemoveZoneAfterPublish() {
        Event event = createDraftEvent();
        ZoneId zoneId = ZoneId.random();

        event.addZone(zoneId);
        event.publish();

        assertThatThrownBy(() -> event.removeZone(zoneId))
                .isInstanceOf(EventDomainException.class);
    }

    @Test
    void cannotAddSameZoneTwice() {
        Event event = createDraftEvent();
        ZoneId zoneId = ZoneId.random();

        event.addZone(zoneId);

        assertThatThrownBy(() -> event.addZone(zoneId))
                .isInstanceOf(EventDomainException.class);
    }

    @Test
    void cannotRemoveZoneThatDoesNotBelongToEvent() {
        Event event = createDraftEvent();

        assertThatThrownBy(() -> event.removeZone(ZoneId.random()))
                .isInstanceOf(EventDomainException.class);
    }

    @Test
    void onlyPublishedEventCanBeMarkedSoldOut() {
        Event event = createDraftEventWithZone("testZoneId");

        assertThatThrownBy(event::markSoldOut)
                .isInstanceOf(EventDomainException.class);

        event.publish();
        event.markSoldOut();

        assertThat(event.status()).isEqualTo(EventStatus.SOLD_OUT);
        assertThat(event.isSoldOut()).isTrue();
    }

    @Test
    void cannotMarkDraftOrCancelledEventAsOver() {
        Event event = createDraftEventWithZone("testZoneId");

        assertThatThrownBy(event::over)
                .isInstanceOf(EventDomainException.class);
        
        event.cancel();

        assertThatThrownBy(event::over)
                .isInstanceOf(EventDomainException.class);
    }

    @Test
    void publishedEventIsPurchasable() {
        Event event = createDraftEvent();
        event.addZone(ZoneId.random());
        event.publish();

        assertThat(event.isPurchasable()).isTrue();
    }

    @Test
    void draftEventIsNotPurchasable() {
        Event event = createDraftEvent();

        assertThat(event.isPurchasable()).isFalse();
    }

    @Test
    void cancelledEventIsNotPurchasable() {
        Event event = createDraftEvent();
        event.cancel();

        assertThat(event.isPurchasable()).isFalse();
    }

    @Test
    void requirePurchasable_whenPublished_doesNotThrow() {
        Event event = createDraftEvent();
        event.addZone(ZoneId.random());
        event.publish();

        assertThatCode(event::requirePurchasable)
                .doesNotThrowAnyException();
    }

    @Test
    void requirePurchasable_whenDraft_throws() {
        Event event = createDraftEvent();

        assertThatThrownBy(event::requirePurchasable)
                .isInstanceOf(EventDomainException.class);
    }

    @Test
    void newEventDefaultsToRegularSalesMethod() {
        Event event = createDraftEvent();

        assertThat(event.salesMethod()).isEqualTo(SalesMethod.REGULAR);
        assertThat(event.isMethodSale()).isTrue();
        assertThat(event.isMethodQueue()).isFalse();
        assertThat(event.isMethodLottery()).isFalse();
    }

    @Test
    void draftEventCanSetSalesMethodToQueue() {
        Event event = createDraftEvent();

        event.setSalesMethod(SalesMethod.VIRTUAL_QUEUE);

        assertThat(event.salesMethod()).isEqualTo(SalesMethod.VIRTUAL_QUEUE);
        assertThat(event.isMethodQueue()).isTrue();
        assertThat(event.isMethodSale()).isFalse();
        assertThat(event.isMethodLottery()).isFalse();
    }

    @Test
    void draftEventCanSetSalesMethodToLottery() {
        Event event = createDraftEvent();

        event.setSalesMethod(SalesMethod.LOTTERY);

        assertThat(event.salesMethod()).isEqualTo(SalesMethod.LOTTERY);
        assertThat(event.isMethodLottery()).isTrue();
        assertThat(event.isMethodSale()).isFalse();
        assertThat(event.isMethodQueue()).isFalse();
    }

    @Test
    void draftEventCanSetSalesMethodBackToRegular() {
        Event event = createDraftEvent();

        event.setSalesMethod(SalesMethod.LOTTERY);
        event.setSalesMethod(SalesMethod.REGULAR);

        assertThat(event.salesMethod()).isEqualTo(SalesMethod.REGULAR);
        assertThat(event.isMethodSale()).isTrue();
        assertThat(event.isMethodQueue()).isFalse();
        assertThat(event.isMethodLottery()).isFalse();
    }

    @Test
    void setMethodRegularSetsRegularSalesMethod() {
        Event event = createDraftEvent();

        event.setMethodLottery();
        event.setMethodRegular();

        assertThat(event.salesMethod()).isEqualTo(SalesMethod.REGULAR);
        assertThat(event.isMethodSale()).isTrue();
    }

    @Test
    void setMethodQueueSetsVirtualQueueSalesMethod() {
        Event event = createDraftEvent();

        event.setMethodQueue();

        assertThat(event.salesMethod()).isEqualTo(SalesMethod.VIRTUAL_QUEUE);
        assertThat(event.isMethodQueue()).isTrue();
    }

    @Test
    void setMethodLotterySetsLotterySalesMethod() {
        Event event = createDraftEvent();

        event.setMethodLottery();

        assertThat(event.salesMethod()).isEqualTo(SalesMethod.LOTTERY);
        assertThat(event.isMethodLottery()).isTrue();
    }

    @Test
    void salesMethodCannotBeNull() {
        Event event = createDraftEvent();

        assertThatThrownBy(() -> event.setSalesMethod(null))
                .isInstanceOf(NullPointerException.class);
    }
}