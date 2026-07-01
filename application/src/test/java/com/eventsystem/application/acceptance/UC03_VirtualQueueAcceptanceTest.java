package com.eventsystem.application.acceptance;

import com.eventsystem.application.order.QueueService.AdmissionStatus;
import com.eventsystem.domain.order.BuyerReference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Acceptance tests for:
 *   UC 3 - Virtual Queue and Load Management
 *
 * Uses the real {@link com.eventsystem.application.order.QueueService} with a
 * fake virtual-queue repository and the fixture's fake notification port.
 *
 * UAT-08 (queue inactivity timeout) is not covered here: admission tokens use a
 * fixed validity inside QueueService and there is no injectable clock, so token
 * expiry cannot be driven deterministically at this layer.
 */
class UC03_VirtualQueueAcceptanceTest {

    // REQ: QUE-01
    // UC: UC 3 - Virtual Queue and Load Management
    // UAT: UAT-06 - Enter Virtual Queue
    @Test
    void visitorEntersQueue_isPlacedInWaitingLineWithAPosition() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();
        BuyerReference visitor = app.memberBuyer("buyer-1");

        app.queueService.enqueueVisitor("event-1", visitor);

        AdmissionStatus status = app.queueService.getAdmissionStatus("event-1", visitor);
        assertThat(status.isAdmitted).isFalse();
        assertThat(status.position).isEqualTo(1);
    }

    // REQ: QUE-01
    // UC: UC 3 - Virtual Queue and Load Management
    // UAT: UAT-07 - Queue Turn Arrives
    @Test
    void whenBatchProcessed_waitingVisitorIsAdmittedAndNotified() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();
        BuyerReference visitor = app.memberBuyer("buyer-1");
        app.queueService.enqueueVisitor("event-1", visitor);

        app.queueService.processNextBatch("event-1");

        assertThat(app.queueService.checkAdmissionStatus("event-1", visitor)).isTrue();
        assertThat(app.notifications.queueTurns).contains("buyer-1:event-1");
    }

    // REQ: QUE-01
    // UC: UC 3 - Virtual Queue and Load Management
    // UAT: UAT-07 - Queue Turn Arrives
    @Test
    void nextBuyerIsAdmittedOnlyAfterCurrentBuyerReleasesTheSlot() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();
        BuyerReference first = app.memberBuyer("buyer-1");
        BuyerReference second = app.memberBuyer("buyer-2");

        // Both buyers join the queue; only the first is admitted into the single slot.
        app.queueService.enqueueVisitor("event-1", first);
        app.queueService.enqueueVisitor("event-1", second);
        app.queueService.processNextBatch("event-1");

        assertThat(app.queueService.checkAdmissionStatus("event-1", first)).isTrue();
        assertThat(app.queueService.checkAdmissionStatus("event-1", second)).isFalse();

        // While the first buyer is still purchasing (holding their token), the second
        // buyer must NOT be admitted, even if the queue is advanced again.
        app.queueService.processNextBatch("event-1");
        assertThat(app.queueService.checkAdmissionStatus("event-1", second)).isFalse();

        // Only once the first buyer finishes and their token is taken back is the
        // second buyer allowed to proceed to checkout.
        app.queueService.releaseAdmissionAndAdmitNext("event-1", first);

        assertThat(app.queueService.checkAdmissionStatus("event-1", first)).isFalse();
        assertThat(app.queueService.checkAdmissionStatus("event-1", second)).isTrue();
        assertThat(app.notifications.queueTurns).contains("buyer-2:event-1");
    }

    // REQ: QUE-01
    // UC: UC 3 - Virtual Queue and Load Management
    // UAT: UAT-09 - Sold Out While Queued
    @Test
    void whenEventSoldOut_waitingVisitorsAreClearedAndNotified() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();
        BuyerReference visitor = app.memberBuyer("buyer-1");
        app.queueService.enqueueVisitor("event-1", visitor);

        app.queueService.handleEventSoldOut("event-1");

        assertThat(app.notifications.soldOuts).contains("buyer-1:event-1");
        AdmissionStatus status = app.queueService.getAdmissionStatus("event-1", visitor);
        assertThat(status.isAdmitted).isFalse();
        assertThat(status.position).isEqualTo(-1);
    }
}
