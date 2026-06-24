package com.eventsystem.application.acceptance;

import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.lottery.Lottery;
import com.eventsystem.domain.lottery.LotteryId;
import com.eventsystem.domain.lottery.LotteryWinner;
import com.eventsystem.domain.member.MemberId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Acceptance tests for:
 *   UC 14 - Register for Purchase Right Lottery
 *
 * Uses the real {@link com.eventsystem.application.lottery.LotteryService} with
 * a seeded RNG (deterministic) and the fixture's fake lottery repository.
 */
class UC14_LotteryAcceptanceTest {

    // REQ: LOT-01
    // UC: UC 14 - Register for Purchase Right Lottery
    // UAT: UAT-39 - Lottery Registration
    @Test
    void memberRegistersForLottery_isAddedToTheLotteryPool() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();
        EventId eventId = app.eventId("event-lottery-1");
        MemberId member = app.memberId("member-1");

        LotteryId lotteryId = app.lotteryService.openLottery(eventId);
        app.lotteryService.register(member, lotteryId);

        Lottery lottery = app.lotteries.findById(lotteryId).orElseThrow();
        assertThat(lottery.getRegistrations()).contains(member);
    }

    // REQ: LOT-02
    // UC: UC 14 - Register for Purchase Right Lottery
    // UAT: UAT-40 - Lottery Win Dispatch
    @Test
    void afterDraw_winnerReceivesAuthCodeThatValidates() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();
        EventId eventId = app.eventId("event-lottery-1");
        MemberId member = app.memberId("member-1");

        LotteryId lotteryId = app.lotteryService.openLottery(eventId);
        app.lotteryService.register(member, lotteryId);

        app.lotteryService.draw(lotteryId, 1);

        Lottery lottery = app.lotteries.findById(lotteryId).orElseThrow();
        assertThat(lottery.getWinners()).hasSize(1);

        LotteryWinner winner = lottery.getWinners().iterator().next();
        assertThat(winner.memberId()).isEqualTo(member);

        // The issued authorization code validates back to the winning member.
        MemberId validated = app.lotteryService.validateCode(lotteryId, winner.permissionCode());
        assertThat(validated).isEqualTo(member);
    }
}