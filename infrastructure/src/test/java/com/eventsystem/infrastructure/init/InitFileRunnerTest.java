package com.eventsystem.infrastructure.init;

import com.eventsystem.infrastructure.config.startup.StartupConfigException;
import com.eventsystem.infrastructure.config.startup.StartupMode;
import com.eventsystem.infrastructure.config.startup.StartupProperties;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class InitFileRunnerTest {

    @Test
    void run_existingDbMode_skipsInitFileProcessing() {
        InitFileProcessor processor = mock(InitFileProcessor.class);
        ResourceLoader resourceLoader = mock(ResourceLoader.class);

        StartupProperties startupProperties = new StartupProperties();
        startupProperties.setMode(StartupMode.EXISTING_DB);
        startupProperties.setInitStateFile("classpath:init/valid-state.txt");

        InitFileRunner runner = new InitFileRunner(
                processor,
                resourceLoader,
                startupProperties);

        assertThatCode(() -> runner.run())
                .doesNotThrowAnyException();

        verify(processor, never()).process(org.mockito.ArgumentMatchers.anyList());
        verify(resourceLoader, never()).getResource(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void run_emptyDbMode_skipsInitFileProcessing() {
        InitFileProcessor processor = mock(InitFileProcessor.class);
        ResourceLoader resourceLoader = mock(ResourceLoader.class);

        StartupProperties startupProperties = new StartupProperties();
        startupProperties.setMode(StartupMode.EMPTY_DB);
        startupProperties.setInitStateFile("classpath:init/valid-state.txt");

        InitFileRunner runner = new InitFileRunner(
                processor,
                resourceLoader,
                startupProperties);

        assertThatCode(() -> runner.run())
                .doesNotThrowAnyException();

        verify(processor, never()).process(org.mockito.ArgumentMatchers.anyList());
        verify(resourceLoader, never()).getResource(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void run_initFileModeWithMissingPath_failsAsStartupConfigError() {
        InitFileProcessor processor = mock(InitFileProcessor.class);
        ResourceLoader resourceLoader = mock(ResourceLoader.class);

        StartupProperties startupProperties = new StartupProperties();
        startupProperties.setMode(StartupMode.INIT_FILE);
        startupProperties.setInitStateFile("");

        InitFileRunner runner = new InitFileRunner(
                processor,
                resourceLoader,
                startupProperties);

        assertThatThrownBy(() -> runner.run())
                .isInstanceOf(StartupConfigException.class)
                .hasMessageContaining("STARTUP_CONFIG_ERROR")
                .hasMessageContaining("init-state-file");

        verify(processor, never()).process(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void run_initFileModeWithUnreadablePath_failsAsStartupConfigError() {
        InitFileProcessor processor = mock(InitFileProcessor.class);

        ResourceLoader resourceLoader = resourceLoaderWithMissingResource();

        StartupProperties startupProperties = new StartupProperties();
        startupProperties.setMode(StartupMode.INIT_FILE);
        startupProperties.setInitStateFile("classpath:init/missing.txt");

        InitFileRunner runner = new InitFileRunner(
                processor,
                resourceLoader,
                startupProperties);

        assertThatThrownBy(() -> runner.run())
                .isInstanceOf(StartupConfigException.class)
                .hasMessageContaining("STARTUP_CONFIG_ERROR")
                .hasMessageContaining("not found");

        verify(processor, never()).process(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void run_initFileModeWithMalformedContent_logsAndContinues() {
        InitFileProcessor processor = mock(InitFileProcessor.class);

        ResourceLoader resourceLoader = resourceLoaderWithContent("broken line");

        StartupProperties startupProperties = new StartupProperties();
        startupProperties.setMode(StartupMode.INIT_FILE);
        startupProperties.setInitStateFile("classpath:init/bad.txt");

        InitFileRunner runner = new InitFileRunner(
                processor,
                resourceLoader,
                startupProperties);

        assertThatCode(() -> runner.run())
                .doesNotThrowAnyException();

        verify(processor, never()).process(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void run_initFileModeWithProcessorFailure_logsAndContinues() {
        InitFileProcessor processor = mock(InitFileProcessor.class);

        org.mockito.Mockito.doThrow(new InitFileException(1, "unknown command 'bad-command'"))
                .when(processor)
                .process(org.mockito.ArgumentMatchers.anyList());

        ResourceLoader resourceLoader = resourceLoaderWithContent("bad-command()");

        StartupProperties startupProperties = new StartupProperties();
        startupProperties.setMode(StartupMode.INIT_FILE);
        startupProperties.setInitStateFile("classpath:init/bad-command.txt");

        InitFileRunner runner = new InitFileRunner(
                processor,
                resourceLoader,
                startupProperties);

        assertThatCode(() -> runner.run())
                .doesNotThrowAnyException();

        verify(processor).process(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void run_initFileModeWithValidContent_processesCommands() {
        InitFileProcessor processor = mock(InitFileProcessor.class);

        ResourceLoader resourceLoader = resourceLoaderWithContent("login(admin, secret)");

        StartupProperties startupProperties = new StartupProperties();
        startupProperties.setMode(StartupMode.INIT_FILE);
        startupProperties.setInitStateFile("classpath:init/valid.txt");

        InitFileRunner runner = new InitFileRunner(
                processor,
                resourceLoader,
                startupProperties);

        assertThatCode(() -> runner.run())
                .doesNotThrowAnyException();

        verify(processor).process(org.mockito.ArgumentMatchers.argThat(commands -> commands.size() == 1
                && commands.get(0).name().equals("login")
                && commands.get(0).args().size() == 2));
    }

    private static ResourceLoader resourceLoaderReturning(Resource resource) {
        return new ResourceLoader() {
            @Override
            public Resource getResource(String location) {
                return resource;
            }

            @Override
            public ClassLoader getClassLoader() {
                return Thread.currentThread().getContextClassLoader();
            }
        };
    }

    private static ResourceLoader resourceLoaderWithContent(String content) {
        return resourceLoaderReturning(resourceWithContent(content));
    }

    private static ResourceLoader resourceLoaderWithMissingResource() {
        return resourceLoaderReturning(new ByteArrayResource(new byte[0]) {
            @Override
            public boolean exists() {
                return false;
            }
        });
    }

    private static Resource resourceWithContent(String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public boolean exists() {
                return true;
            }
        };
    }

}