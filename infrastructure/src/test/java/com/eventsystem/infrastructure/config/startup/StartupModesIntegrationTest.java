package com.eventsystem.infrastructure.config.startup;

import com.eventsystem.application.system.IExternalSystemsAvailabilityPort;
import com.eventsystem.domain.company.IProductionCompanyRepository;
import com.eventsystem.domain.member.IMemberRepository;
import com.eventsystem.domain.platform.IPlatformRepository;
import com.eventsystem.infrastructure.config.AdminBootstrap;
import com.eventsystem.infrastructure.config.BootstrapProperties;
import com.eventsystem.infrastructure.init.InitCommand;
import com.eventsystem.infrastructure.init.InitFileRunner;
import com.eventsystem.infrastructure.init.InitFileProcessor;
import com.eventsystem.infrastructure.init.StateFileParser;
import com.eventsystem.infrastructure.security.BCryptPasswordHasher;
import com.eventsystem.infrastructure.testsupport.PostgresAvailableCondition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mockito;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * REQ: SYS-01, SYS-02, PERS-06, PERS-07, PERS-08, TST-18, ROB-02
 * UC: UC1 Platform and Market Initialization
 *
 * Final integration coverage for startup mode semantics:
 * - EXISTING_DB keeps current DB state
 * - EMPTY_DB resets persisted state and recreates admin/platform baseline
 * - INIT_FILE resets persisted state, recreates admin/platform baseline, then replays init file
 * - invalid INIT_FILE content logs and continues, leaving only the admin/platform baseline
 */
@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(PostgresAvailableCondition.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5434/eventsdb",
        "spring.datasource.username=admin",
        "spring.datasource.password=admin123",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.hikari.maximum-pool-size=4",
        "spring.datasource.hikari.minimum-idle=0",
        "spring.datasource.hikari.idle-timeout=10000",

        // Keep automatic context startup quiet. These tests drive the startup steps manually.
        "eventsystem.bootstrap.enabled=false",
        "eventsystem.startup.mode=EXISTING_DB",
        "eventsystem.startup.init-state-file=",
        "eventsystem.startup.validate-external-systems=false"
})
class StartupModesIntegrationTest {

    @Autowired private StartupProperties startupProperties;
    @Autowired private StartupDBResetRunner dbResetRunner;
    @Autowired private DBResetService dbResetService;
    @Autowired private InitFileRunner initFileRunner;
    @Autowired private InitFileProcessor initFileProcessor;

    @Autowired private IMemberRepository memberRepository;
    @Autowired private IProductionCompanyRepository companyRepository;
    @Autowired private IPlatformRepository platformRepository;
    @Autowired private BCryptPasswordHasher passwordHasher;
    @Autowired private BootstrapProperties bootstrapProperties;

    @MockBean private IExternalSystemsAvailabilityPort externalSystemsAvailabilityPort;

    private final StateFileParser parser = new StateFileParser();

    @BeforeEach
    void cleanDatabaseBeforeEachTest() {
        startupProperties.setMode(StartupMode.EXISTING_DB);
        startupProperties.setInitStateFile(null);
        startupProperties.setValidateExternalSystems(false);

        dbResetService.resetDatabase();

        Mockito.reset(externalSystemsAvailabilityPort);
        Mockito.when(externalSystemsAvailabilityPort.areExternalSystemsAvailable()).thenReturn(true);
    }

    @Test
    void existingDbMode_preservesExistingPersistedData() {
        seedOldUser();

        startupProperties.setMode(StartupMode.EXISTING_DB);
        startupProperties.setInitStateFile("classpath:init/valid-state.txt");

        runStartupFlow();

        assertThat(memberRepository.findByUsername("olduser"))
                .as("EXISTING_DB must not wipe existing members")
                .isPresent();

        assertThat(memberRepository.findByUsername("testadmin"))
                .as("admin/platform baseline should still be created when missing")
                .isPresent();

        assertThat(platformRepository.findInstance())
                .as("platform should be bootstrapped")
                .isPresent();

        assertThat(memberRepository.findByUsername("rina"))
                .as("EXISTING_DB must not replay init file")
                .isEmpty();
    }

    @Test
    void emptyDbMode_clearsExistingDataAndLeavesAdminPlatformBaseline() {
        seedOldUser();

        startupProperties.setMode(StartupMode.EMPTY_DB);
        startupProperties.setInitStateFile("classpath:init/valid-state.txt");

        runStartupFlow();

        assertThat(memberRepository.findByUsername("olduser"))
                .as("EMPTY_DB must wipe old persisted members")
                .isEmpty();

        assertThat(companyRepository.findByName("Acme Live"))
                .as("EMPTY_DB must not replay init file")
                .isEmpty();

        assertThat(memberRepository.findByUsername("testadmin"))
                .as("EMPTY_DB should recreate required admin baseline")
                .isPresent();

        assertThat(platformRepository.findInstance())
                .as("EMPTY_DB should recreate required platform baseline")
                .isPresent();
    }

    @Test
    void initFileMode_clearsExistingDataBootstrapsBaselineAndReplaysInitFile() {
        seedOldUser();

        startupProperties.setMode(StartupMode.INIT_FILE);
        startupProperties.setInitStateFile("classpath:init/valid-state.txt");

        runStartupFlow();

        assertThat(memberRepository.findByUsername("olduser"))
                .as("INIT_FILE must wipe old persisted data before replay")
                .isEmpty();

        assertThat(memberRepository.findByUsername("testadmin"))
                .as("required admin baseline should exist before/after replay")
                .isPresent();

        assertThat(platformRepository.findInstance())
                .as("required platform baseline should exist before/after replay")
                .isPresent();

        assertThat(memberRepository.findByUsername("rina"))
                .as("valid init file should be replayed")
                .isPresent();

        assertThat(memberRepository.findByUsername("moshe"))
                .as("valid init file should be replayed")
                .isPresent();

        assertThat(companyRepository.findByName("Acme Live"))
                .as("valid init file should create company")
                .isPresent();
    }

    @Test
    void initFileMode_whenInitContentInvalid_logsContinuesAndLeavesOnlyBaseline() {
        seedOldUser();

        startupProperties.setMode(StartupMode.INIT_FILE);
        startupProperties.setInitStateFile("classpath:init/invalid-state.txt");

        runStartupFlow();

        assertThat(memberRepository.findByUsername("olduser"))
                .as("INIT_FILE must wipe old data before attempting replay")
                .isEmpty();

        assertThat(memberRepository.findByUsername("dana"))
                .as("invalid init file should be rolled back")
                .isEmpty();

        assertThat(companyRepository.findByName("Acme"))
                .as("invalid init file should not partially create company")
                .isEmpty();

        assertThat(memberRepository.findByUsername("testadmin"))
                .as("server continues with required admin baseline")
                .isPresent();

        assertThat(platformRepository.findInstance())
                .as("server continues with required platform baseline")
                .isPresent();
    }

    @Test
    void startupModeIntegrationTests_doNotCallRealExternalSystems() {
        startupProperties.setMode(StartupMode.EXISTING_DB);

        runStartupFlow();

        verify(externalSystemsAvailabilityPort, never()).areExternalSystemsAvailable();
    }

    private void runStartupFlow() {
        dbResetRunner.run();

        new AdminBootstrap(
                platformRepository,
                memberRepository,
                passwordHasher,
                bootstrapProperties
        ).run();

        initFileRunner.run();
    }

    private void seedOldUser() {
        List<InitCommand> commands = parser.parse("""
                register(olduser, password123, Old, User, olduser@example.com, 1990-01-01)
                """);

        initFileProcessor.process(commands);

        assertThat(memberRepository.findByUsername("olduser"))
                .as("test setup failed: olduser should exist before startup flow")
                .isPresent();
    }

}