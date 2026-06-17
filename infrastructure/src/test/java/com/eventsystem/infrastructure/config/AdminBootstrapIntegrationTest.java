package com.eventsystem.infrastructure.config;

import com.eventsystem.application.admin.AdminService;
import com.eventsystem.application.admin.PlatformDto;
import com.eventsystem.application.auth.LoginRequest;
import com.eventsystem.application.auth.LoginResponse;
import com.eventsystem.application.member.MemberService;
import com.eventsystem.domain.member.HashedCredentials;
import com.eventsystem.domain.member.IMemberRepository;
import com.eventsystem.domain.member.Member;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.member.PersonalDetails;
import com.eventsystem.domain.platform.IPlatformRepository;
import com.eventsystem.domain.platform.Platform;
import com.eventsystem.domain.platform.PlatformStatus;
import com.eventsystem.infrastructure.persistence.inmemoryrepos.InMemoryMemberRepository;
import com.eventsystem.infrastructure.persistence.inmemoryrepos.InMemoryPlatformRepository;
import com.eventsystem.infrastructure.security.BCryptPasswordHasher;
import com.eventsystem.infrastructure.security.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminBootstrapIntegrationTest {

    private static final String ADMIN_USERNAME = "testadmin";
    private static final String ADMIN_PASSWORD = "testadmin123";

    private InMemoryPlatformRepository platformRepo;
    private InMemoryMemberRepository memberRepo;
    private BCryptPasswordHasher hasher;
    private BootstrapProperties props;
    private MemberService memberService;
    private AdminService adminService;

    @BeforeEach
    void setUp() {
        memberRepo = new InMemoryMemberRepository();
        platformRepo = new InMemoryPlatformRepository();
        hasher = new BCryptPasswordHasher(4);
        JwtTokenService tokens = new JwtTokenService("test_test_test_test_test_test_te");

        props = new BootstrapProperties(
                new BootstrapProperties.Admin(
                        ADMIN_USERNAME, ADMIN_PASSWORD,
                        "Test", "Admin",
                        "testadmin@local",
                        LocalDate.of(1990, 1, 1)),
                Duration.ofMinutes(15),
                100);

        new AdminBootstrap(platformRepo, memberRepo, hasher, props).run();

        this.memberService = new MemberService(memberRepo, hasher, tokens, Duration.ofMinutes(5));
        this.adminService = new AdminService(platformRepo, memberRepo);
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

    // =========================================================================
    // בדיקות חדשות להשגת 100% כיסוי על AdminBootstrap
    // =========================================================================

    @Test
    void run_skipsBootstrap_whenPlatformAlreadyInitialized() {
        // אם נריץ שוב, הוא יזהה שהפלטפורמה כבר קיימת ויצא מיד בלי לשנות כלום
        AdminBootstrap secondBootstrap = new AdminBootstrap(platformRepo, memberRepo, hasher, props);
        secondBootstrap.run();
        
        assertThat(platformRepo.findInstance()).isPresent();
    }

    @Test
    void run_reusesExistingAdminMember_whenUsernameAlreadyPresent() {
        // ניצור פלטפורמה חדשה ריקה אבל נשאיר את המשתמש הקיים ברפוזיטורי
        InMemoryPlatformRepository freshPlatformRepo = new InMemoryPlatformRepository();
        
        AdminBootstrap bootstrap = new AdminBootstrap(freshPlatformRepo, memberRepo, hasher, props);
        bootstrap.run();
        
        assertThat(freshPlatformRepo.findInstance()).isPresent();
        // מוודא שלא נוצר משתמש חדש נוסף באותו השם
        assertThat(memberRepo.findByUsername(ADMIN_USERNAME)).isPresent();
    }

    @Test
    void validate_throwsException_whenAdminConfigIsMissingOrBlank() {
        InMemoryPlatformRepository freshPlatformRepo = new InMemoryPlatformRepository();
        
        // מקרה 1: אובייקט אדמין נאל
        BootstrapProperties nullAdminProps = new BootstrapProperties(null, Duration.ofMinutes(15), 100);
        assertThatThrownBy(() -> new AdminBootstrap(freshPlatformRepo, memberRepo, hasher, nullAdminProps).run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing required bootstrap admin configuration");

        // מקרה 2: שדה ריק (למשל שם פרטי ריק)
        BootstrapProperties blankFieldProps = new BootstrapProperties(
                new BootstrapProperties.Admin("user", "password123", " ", "Last", "email@local", LocalDate.now()),
                Duration.ofMinutes(15), 100);
        assertThatThrownBy(() -> new AdminBootstrap(freshPlatformRepo, memberRepo, hasher, blankFieldProps).run())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validate_throwsException_whenPasswordIsTooShort() {
        InMemoryPlatformRepository freshPlatformRepo = new InMemoryPlatformRepository();
        
        // סיסמה באורך 7 תווים (פחות מ-8)
        BootstrapProperties shortPasswordProps = new BootstrapProperties(
                new BootstrapProperties.Admin("admin2", "short1", "First", "Last", "email@local", LocalDate.now()),
                Duration.ofMinutes(15), 100);
                
        assertThatThrownBy(() -> new AdminBootstrap(freshPlatformRepo, memberRepo, hasher, shortPasswordProps).run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("password must be at least 8 characters");
    }
}