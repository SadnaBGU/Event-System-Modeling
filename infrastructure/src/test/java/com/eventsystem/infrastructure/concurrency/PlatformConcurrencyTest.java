package com.eventsystem.infrastructure.concurrency;

import com.eventsystem.application.admin.AdminService;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.platform.Platform;
import com.eventsystem.domain.shared.ProviderId;
import com.eventsystem.infrastructure.persistence.inmemoryrepos.InMemoryMemberRepository;
import com.eventsystem.infrastructure.persistence.inmemoryrepos.InMemoryPlatformRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency tests over {@link AdminService} writing to the singleton
 * {@link Platform} aggregate.
 */
class PlatformConcurrencyTest {

    private InMemoryPlatformRepository platformRepo;
    private InMemoryMemberRepository memberRepo;
    private AdminService admin;
    private MemberId rootAdmin;

    @BeforeEach
    void setUp() {
        platformRepo = new InMemoryPlatformRepository();
        memberRepo = new InMemoryMemberRepository();
        admin = new AdminService(platformRepo, memberRepo);
        rootAdmin = MemberId.generate();
        platformRepo.save(new Platform(rootAdmin, Duration.ofMinutes(15), 100));
    }

    @Test
    void concurrentDistinctPaymentProviders_allLandInSet() throws InterruptedException {
        int threads = 100;
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
                        admin.addPaymentProvider(rootAdmin, provider);
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
        Platform p = platformRepo.findInstance().orElseThrow();
        assertThat(p.getPaymentProviders()).hasSize(threads);
    }

    @Test
    void concurrentAdminPromotions_allLandInSet() throws InterruptedException {
        int threads = 100;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger errors = new AtomicInteger();

        // pre-create candidate members so addAdmin's existence check passes
        MemberId[] candidates = new MemberId[threads];
        for (int i = 0; i < threads; i++) {
            candidates[i] = MemberId.generate();
            memberRepo.save(new com.eventsystem.domain.member.Member(
                    candidates[i],
                    "u" + i,
                    new com.eventsystem.domain.member.HashedCredentials("h", "s", "BCrypt"),
                    new com.eventsystem.domain.member.PersonalDetails(java.time.LocalDate.of(1990, 1, 1), "e" + i + "@x", 
                            "F", "L")));
        }

        try {
            for (int i = 0; i < threads; i++) {
                final MemberId id = candidates[i];
                pool.submit(() -> {
                    try {
                        start.await();
                        admin.addAdmin(rootAdmin, id);
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
        Platform p = platformRepo.findInstance().orElseThrow();
        // root admin + all promoted candidates
        assertThat(p.getSystemAdmins()).hasSize(threads + 1);
    }

    @Test
    void concurrentRemoveAdmin_neverEmptiesTheSet() throws InterruptedException {
        // Pre-populate with N admins, then have N threads each try to remove a different one.
        // The last-admin guard MUST hold: at least one admin must remain.
        int n = 50;
        MemberId[] admins = new MemberId[n];
        for (int i = 0; i < n; i++) {
            admins[i] = MemberId.generate();
            memberRepo.save(new com.eventsystem.domain.member.Member(
                    admins[i],
                    "u" + i,
                    new com.eventsystem.domain.member.HashedCredentials("h", "s", "BCrypt"),
                    new com.eventsystem.domain.member.PersonalDetails(java.time.LocalDate.of(1990, 1, 1), "e" + i + "@x", "F", "L")));
            admin.addAdmin(rootAdmin, admins[i]);
        }

        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(n + 1); // +1 for root admin removal attempt

        try {
            for (int i = 0; i < n; i++) {
                final MemberId target = admins[i];
                pool.submit(() -> {
                    try {
                        start.await();
                        try { admin.removeAdmin(rootAdmin, target); } catch (IllegalStateException ignored) { }
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
                    try { admin.removeAdmin(rootAdmin, rootAdmin); } catch (IllegalStateException ignored) { }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });

            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        Platform p = platformRepo.findInstance().orElseThrow();
        assertThat(p.getSystemAdmins()).isNotEmpty();
    }
}
