package com.eventsystem.infrastructure.concurrency;

import com.eventsystem.application.admin.AdminService;
import com.eventsystem.domain.member.HashedCredentials;
import com.eventsystem.domain.member.Member;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.member.PersonalDetails;
import com.eventsystem.domain.platform.Platform;
import com.eventsystem.domain.shared.ProviderId;
import com.eventsystem.infrastructure.persistence.springrepos.PostgresMemberRepository;
import com.eventsystem.infrastructure.persistence.springrepos.PostgresPlatformRepository;
import com.eventsystem.infrastructure.persistence.springrepostests.BasePostgresTest;
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
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency tests over {@link AdminService} writing to the singleton
 * {@link Platform} aggregate, running against the real PostgreSQL test database
 * (the in-memory repositories were removed in V3, team task 2.2).
 *
 * <p>{@link Platform} carries an {@code @Version} field, so concurrent writers
 * collide with {@link OptimisticLockingFailureException}; each operation runs in
 * its own transaction and retries until it commits — the canonical pattern used
 * by {@code ZoneConcurrencyTest}. Skipped automatically when the DB is down.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EntityScan(basePackages = "com.eventsystem.domain")
@Import({PostgresPlatformRepository.class, PostgresMemberRepository.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ExtendWith(PostgresAvailableCondition.class)
class PlatformConcurrencyTest extends BasePostgresTest {

    @Autowired
    private PostgresPlatformRepository platformRepo;

    @Autowired
    private PostgresMemberRepository memberRepo;

    @Autowired
    private TransactionTemplate txTemplate;

    @Autowired
    private EntityManager em;

    private AdminService admin;
    private MemberId rootAdmin;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        admin = new AdminService(platformRepo, memberRepo);
        rootAdmin = MemberId.generate();
        txTemplate.executeWithoutResult(s ->
                platformRepo.save(new Platform(rootAdmin, Duration.ofMinutes(15), 100)));
    }

    @AfterEach
    void tearDown() {
        // Committed data is not rolled back under NOT_SUPPORTED — clean up so we
        // don't leave a Platform singleton behind for other test classes.
        cleanDatabase();
    }

    private void cleanDatabase() {
        txTemplate.executeWithoutResult(s -> {
            em.createQuery("DELETE FROM Platform").executeUpdate();
            em.createQuery("DELETE FROM Member").executeUpdate();
        });
    }

    /** Runs {@code op} in its own transaction, retrying on optimistic-lock conflicts. */
    private void runWithRetry(Runnable op, AtomicInteger errors) {
        boolean resolved = false;
        while (!resolved) {
            try {
                txTemplate.executeWithoutResult(s -> op.run());
                resolved = true;
            } catch (OptimisticLockingFailureException retry) {
                // contended singleton row — reload and try again
            } catch (RuntimeException e) {
                errors.incrementAndGet();
                resolved = true;
            }
        }
    }

    private void saveMember(MemberId id, String username) {
        txTemplate.executeWithoutResult(s -> memberRepo.save(new Member(
                id, username,
                new HashedCredentials("h", "s", "BCrypt"),
                new PersonalDetails(LocalDate.of(1990, 1, 1), username + "@x", "F", "L"))));
    }

    @Test
    void concurrentDistinctPaymentProviders_allLandInSet() throws InterruptedException {
        int threads = 25;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger errors = new AtomicInteger();

        try {
            for (int i = 0; i < threads; i++) {
                final ProviderId provider = new ProviderId("provider-" + i);
                pool.submit(() -> {
                    try {
                        start.await();
                        runWithRetry(() -> admin.addPaymentProvider(rootAdmin, provider), errors);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(errors.get()).isZero();
        int size = txTemplate.execute(s ->
                platformRepo.findInstance().orElseThrow().getPaymentProviders().size());
        assertThat(size).isEqualTo(threads);
    }

    @Test
    void concurrentAdminPromotions_allLandInSet() throws InterruptedException {
        int threads = 25;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger errors = new AtomicInteger();

        // pre-create candidate members so addAdmin's existence check passes
        MemberId[] candidates = new MemberId[threads];
        for (int i = 0; i < threads; i++) {
            candidates[i] = MemberId.generate();
            saveMember(candidates[i], "u" + i);
        }

        try {
            for (int i = 0; i < threads; i++) {
                final MemberId id = candidates[i];
                pool.submit(() -> {
                    try {
                        start.await();
                        runWithRetry(() -> admin.addAdmin(rootAdmin, id), errors);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(errors.get()).isZero();
        int size = txTemplate.execute(s ->
                platformRepo.findInstance().orElseThrow().getSystemAdmins().size());
        // root admin + all promoted candidates
        assertThat(size).isEqualTo(threads + 1);
    }

    @Test
    void concurrentRemoveAdmin_neverEmptiesTheSet() throws InterruptedException {
        // Pre-populate with N admins, then have N+1 threads each try to remove one
        // (including the root). The last-admin guard MUST hold: at least one remains.
        int n = 20;
        MemberId[] admins = new MemberId[n];
        AtomicInteger setupErrors = new AtomicInteger();
        for (int i = 0; i < n; i++) {
            admins[i] = MemberId.generate();
            saveMember(admins[i], "u" + i);
            final MemberId target = admins[i];
            runWithRetry(() -> admin.addAdmin(rootAdmin, target), setupErrors);
        }
        assertThat(setupErrors.get()).isZero();

        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(n + 1); // +1 for root admin removal attempt

        try {
            for (int i = 0; i < n; i++) {
                final MemberId target = admins[i];
                pool.submit(() -> {
                    try {
                        start.await();
                        removeIgnoringGuard(target);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            // Also try to remove the root admin
            pool.submit(() -> {
                try {
                    start.await();
                    removeIgnoringGuard(rootAdmin);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });

            start.countDown();
            assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        int size = txTemplate.execute(s ->
                platformRepo.findInstance().orElseThrow().getSystemAdmins().size());
        assertThat(size).isGreaterThanOrEqualTo(1);
    }

    /** Retry on optimistic-lock conflict; the last-admin guard ({@link IllegalStateException}) is expected. */
    private void removeIgnoringGuard(MemberId target) {
        boolean resolved = false;
        while (!resolved) {
            try {
                txTemplate.executeWithoutResult(s -> admin.removeAdmin(rootAdmin, target));
                resolved = true;
            } catch (OptimisticLockingFailureException retry) {
                // reload and retry
            } catch (IllegalStateException guard) {
                resolved = true; // last-admin guard fired — acceptable
            }
        }
    }
}
