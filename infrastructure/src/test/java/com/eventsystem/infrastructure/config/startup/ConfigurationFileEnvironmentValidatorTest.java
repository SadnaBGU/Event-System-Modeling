package com.eventsystem.infrastructure.config.startup;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigurationFileEnvironmentValidatorTest {

    private final ConfigurationFileEnvironmentValidator validator = new ConfigurationFileEnvironmentValidator();

    @Test
    void acceptsSupportedApplicationConfigurationKeysFromConfigFile() {
        MockEnvironment environment = new MockEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource(
                "Config resource 'class path resource [application-main.yml]' via location 'optional:classpath:/'",
                Map.of(
                        "eventsystem.startup.mode", "EXISTING_DB",
                        "eventsystem.bootstrap.admin.username", "admin",
                        "wsep.base-url", "https://example.test"
                )));

        assertThat(validator.findUnknownApplicationKeys(environment)).isEmpty();
    }

    @Test
    void rejectsUnsupportedApplicationConfigurationKeysFromConfigFile() {
        MockEnvironment environment = new MockEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource(
                "Config resource 'class path resource [application-main.yml]' via location 'optional:classpath:/'",
                Map.of(
                        "eventsystem.startup.mdoe", "INIT_FILE",
                        "wsep.base-uri", "https://example.test"
                )));

        assertThatThrownBy(() -> validator.postProcessEnvironment(environment, new SpringApplication()))
                .isInstanceOf(StartupConfigException.class)
                .hasMessageContaining("STARTUP_CONFIG_ERROR")
                .hasMessageContaining("eventsystem.startup.mdoe")
                .hasMessageContaining("wsep.base-uri");
    }

    @Test
    void ignoresFrameworkAndNonConfigFileKeys() {
        MockEnvironment environment = new MockEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource(
                "Config resource 'class path resource [application-main.yml]' via location 'optional:classpath:/'",
                Map.of(
                        "spring.application.name", "event-system",
                        "logging.level.root", "INFO"
                )));
        environment.getPropertySources().addFirst(new MapPropertySource(
                "commandLineArgs",
                Map.of("eventsystem.not-a-file-key", "ignored")
        ));

        assertThat(validator.findUnknownApplicationKeys(environment)).isEmpty();
    }
}
