package com.eventsystem.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class AppConfigurationFailureTest {

    @Configuration
    @EnableConfigurationProperties(BootstrapProperties.class)
    static class TestConfig {}

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class));

    @Test
    void serverCrashesWithInformativeMessage_whenAdminConfigurationIsMissing() {
        contextRunner
                .withPropertyValues("eventsystem.bootstrap.admin.username=")
                .withPropertyValues("eventsystem.bootstrap.admin.password=") 
                .run(context -> {
                    
                    assertThat(context).hasFailed();

                    Throwable failure = context.getStartupFailure();
                    assertThat(failure).isNotNull();

                    assertThat(failure.getMessage()).containsAnyOf(
                            "Could not bind properties to 'BootstrapProperties'",
                            "eventsystem.bootstrap"
                    );
                });
    }
}