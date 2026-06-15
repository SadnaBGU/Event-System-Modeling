package com.eventsystem.infrastructure.persistence.springrepostests;

import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.lottery.Lottery;
import com.eventsystem.domain.lottery.LotteryId;
import com.eventsystem.domain.lottery.LotteryStatus;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.infrastructure.persistence.springrepos.PostgresLotteryRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@EntityScan(basePackages = "com.eventsystem.domain")
@Import(PostgresLotteryRepository.class)
class PostgresLotteryRepositoryTest extends BasePostgresTest {

    @Autowired
    private PostgresLotteryRepository lotteryRepository;

    @Autowired
    private EntityManager em;

    private EventId eventId;
    private MemberId member1;
    private MemberId member2;

    @BeforeEach
    void setUp() {
        eventId = new EventId("EVT-100");
        member1 = new MemberId("MEM-1");
        member2 = new MemberId("MEM-2");
    }

    @Test
    void saveAndFindById_savesLotteryWithRegistrationsAndWinners() {
        // Arrange
        LotteryId lotteryId = LotteryId.generate();
        Lottery lottery = new Lottery(lotteryId, eventId);
        
        lottery.register(member1);
        lottery.register(member2);
        
        lottery.close();
        lottery.draw(1, RandomGenerator.getDefault(), Instant.now(), Duration.ofMinutes(15));

        // Act
        lotteryRepository.save(lottery);
        em.flush();
        em.clear();
        
        Optional<Lottery> foundOpt = lotteryRepository.findById(lotteryId);

        // Assert
        assertThat(foundOpt).isPresent();
        Lottery found = foundOpt.get();

        assertThat(found.getEventId()).isEqualTo(eventId);
        assertThat(found.getStatus()).isEqualTo(LotteryStatus.DRAWN);
        assertThat(found.getRegistrations()).hasSize(2).contains(member1, member2);
        assertThat(found.getWinners()).hasSize(1);
    }

    @Test
    void findByEventId_returnsCorrectLottery() {
        // Arrange
        LotteryId lotteryId1 = LotteryId.generate();
        Lottery lottery1 = new Lottery(lotteryId1, eventId);
        lotteryRepository.save(lottery1);

        LotteryId lotteryId2 = LotteryId.generate();
        Lottery lottery2 = new Lottery(lotteryId2, new EventId("EVT-999"));
        lotteryRepository.save(lottery2);

        em.flush();
        em.clear();

        // Act
        Optional<Lottery> foundOpt = lotteryRepository.findByEventId(eventId);

        // Assert
        assertThat(foundOpt).isPresent();
        assertThat(foundOpt.get().getId()).isEqualTo(lotteryId1);
    }
}