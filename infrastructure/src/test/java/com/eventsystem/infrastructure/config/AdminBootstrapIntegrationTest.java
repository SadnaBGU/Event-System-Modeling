package com.eventsystem.infrastructure.config;

import com.eventsystem.application.admin.AdminService;
import com.eventsystem.application.admin.PlatformDto;
import com.eventsystem.application.auth.LoginRequest;
import com.eventsystem.application.auth.LoginResponse;
import com.eventsystem.application.member.MemberService;
import com.eventsystem.domain.platform.PlatformStatus;
import com.eventsystem.infrastructure.persistence.springrepos.PostgresMemberRepository;
import com.eventsystem.infrastructure.persistence.springrepos.PostgresPlatformRepository;
import com.eventsystem.infrastructure.persistence.springrepostests.BasePostgresTest;
import com.eventsystem.infrastructure.security.BCryptPasswordHasher;
import com.eventsystem.infrastructure.security.JwtTokenService;
import com.eventsystem.infrastructure.testsupport.PostgresAvailableCondition;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link AdminBootstrap}, now running against the real
 * PostgreSQL test database (docker-compose service on {@code localhost:5434})
 * via {@link BasePostgresTest} — the in-memory repositories were removed in V3
 * (team task 2.2). Skipped automatically when the DB is unreachable so the build
 * stays green.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EntityScan(basePackages = "com.eventsystem.domain")
@Import({PostgresMemberRepository.class, PostgresPlatformRepository.class})
@ExtendWith(PostgresAvailableCondition.class)
class AdminBootstrapIntegrationTest extends BasePostgresTest {

    private static final String ADMIN_USERNAME = "testadmin";
    private static final String ADMIN_PASSWORD = "testadmin123";

    @Autowired
    private PostgresMemberRepository memberRepo;

    @Autowired
    private PostgresPlatformRepository platformRepo;

    @Autowired
    private EntityManager em;

    private BCryptPasswordHasher hasher;
    private JwtTokenService tokens;
    private BootstrapProperties props;

    @BeforeEach
    void setUp() {
        hasher = new BCryptPasswordHasher(4);
        tokens = new JwtTokenService("test_test_test_test_test_test_te");
        props = new BootstrapProperties(
                new BootstrapProperties.Admin(
                        ADMIN_USERNAME, ADMIN_PASSWORD,
                        "Test", "Admin",
                        "testadmin@local",
                        LocalDate.of(1990, 1, 1)),
                Duration.ofMinutes(15),
                100);
    }

    private void runBootstrap(BootstrapProperties p) {
        new AdminBootstrap(platformRepo, memberRepo, hasher, p).run();
        em.flush();
        em.clear();
    }

    private MemberService memberService() {
        return new MemberService(memberRepo, hasher, tokens, Duration.ofMinutes(5));
    }

    private AdminService adminService() {
        return new AdminService(platformRepo, memberRepo);
    }

    @Test
    void platformIsActive() {
        runBootstrap(props);

        assertThat(platformRepo.findInstance()).isPresent();
        assertThat(platformRepo.findInstance().orElseThrow().getStatus())
                .isEqualTo(PlatformStatus.ACTIVE);
    }

    @Test
    void initialAdminMemberWasCreated() {
        runBootstrap(props);

        assertThat(memberRepo.findByUsername(ADMIN_USERNAME)).isPresent();
    }

    @Test
    void initialAdminCanLogInAndIsRecognisedAsAdmin() {
        runBootstrap(props);

        LoginResponse resp = memberService().login(new LoginRequest(ADMIN_USERNAME, ADMIN_PASSWORD));
        assertThat(resp.token()).isNotBlank();

        PlatformDto dto = adminService().getPlatform(resp.memberId());
        assertThat(dto.systemAdmins()).contains(resp.memberId());
    }

    @Test
    void run_skipsBootstrap_whenPlatformAlreadyInitialized() {
        runBootstrap(props);

        // Re-running must detect the existing platform and exit without changes.
        runBootstrap(props);

        assertThat(platformRepo.findInstance()).isPresent();
        assertThat(memberRepo.findByUsername(ADMIN_USERNAME)).isPresent();
    }

    @Test
    void run_reusesExistingAdminMember_whenUsernameAlreadyPresent() {
        // First bootstrap creates the admin member + platform.
        runBootstrap(props);

        // Remove only the platform, keeping the existing admin member.
        em.createQuery("DELETE FROM Platform").executeUpdate();
        em.flush();
        em.clear();
        assertThat(platformRepo.findInstance()).isEmpty();

        // Second bootstrap must reuse the existing member rather than create a duplicate.
        runBootstrap(props);

        assertThat(platformRepo.findInstance()).isPresent();
        Long count = em.createQuery(
                        "SELECT COUNT(m) FROM Member m WHERE m.username = :u", Long.class)
                .setParameter("u", ADMIN_USERNAME)
                .getSingleResult();
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void validate_throwsException_whenAdminConfigIsMissingOrBlank() {
        // No platform exists yet, so run() proceeds to validation.
        BootstrapProperties nullAdminProps = new BootstrapProperties(null, Duration.ofMinutes(15), 100);
        assertThatThrownBy(() -> new AdminBootstrap(platformRepo, memberRepo, hasher, nullAdminProps).run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing required bootstrap admin configuration");

        BootstrapProperties blankFieldProps = new BootstrapProperties(
                new BootstrapProperties.Admin("user", "password123", " ", "Last", "email@local", LocalDate.now()),
                Duration.ofMinutes(15), 100);
        assertThatThrownBy(() -> new AdminBootstrap(platformRepo, memberRepo, hasher, blankFieldProps).run())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validate_throwsException_whenPasswordIsTooShort() {
        BootstrapProperties shortPasswordProps = new BootstrapProperties(
                new BootstrapProperties.Admin("admin2", "short1", "First", "Last", "email@local", LocalDate.now()),
                Duration.ofMinutes(15), 100);

        assertThatThrownBy(() -> new AdminBootstrap(platformRepo, memberRepo, hasher, shortPasswordProps).run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("password must be at least 8 characters");
    }
}