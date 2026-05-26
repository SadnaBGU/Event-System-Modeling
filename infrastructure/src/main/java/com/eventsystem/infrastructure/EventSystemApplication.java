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

import org.springframework.boot.SpringApplication;

/**
 * Composition root: builds every adapter and service, then runs the bootstrap
 * that seeds the singleton Platform aggregate and an initial admin Member.
 */
public final class EventSystemApplication {

    private EventSystemApplication() {}

    public static void main(String[] args) {
        SpringApplication.run(EventSystemApplication.class, args);
    }
}


