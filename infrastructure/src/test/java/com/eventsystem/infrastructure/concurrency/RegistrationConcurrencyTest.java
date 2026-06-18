package com.eventsystem.infrastructure.concurrency;

import com.eventsystem.application.appexceptions.UsernameAlreadyTakenException;
import com.eventsystem.application.member.MemberService;
import com.eventsystem.application.security.ITokenService;
import com.eventsystem.application.auth.RegisterMemberRequest;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.infrastructure.persistence.springrepos.PostgresMemberRepository;
import com.eventsystem.infrastructure.persistence.springrepostests.BasePostgresTest;
import com.eventsystem.infrastructure.security.BCryptPasswordHasher;
import com.eventsystem.infrastructure.security.JwtTokenService;
import com.eventsystem.infrastructure.testsupport.PostgresAvailableCondition;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency stress tests for member registration, running against the real
 * PostgreSQL test database (the in-memory repositories were removed in V3, team
 * task 2.2). Username uniqueness is now enforced by a DB unique constraint
 * ({@code uk_members_username}); under a race the application-level check or the
 * constraint rejects all but one writer. Skipped automatically when the DB is
 * unreachable so the build stays green.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EntityScan(basePackages = "com.eventsystem.domain")
@Import(PostgresMemberRepository.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ExtendWith(PostgresAvailableCondition.class)
class RegistrationConcurrencyTest extends BasePostgresTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Autowired
    private PostgresMemberRepository repo;

    @Autowired
    private TransactionTemplate txTemplate;

    @Autowired
    private EntityManager em;

    private MemberService memberService;

    @BeforeEach
    void setUp() {
        // No per-test rollback under NOT_SUPPORTED, so clear committed members first.
        txTemplate.executeWithoutResult(s -> em.createQuery("DELETE FROM Member").executeUpdate());

        BCryptPasswordHasher hasher = new BCryptPasswordHasher(4); // low cost = fast tests
        ITokenService tokens = new JwtTokenService(SECRET);
        memberService = new MemberService(repo, hasher, tokens, Duration.ofHours(1));
    }

    @AfterEach
    void tearDown() {
        // Committed members are not rolled back under NOT_SUPPORTED — clean up.
        txTemplate.executeWithoutResult(s -> em.createQuery("DELETE FROM Member").executeUpdate());
    }

    private RegisterMemberRequest req(String username) {
        return new RegisterMemberRequest(
                username, "password123", "F", "L", username + "@x", LocalDate.of(1990, 1, 1));
    }

    private long countMembers() {
        return txTemplate.execute(s ->
                em.createQuery("SELECT COUNT(m) FROM Member m", Long.class).getSingleResult());
    }

    @Test
    void concurrentRegistrationsOfSameUsername_onlyOneSucceeds() throws InterruptedException {
        int threads = 30;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();

        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        txTemplate.executeWithoutResult(s -> memberService.register(req("racer")));
                        successes.incrementAndGet();
                    } catch (UsernameAlreadyTakenException | DataIntegrityViolationException expected) {
                        // App-level duplicate detected, OR DB unique-constraint race-loser.
                        failures.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        throw new AssertionError("Unexpected exception", e);
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(successes.get()).isEqualTo(1);
        assertThat(failures.get()).isEqualTo(threads - 1);
        assertThat(countMembers()).isEqualTo(1);
        boolean racerPresent = Boolean.TRUE.equals(
                txTemplate.execute(s -> repo.findByUsername("racer").isPresent()));
        assertThat(racerPresent).isTrue();
    }

    @Test
    void concurrentRegistrationsOfDistinctUsernames_allSucceed() throws InterruptedException {
        int threads = 30;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        List<MemberId> ids = new ArrayList<>();
        AtomicInteger errors = new AtomicInteger();

        try {
            for (int i = 0; i < threads; i++) {
                final String username = "user" + i;
                pool.submit(() -> {
                    try {
                        start.await();
                        MemberId id = txTemplate.execute(s -> memberService.register(req(username)));
                        synchronized (ids) {
                            ids.add(id);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(errors.get()).isZero();
        assertThat(ids).hasSize(threads);
        assertThat(countMembers()).isEqualTo(threads);
        for (int i = 0; i < threads; i++) {
            final String username = "user" + i;
            boolean present = Boolean.TRUE.equals(
                    txTemplate.execute(s -> repo.findByUsername(username).isPresent()));
            assertThat(present).isTrue();
        }
    }
}
