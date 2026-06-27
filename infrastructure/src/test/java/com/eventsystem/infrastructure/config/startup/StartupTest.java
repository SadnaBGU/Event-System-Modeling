package com.eventsystem.infrastructure.config.startup;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * REQ: PERS-06, TST-18, UC1
 *
 * Startup configuration must be validated before bootstrap/init logic runs.
 * Invalid configuration is fatal and should fail startup with a clear message.
 */
class StartupTest {

    @AfterEach
    void tearDown() {
        detachListAppenders(StartupConfigValidator.class);
    }

    // ---------------------------------------------------------------------
    // StartupProperties validation tests
    // ---------------------------------------------------------------------

    @Test
    void validate_existingDbWithoutInitFile_passes() {
        StartupProperties props = new StartupProperties();
        props.setMode(StartupMode.EXISTING_DB);
        props.setInitStateFile("");

        assertThatCode(props::validateOrThrow)
                .doesNotThrowAnyException();
    }

    @Test
    void validate_emptyDbWithoutInitFile_passes() {
        StartupProperties props = new StartupProperties();
        props.setMode(StartupMode.EMPTY_DB);
        props.setInitStateFile(null);

        assertThatCode(props::validateOrThrow)
                .doesNotThrowAnyException();
    }

    @Test
    void validate_initFileWithRegularPath_passes() {
        StartupProperties props = new StartupProperties();
        props.setMode(StartupMode.INIT_FILE);
        props.setInitStateFile("config/initial-state.sample.txt");

        assertThatCode(props::validateOrThrow)
                .doesNotThrowAnyException();
    }

    @Test
    void validate_initFileWithClasspathPath_passes() {
        StartupProperties props = new StartupProperties();
        props.setMode(StartupMode.INIT_FILE);
        props.setInitStateFile("classpath:initial-state.sample.txt");

        assertThatCode(props::validateOrThrow)
                .doesNotThrowAnyException();
    }

    @Test
    void validate_initFileWithFilePath_passes() {
        StartupProperties props = new StartupProperties();
        props.setMode(StartupMode.INIT_FILE);
        props.setInitStateFile("file:config/initial-state.sample.txt");

        assertThatCode(props::validateOrThrow)
                .doesNotThrowAnyException();
    }

    @Test
    void validate_initFileWithNullPath_failsWithClearMessage() {
        StartupProperties props = new StartupProperties();
        props.setMode(StartupMode.INIT_FILE);
        props.setInitStateFile(null);

        assertThatThrownBy(props::validateOrThrow)
                .isInstanceOf(StartupConfigException.class)
                .hasMessageContaining("STARTUP_CONFIG_ERROR")
                .hasMessageContaining("INIT_FILE")
                .hasMessageContaining("eventsystem.startup.init-state-file");
    }

    @Test
    void validate_initFileWithBlankPath_failsWithClearMessage() {
        StartupProperties props = new StartupProperties();
        props.setMode(StartupMode.INIT_FILE);
        props.setInitStateFile("   ");

        assertThatThrownBy(props::validateOrThrow)
                .isInstanceOf(StartupConfigException.class)
                .hasMessageContaining("STARTUP_CONFIG_ERROR")
                .hasMessageContaining("INIT_FILE")
                .hasMessageContaining("eventsystem.startup.init-state-file");
    }

    @Test
    void validate_missingMode_failsWithClearMessage() {
        StartupProperties props = new StartupProperties();
        props.setMode(null);
        props.setInitStateFile("config/initial-state.sample.txt");

        assertThatThrownBy(props::validateOrThrow)
                .isInstanceOf(StartupConfigException.class)
                .hasMessageContaining("STARTUP_CONFIG_ERROR")
                .hasMessageContaining("eventsystem.startup.mode")
                .hasMessageContaining("EMPTY_DB")
                .hasMessageContaining("EXISTING_DB")
                .hasMessageContaining("INIT_FILE");
    }

    // ---------------------------------------------------------------------
    // Spring Binder tests
    // ---------------------------------------------------------------------

    @Test
    void bind_validExistingDbMode_bindsEnum() {
        StartupProperties props = bind(Map.of(
                "eventsystem.startup.mode", "EXISTING_DB"
        ));

        assertThat(props.getMode()).isEqualTo(StartupMode.EXISTING_DB);
        assertThat(props.getInitStateFile()).isNull();
    }

    @Test
    void bind_validEmptyDbMode_bindsEnum() {
        StartupProperties props = bind(Map.of(
                "eventsystem.startup.mode", "EMPTY_DB"
        ));

        assertThat(props.getMode()).isEqualTo(StartupMode.EMPTY_DB);
    }

    @Test
    void bind_validInitFileMode_bindsEnumAndPath() {
        StartupProperties props = bind(Map.of(
                "eventsystem.startup.mode", "INIT_FILE",
                "eventsystem.startup.init-state-file", "config/init.txt"
        ));

        assertThat(props.getMode()).isEqualTo(StartupMode.INIT_FILE);
        assertThat(props.getInitStateFile()).isEqualTo("config/init.txt");
    }

    @Test
    void bind_defaultModeIsExistingDbWhenModeNotProvided() {
        StartupProperties props = bind(Map.of());

        assertThat(props.getMode()).isEqualTo(StartupMode.EXISTING_DB);
        assertThatCode(props::validateOrThrow)
                .doesNotThrowAnyException();
    }

    @Test
    void bind_invalidStartupMode_failsAtBindingStage() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "eventsystem.startup.mode", "BAD_MODE"
        ));

        assertThatThrownBy(() -> new Binder(source)
                .bind("eventsystem.startup", StartupProperties.class)
                .orElseThrow(() -> new AssertionError("Should not bind invalid startup mode")))
                .isInstanceOf(BindException.class)
                .hasMessageContaining("BAD_MODE");
    }

    // ---------------------------------------------------------------------
    // StartupConfigValidator tests
    // ---------------------------------------------------------------------

    @Test
    void validator_validConfig_logsStartupConfigValid() {
        StartupProperties props = new StartupProperties();
        props.setMode(StartupMode.EXISTING_DB);

        StartupConfigValidator validator = new StartupConfigValidator(props);

        ListAppender<ILoggingEvent> logs = attachListAppender(StartupConfigValidator.class);

        assertThatCode(() -> validator.run())
                .doesNotThrowAnyException();

        assertThat(logs.list)
                .anySatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.INFO);
                    assertThat(event.getFormattedMessage()).contains("STARTUP_CONFIG_VALID");
                    assertThat(event.getFormattedMessage()).contains("EXISTING_DB");
                });
    }

    @Test
    void validator_invalidConfig_logsStartupConfigErrorAndThrows() {
        StartupProperties props = new StartupProperties();
        props.setMode(StartupMode.INIT_FILE);
        props.setInitStateFile("");

        StartupConfigValidator validator = new StartupConfigValidator(props);

        ListAppender<ILoggingEvent> logs = attachListAppender(StartupConfigValidator.class);

        assertThatThrownBy(() -> validator.run())
                .isInstanceOf(StartupConfigException.class)
                .hasMessageContaining("STARTUP_CONFIG_ERROR")
                .hasMessageContaining("INIT_FILE");

        assertThat(logs.list)
                .anySatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                    assertThat(event.getFormattedMessage()).contains("STARTUP_CONFIG_ERROR");
                    assertThat(event.getFormattedMessage()).contains("INIT_FILE");
                });
    }

    @Test
    void validator_initFileWithPath_logsPath() {
        StartupProperties props = new StartupProperties();
        props.setMode(StartupMode.INIT_FILE);
        props.setInitStateFile("config/init.txt");

        StartupConfigValidator validator = new StartupConfigValidator(props);

        ListAppender<ILoggingEvent> logs = attachListAppender(StartupConfigValidator.class);

        assertThatCode(() -> validator.run())
                .doesNotThrowAnyException();

        assertThat(logs.list)
                .anySatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.INFO);
                    assertThat(event.getFormattedMessage()).contains("STARTUP_CONFIG_VALID");
                    assertThat(event.getFormattedMessage()).contains("INIT_FILE");
                    assertThat(event.getFormattedMessage()).contains("config/init.txt");
                });
    }

    // ---------------------------------------------------------------------
    // Test helpers
    // ---------------------------------------------------------------------

    private static StartupProperties bind(Map<String, String> values) {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(values);

        return new Binder(source)
                .bind("eventsystem.startup", StartupProperties.class)
                .orElseGet(StartupProperties::new);
    }

    private static ListAppender<ILoggingEvent> attachListAppender(Class<?> loggerClass) {
        Logger logger = (Logger) LoggerFactory.getLogger(loggerClass);

        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();

        logger.addAppender(appender);
        return appender;
    }

    private static void detachListAppenders(Class<?> loggerClass) {
        Logger logger = (Logger) LoggerFactory.getLogger(loggerClass);

        logger.iteratorForAppenders().forEachRemaining(appender -> {
            if (appender instanceof ListAppender<?>) {
                logger.detachAppender(appender);
                appender.stop();
            }
        });
    }
}