package com.eventsystem.infrastructure.config.startup;

import com.eventsystem.application.system.IExternalSystemsAvailabilityPort;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StartupExternalSystemsValidatorTest {

    @Test
    void run_whenValidationDisabled_skipsExternalSystemsCheck() {
        IExternalSystemsAvailabilityPort availability = mock(IExternalSystemsAvailabilityPort.class);

        StartupProperties props = new StartupProperties();
        props.setValidateExternalSystems(false);

        StartupExternalSystemsValidator validator =
                new StartupExternalSystemsValidator(availability, props);

        assertThatCode(() -> validator.run())
                .doesNotThrowAnyException();

        verify(availability, never()).areExternalSystemsAvailable();
    }

    @Test
    void run_whenExternalSystemsAvailable_passes() {
        IExternalSystemsAvailabilityPort availability = mock(IExternalSystemsAvailabilityPort.class);
        when(availability.areExternalSystemsAvailable()).thenReturn(true);

        StartupProperties props = new StartupProperties();
        props.setValidateExternalSystems(true);

        StartupExternalSystemsValidator validator =
                new StartupExternalSystemsValidator(availability, props);

        assertThatCode(() -> validator.run())
                .doesNotThrowAnyException();

        verify(availability).areExternalSystemsAvailable();
    }

    @Test
    void run_whenExternalSystemsUnavailable_failsWithClearStartupMessage() {
        IExternalSystemsAvailabilityPort availability = mock(IExternalSystemsAvailabilityPort.class);
        when(availability.areExternalSystemsAvailable()).thenReturn(false);

        StartupProperties props = new StartupProperties();
        props.setValidateExternalSystems(true);

        StartupExternalSystemsValidator validator =
                new StartupExternalSystemsValidator(availability, props);

        assertThatThrownBy(() -> validator.run())
                .isInstanceOf(StartupConfigException.class)
                .hasMessageContaining("STARTUP_EXTERNAL_SYSTEMS_ERROR")
                .hasMessageContaining("external payment/ticketing services are unavailable")
                .hasMessageContaining("startup halted");

        verify(availability).areExternalSystemsAvailable();
    }

    @Test
    void run_whenExternalSystemsCheckThrows_failsWithClearStartupMessage() {
        IExternalSystemsAvailabilityPort availability = mock(IExternalSystemsAvailabilityPort.class);
        when(availability.areExternalSystemsAvailable())
                .thenThrow(new RuntimeException("handshake timeout"));

        StartupProperties props = new StartupProperties();
        props.setValidateExternalSystems(true);

        StartupExternalSystemsValidator validator =
                new StartupExternalSystemsValidator(availability, props);

        assertThatThrownBy(() -> validator.run())
                .isInstanceOf(StartupConfigException.class)
                .hasMessageContaining("STARTUP_EXTERNAL_SYSTEMS_ERROR")
                .hasMessageContaining("failed to validate external payment/ticketing services")
                .hasRootCauseMessage("handshake timeout");

        verify(availability).areExternalSystemsAvailable();
    }
}