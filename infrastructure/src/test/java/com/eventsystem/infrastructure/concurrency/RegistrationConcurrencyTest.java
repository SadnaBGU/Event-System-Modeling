package com.eventsystem.infrastructure.concurrency;

import com.eventsystem.application.appexceptions.UsernameAlreadyTakenException;
import com.eventsystem.application.auth.AuthService;
import com.eventsystem.application.auth.RegisterMemberRequest;
import com.eventsystem.application.appexceptions.UsernameAlreadyTakenException;
import com.eventsystem.application.security.ITokenService;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.infrastructure.persistence.InMemoryMemberRepository;
import com.eventsystem.infrastructure.security.BCryptPasswordHasher;
import com.eventsystem.infrastructure.security.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
 * Concurrency stress tests against the real wired stack
 * ({@link AuthService} + {@link InMemoryMemberRepository} + BCrypt + JWT).
 *
 * Validates that repository invariants (username uniqueness) survive parallel access.
 */
class RegistrationConcurrencyTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    private InMemoryMemberRepository repo;
    private AuthService auth;

    @BeforeEach
    void setUp() {
        repo = new InMemoryMemberRepository();
        BCryptPasswordHasher hasher = new BCryptPasswordHasher(4); // low cost = fast tests
        ITokenService tokens = new JwtTokenService(SECRET);
        auth = new AuthService(repo, hasher, tokens, Duration.ofHours(1));
    }

    private RegisterMemberRequest req(String username) {
        return new RegisterMemberRequest(
                username, "password123", "F", "L", username + "@x", LocalDate.of(1990, 1, 1));
    }

    @Test
    void concurrentRegistrationsOfSameUsername_onlyOneSucceeds() throws InterruptedException {
        int threads = 50;
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
                        auth.register(req("racer"));
                        successes.incrementAndGet();
                    } catch (UsernameAlreadyTakenException | IllegalStateException expected) {
                        // App-level dup detected, OR repo-level putIfAbsent race-loser
                        failures.incrementAndGet();
                    } catch (Exception e) {
                        // Any other exception is a real bug
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
        assertThat(repo.size()).isEqualTo(1);
        assertThat(repo.findByUsername("racer")).isPresent();
    }

    @Test
    void concurrentRegistrationsOfDistinctUsernames_allSucceed() throws InterruptedException {
        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        List<MemberId> ids = new ArrayList<>();

        try {
            for (int i = 0; i < threads; i++) {
                final String username = "user" + i;
                pool.submit(() -> {
                    try {
                        start.await();
                        MemberId id = auth.register(req(username));
                        synchronized (ids) {
                            ids.add(id);
                        }
                    } catch (Exception e) {
                        throw new AssertionError("Failed to register " + username, e);
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

        assertThat(ids).hasSize(threads);
        assertThat(repo.size()).isEqualTo(threads);
        for (int i = 0; i < threads; i++) {
            assertThat(repo.findByUsername("user" + i)).isPresent();
        }
    }
}
