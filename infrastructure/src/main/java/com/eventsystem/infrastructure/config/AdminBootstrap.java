package com.eventsystem.infrastructure.config;

import com.eventsystem.application.security.PasswordHasher;
import com.eventsystem.domain.member.HashedCredentials;
import com.eventsystem.domain.member.Member;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.member.MemberRepository;
import com.eventsystem.domain.member.PersonalDetails;
import com.eventsystem.domain.platform.Platform;
import com.eventsystem.domain.platform.PlatformRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bootstraps the singleton {@link Platform} aggregate and an initial system admin
 * {@link Member} on first startup. Idempotent: if a Platform instance already exists,
 * {@link #run()} is a no-op.
 */
public class AdminBootstrap {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final PlatformRepository platformRepo;
    private final MemberRepository memberRepo;
    private final PasswordHasher passwordHasher;
    private final BootstrapProperties props;

    public AdminBootstrap(PlatformRepository platformRepo,
                          MemberRepository memberRepo,
                          PasswordHasher passwordHasher,
                          BootstrapProperties props) {
        this.platformRepo = platformRepo;
        this.memberRepo = memberRepo;
        this.passwordHasher = passwordHasher;
        this.props = props;
    }

    public void run() {
        if (platformRepo.findInstance().isPresent()) {
            log.info("Platform already initialised; skipping bootstrap.");
            return;
        }

        BootstrapProperties.Admin a = props.admin();
        validate(a);

        // Reuse existing admin member if username already present (e.g. after a reset that kept members)
        Member admin = memberRepo.findByUsername(a.username()).orElseGet(() -> {
            HashedCredentials creds = passwordHasher.hash(a.password());
            PersonalDetails details = new PersonalDetails(
                    a.firstName(), a.lastName(), a.email(), a.dateOfBirth());
            Member m = new Member(MemberId.generate(), a.username(), creds, details);
            memberRepo.save(m);
            log.info("Bootstrap: created initial admin member username={} memberId={}",
                    m.getUsername(), m.getMemberId().value());
            return m;
        });

        Platform platform = new Platform(
                admin.getMemberId(),
                props.defaultReservationTimeout(),
                props.queueLoadThreshold());
        platform.activate();
        platformRepo.save(platform);
        log.info("Bootstrap: platform initialised and ACTIVE with admin={}", admin.getUsername());
    }

    private static void validate(BootstrapProperties.Admin a) {
        if (a == null
                || isBlank(a.username()) || isBlank(a.password())
                || isBlank(a.firstName()) || isBlank(a.lastName())
                || isBlank(a.email()) || a.dateOfBirth() == null) {
            throw new IllegalStateException(
                    "Missing required bootstrap admin configuration. " +
                    "Provide username/password/firstName/lastName/email/dateOfBirth.");
        }
        if (a.password().length() < 8) {
            throw new IllegalStateException("Bootstrap admin password must be at least 8 characters");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}

