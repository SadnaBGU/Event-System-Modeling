package com.eventsystem.infrastructure.config;

import com.eventsystem.application.admin.AdminService;
import com.eventsystem.application.admin.PlatformDto;
import com.eventsystem.application.auth.LoginRequest;
import com.eventsystem.application.auth.LoginResponse;
import com.eventsystem.application.member.MemberService;
import com.eventsystem.domain.member.IMemberRepository;
import com.eventsystem.domain.platform.IPlatformRepository;
import com.eventsystem.domain.platform.PlatformStatus;
import com.eventsystem.infrastructure.persistence.InMemoryMemberRepository;
import com.eventsystem.infrastructure.persistence.InMemoryPlatformRepository;
import com.eventsystem.infrastructure.security.BCryptPasswordHasher;
import com.eventsystem.infrastructure.security.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wires the composition root by hand (same shape as
 * {@link com.eventsystem.infrastructure.EventSystemApplication#main}) and verifies that
 * {@link AdminBootstrap#run()} initialises the Platform and seeds the initial admin.
 *
 * Uses BCrypt strength 4 (fast) for tests.
 */
class AdminBootstrapIntegrationTest {

    private static final String ADMIN_USERNAME = "testadmin";
    private static final String ADMIN_PASSWORD = "testadmin123";

    private IPlatformRepository platformRepo;
    private IMemberRepository memberRepo;
    private MemberService memberService;
    private AdminService adminService;

    @BeforeEach
    void setUp() {
        InMemoryMemberRepository members = new InMemoryMemberRepository();
        InMemoryPlatformRepository platforms = new InMemoryPlatformRepository();
        BCryptPasswordHasher hasher = new BCryptPasswordHasher(4);
        JwtTokenService tokens = new JwtTokenService("test_test_test_test_test_test_te");

        BootstrapProperties props = new BootstrapProperties(
                new BootstrapProperties.Admin(
                        ADMIN_USERNAME, ADMIN_PASSWORD,
                        "Test", "Admin",
                        "testadmin@local",
                        LocalDate.of(1990, 1, 1)),
                Duration.ofMinutes(15),
                100);

        new AdminBootstrap(platforms, members, hasher, props).run();

        this.platformRepo = platforms;
        this.memberRepo = members;
        this.memberService = new MemberService(members, hasher, tokens, Duration.ofMinutes(5));
        this.adminService = new AdminService(platforms, members);
    }

    @Test
    void platformIsActive() {
        assertThat(platformRepo.findInstance()).isPresent();
        assertThat(platformRepo.findInstance().orElseThrow().getStatus())
                .isEqualTo(PlatformStatus.ACTIVE);
    }

    @Test
    void initialAdminMemberWasCreated() {
        assertThat(memberRepo.findByUsername(ADMIN_USERNAME)).isPresent();
    }

    @Test
    void initialAdminCanLogInAndIsRecognisedAsAdmin() {
        LoginResponse resp = memberService.login(new LoginRequest(ADMIN_USERNAME, ADMIN_PASSWORD));
        assertThat(resp.token()).isNotBlank();

        PlatformDto dto = adminService.getPlatform(resp.memberId());
        assertThat(dto.systemAdmins()).contains(resp.memberId());
    }
}

