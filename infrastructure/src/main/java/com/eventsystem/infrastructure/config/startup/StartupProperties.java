package com.eventsystem.infrastructure.config.startup;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "eventsystem.startup")
public class StartupProperties {

    private StartupMode mode = StartupMode.EXISTING_DB;

    /**
     * Optional unless mode is INIT_FILE.
     *
     * Can be a normal filesystem path, classpath:..., or file:...
     */
    private String initStateFile;

    /**
     * Real application startup should validate external systems.
     * Tests can disable this to avoid calling real/local WSEP services during context startup.
     */
    private boolean validateExternalSystems = true;

    public StartupMode getMode() {
        return mode;
    }

    public void setMode(StartupMode mode) {
        this.mode = mode;
    }

    public String getInitStateFile() {
        return initStateFile;
    }

    public void setInitStateFile(String initStateFile) {
        this.initStateFile = initStateFile;
    }

    public boolean isValidateExternalSystems() {
        return validateExternalSystems;
    }

    public void setValidateExternalSystems(boolean validateExternalSystems) {
        this.validateExternalSystems = validateExternalSystems;
    }

    public void validateOrThrow() {
        if (mode == null) {
            throw new StartupConfigException(
                    "STARTUP_CONFIG_ERROR: eventsystem.startup.mode is missing. " +
                    "Allowed values: EMPTY_DB, EXISTING_DB, INIT_FILE."
            );
        }

        if (mode == StartupMode.INIT_FILE && isBlank(initStateFile)) {
            throw new StartupConfigException(
                    "STARTUP_CONFIG_ERROR: eventsystem.startup.mode=INIT_FILE requires " +
                    "eventsystem.startup.init-state-file to be configured."
            );
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}