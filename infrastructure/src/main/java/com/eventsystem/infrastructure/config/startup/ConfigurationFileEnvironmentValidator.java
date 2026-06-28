package com.eventsystem.infrastructure.config.startup;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;

/**
 * Fails startup early when application configuration contains unsupported keys.
 */
public class ConfigurationFileEnvironmentValidator implements EnvironmentPostProcessor, Ordered {

    private static final List<String> CONFIG_FILE_SOURCE_PREFIXES = List.of(
            "Config resource ",
            "applicationConfig:"
    );

    private static final List<String> ALLOWED_KEYS = List.of(
            "eventsystem.security.jwt-secret",
            "eventsystem.security.token-validity",
            "eventsystem.security.bcrypt-strength",
            "eventsystem.recovery.enabled",
            "eventsystem.recovery.initial-delay-ms",
            "eventsystem.recovery.sweep-interval-ms",
            "eventsystem.bootstrap.enabled",
            "eventsystem.bootstrap.default-reservation-timeout",
            "eventsystem.bootstrap.queue-load-threshold",
            "eventsystem.bootstrap.admin.username",
            "eventsystem.bootstrap.admin.password",
            "eventsystem.bootstrap.admin.first-name",
            "eventsystem.bootstrap.admin.last-name",
            "eventsystem.bootstrap.admin.email",
            "eventsystem.bootstrap.admin.date-of-birth",
            "eventsystem.startup.mode",
            "eventsystem.startup.init-state-file",
            "eventsystem.startup.validate-external-systems",
            "wsep.base-url",
            "wsep.connect-timeout",
            "wsep.read-timeout"
    );

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Set<String> unknownKeys = findUnknownApplicationKeys(environment);

        if (!unknownKeys.isEmpty()) {
            throw new StartupConfigException(
                    "STARTUP_CONFIG_ERROR: unsupported configuration key(s): " +
                    String.join(", ", unknownKeys) +
                    ". Check the YAML file for typos or remove keys that are not part of the supported system configuration."
            );
        }
    }

    Set<String> findUnknownApplicationKeys(ConfigurableEnvironment environment) {
        Set<String> unknownKeys = new TreeSet<>();

        for (PropertySource<?> propertySource : environment.getPropertySources()) {
            if (!isConfigFilePropertySource(propertySource) || !(propertySource instanceof EnumerablePropertySource<?> enumerable)) {
                continue;
            }

            for (String propertyName : enumerable.getPropertyNames()) {
                if (isApplicationOwnedKey(propertyName) && !ALLOWED_KEYS.contains(propertyName)) {
                    unknownKeys.add(propertyName);
                }
            }
        }

        return unknownKeys;
    }

    private boolean isApplicationOwnedKey(String propertyName) {
        return propertyName.startsWith("eventsystem.") || propertyName.startsWith("wsep.");
    }

    private boolean isConfigFilePropertySource(PropertySource<?> propertySource) {
        String sourceName = propertySource.getName();
        return CONFIG_FILE_SOURCE_PREFIXES.stream().anyMatch(sourceName::startsWith);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
