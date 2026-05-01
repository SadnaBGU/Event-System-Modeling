/**
 * Lottery aggregate.
 *
 * <p>Owns: {@code Lottery} aggregate root + {@code LotteryEntry} + {@code LotteryStatus} +
 * {@code LotteryRepository} port. Draws are deterministic given a seed (testable).
 *
 * <p>See: {@code docs/8_Lottery.mmd}.
 */
package com.eventsystem.domain.lottery;
