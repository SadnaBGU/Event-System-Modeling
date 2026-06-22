package com.eventsystem.infrastructure.init;

import com.eventsystem.domain.company.IProductionCompanyRepository;
import com.eventsystem.domain.member.IMemberRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.extension.ExtendWith;
import com.eventsystem.infrastructure.testsupport.PostgresAvailableCondition;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for the initial-state loader running against a real
 * PostgreSQL database (docker-compose service on {@code localhost:5434}). H2
 * cannot host the {@code jsonb}/{@code enum} columns and reserved-word ({@code
 * value}) column names used by the domain entities, so DB-backed tests use
 * Postgres like the rest of the suite. Verifies that a valid file is replayed
 * through the application layer and persisted, and that a single failing command
 * rolls the whole file back (all-or-nothing, team task 2.1) and aborts startup.
 *
 * <p>Skipped automatically when the test database is unreachable, so the build
 * stays green without it.</p>
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
        "spring.datasource.hikari.idle-timeout=10000"
})
class InitFileProcessorIntegrationTest {

    @Autowired
    private InitFileProcessor processor;

    @Autowired
    private IMemberRepository memberRepository;

    @Autowired
    private IProductionCompanyRepository companyRepository;

    private final StateFileParser parser = new StateFileParser();

    @Test
    void validScript_isReplayedAndPersisted() {
        List<InitCommand> commands = parser.parse(read("/init/valid-state.txt"));

        processor.process(commands);

        assertThat(memberRepository.findByUsername("rina")).isPresent();
        assertThat(memberRepository.findByUsername("moshe")).isPresent();
        assertThat(companyRepository.findByName("Acme Live")).isPresent();
    }

    @Test
    void invalidScript_rollsBackEveryPrecedingCommand() {
        List<InitCommand> commands = parser.parse(read("/init/invalid-state.txt"));

        assertThatThrownBy(() -> processor.process(commands))
                .isInstanceOf(InitFileException.class)
                .hasMessageContaining("not logged in");

        // 'dana' was registered on line 1 but the file failed on line 2 → rollback.
        assertThat(memberRepository.findByUsername("dana")).isEmpty();
    }

    @Test
    void runner_failsStartupWhenStateFileIsInvalid() {
        InitFileRunner runner = new InitFileRunner(processor, new DefaultResourceLoader());
        ReflectionTestUtils.setField(runner, "stateFilePath", "classpath:init/invalid-state.txt");

        assertThatThrownBy(() -> runner.run())
                .isInstanceOf(InitFileException.class);

        assertThat(memberRepository.findByUsername("dana")).isEmpty();
    }

    @Test
    void runner_failsWhenStateFileMissing() {
        InitFileRunner runner = new InitFileRunner(processor, new DefaultResourceLoader());
        ReflectionTestUtils.setField(runner, "stateFilePath", "classpath:init/does-not-exist.txt");

        assertThatThrownBy(() -> runner.run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not found");
    }

    private String read(String classpathResource) {
        try (InputStream in = getClass().getResourceAsStream(classpathResource)) {
            if (in == null) {
                throw new IllegalStateException("missing test resource: " + classpathResource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
