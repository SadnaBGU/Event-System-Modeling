package com.eventsystem.infrastructure;

import com.eventsystem.application.admin.AdminService;
import com.eventsystem.application.auth.AuthService;
import com.eventsystem.application.member.MemberService;
import com.eventsystem.infrastructure.config.AdminBootstrap;
import com.eventsystem.infrastructure.config.BootstrapProperties;
import com.eventsystem.infrastructure.persistence.InMemoryMemberRepository;
import com.eventsystem.infrastructure.persistence.InMemoryPlatformRepository;
import com.eventsystem.infrastructure.security.BCryptPasswordHasher;
import com.eventsystem.infrastructure.security.JwtTokenService;

import java.time.Duration;
import java.time.LocalDate;

/**
 * Composition root: constructs every adapter and service by hand, then runs the
 * bootstrap that seeds the singleton Platform aggregate and an initial admin Member.
 *
 * V1 keeps all configuration as constants here — no YAML, no env vars.
 *
 * Wiring note: LotteryService is added by the stream5-lottery PR.
 */
public final class EventSystemApplication {

    private EventSystemApplication() {}

    public static void main(String[] args) {
        // --- Configuration constants (formerly application.yml) ---
        String jwtSecret = "CHANGE_ME_DEV_ONLY_0123456789abcdef";
        Duration tokenValidity = Duration.ofHours(1);
        int bcryptStrength = 12;
        BootstrapProperties bootstrapProps = new BootstrapProperties(
                new BootstrapProperties.Admin(
                        "admin",
                        "changeme123",
                        "Initial",
                        "Admin",
                        "admin@eventsystem.local",
                        LocalDate.of(1990, 1, 1)),
                Duration.ofMinutes(15),
                100);

        // --- Adapters ---
        InMemoryMemberRepository memberRepo = new InMemoryMemberRepository();
        InMemoryPlatformRepository platformRepo = new InMemoryPlatformRepository();
        BCryptPasswordHasher passwordHasher = new BCryptPasswordHasher(bcryptStrength);
        JwtTokenService tokenService = new JwtTokenService(jwtSecret);

        // --- Application services ---
        new AuthService(memberRepo, passwordHasher, tokenService, tokenValidity);
        new MemberService(memberRepo);
        new AdminService(platformRepo, memberRepo);

        // TODO(stream5-lottery): wire InMemoryLotteryRepository + LotteryService

        // --- Bootstrap ---
        new AdminBootstrap(platformRepo, memberRepo, passwordHasher, bootstrapProps).run();
    }
}
