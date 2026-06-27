package com.eventsystem.infrastructure.config.startup;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class StartupDatabaseResetRunnerTest {

    @Test
    void run_existingDbMode_doesNotResetDatabase() {
        StartupProperties props = new StartupProperties();
        props.setMode(StartupMode.EXISTING_DB);

        DBResetService resetService = mock(DBResetService.class);

        StartupDBResetRunner runner = new StartupDBResetRunner(props, resetService);

        assertThatCode(() -> runner.run())
                .doesNotThrowAnyException();

        verify(resetService, never()).resetDatabase();
    }

    @Test
    void run_emptyDbMode_resetsDatabase() {
        StartupProperties props = new StartupProperties();
        props.setMode(StartupMode.EMPTY_DB);

        DBResetService resetService = mock(DBResetService.class);

        StartupDBResetRunner runner = new StartupDBResetRunner(props, resetService);

        assertThatCode(() -> runner.run())
                .doesNotThrowAnyException();

        verify(resetService).resetDatabase();
    }

    @Test
    void run_initFileMode_resetsDatabase() {
        StartupProperties props = new StartupProperties();
        props.setMode(StartupMode.INIT_FILE);
        props.setInitStateFile("classpath:init/valid-state.txt");

        DBResetService resetService = mock(DBResetService.class);

        StartupDBResetRunner runner = new StartupDBResetRunner(props, resetService);

        assertThatCode(() -> runner.run())
                .doesNotThrowAnyException();

        verify(resetService).resetDatabase();
    }
}