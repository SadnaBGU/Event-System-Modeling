package com.eventsystem.infrastructure;

import com.eventsystem.application.admin.AdminService;
import com.eventsystem.application.auth.AuthService;
import com.eventsystem.application.lottery.LotteryService;
import com.eventsystem.application.member.MemberService;
import com.eventsystem.infrastructure.config.AdminBootstrap;
import com.eventsystem.infrastructure.config.BootstrapProperties;
import com.eventsystem.infrastructure.persistence.InMemoryLotteryRepository;
import com.eventsystem.infrastructure.persistence.InMemoryMemberRepository;
import com.eventsystem.infrastructure.persistence.InMemoryPlatformRepository;
import com.eventsystem.infrastructure.security.BCryptPasswordHasher;
import com.eventsystem.infrastructure.security.JwtTokenService;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;

/**
 * Composition root: builds every adapter and service, then runs the bootstrap
 * that seeds the singleton Platform aggregate and an initial admin Member.
 */
public final class EventSystemApplication {

    private EventSystemApplication() {}

    public static void main(String[] args) {
        // --- Configuration constants ---
        String jwtSecret = "CHANGE_ME_DEV_ONLY_0123456789abcdef";
        Duration tokenValidity = Duration.ofHours(1);
        int bcryptStrength = 12;
        Duration lotteryCodeValidity = Duration.ofMinutes(15);
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
        InMemoryLotteryRepository lotteryRepo = new InMemoryLotteryRepository();
        BCryptPasswordHasher passwordHasher = new BCryptPasswordHasher(bcryptStrength);
        JwtTokenService tokenService = new JwtTokenService(jwtSecret);

        // --- Application services ---
        new AuthService(memberRepo, passwordHasher, tokenService, tokenValidity);
        new MemberService(memberRepo);
        new AdminService(platformRepo, memberRepo);
        new LotteryService(lotteryRepo, new SecureRandom(), Clock.systemUTC(), lotteryCodeValidity);

        // --- Bootstrap ---
        new AdminBootstrap(platformRepo, memberRepo, passwordHasher, bootstrapProps).run();
    }
}


