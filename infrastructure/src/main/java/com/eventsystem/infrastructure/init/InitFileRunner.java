package com.eventsystem.infrastructure.init;

import com.eventsystem.infrastructure.config.startup.StartupConfigException;
import com.eventsystem.infrastructure.config.startup.StartupMode;
import com.eventsystem.infrastructure.config.startup.StartupProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@Order(100)
public class InitFileRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(InitFileRunner.class);

    private final InitFileProcessor processor;
    private final ResourceLoader resourceLoader;
    private final StartupProperties startupProperties;
    private final StateFileParser parser;

    @Autowired
    public InitFileRunner(
            InitFileProcessor processor,
            ResourceLoader resourceLoader,
            StartupProperties startupProperties) {
        this(processor, resourceLoader, startupProperties, new StateFileParser());
    }

    InitFileRunner(
            InitFileProcessor processor,
            ResourceLoader resourceLoader,
            StartupProperties startupProperties,
            StateFileParser parser) {
        this.processor = processor;
        this.resourceLoader = resourceLoader;
        this.startupProperties = startupProperties;
        this.parser = parser;
    }

    @Override
    public void run(String... args) {
        if (startupProperties.getMode() != StartupMode.INIT_FILE) {
            log.info("INIT_FILE_SKIPPED: startup mode={}", startupProperties.getMode());
            return;
        }

        String stateFilePath = startupProperties.getInitStateFile();

        try {
            String content = readFileOrThrowConfigError(stateFilePath);
            List<InitCommand> commands = parser.parse(content);

            log.info(
                    "INIT_FILE_START: loaded {} command(s) from {}",
                    commands.size(),
                    stateFilePath
            );

            processor.process(commands);

            log.info(
                    "INIT_FILE_SUCCESS: initialization from {} completed successfully",
                    stateFilePath
            );
        } catch (StartupConfigException e) {
            log.error(e.getMessage(), e);
            throw e;
        } catch (InitFileException e) {
            log.error(
                    "INIT_FILE_FAILED: file={}, line={}, reason={}. Startup continues; init-file transaction was rolled back.",
                    stateFilePath,
                    e.lineNumber(),
                    e.getMessage()
            );
        } catch (RuntimeException e) {
            log.error(
                    "INIT_FILE_FAILED: file={}, reason={}. Startup continues; init-file transaction was rolled back.",
                    stateFilePath,
                    e.getMessage(),
                    e
            );
        }
    }

    private String readFileOrThrowConfigError(String path) {
        if (path == null || path.isBlank()) {
            throw new StartupConfigException(
                    "STARTUP_CONFIG_ERROR: startup mode INIT_FILE requires " +
                    "eventsystem.startup.init-state-file to be configured."
            );
        }

        String location = path.contains(":") ? path : "file:" + path;
        Resource resource = resourceLoader.getResource(location);

        if (!resource.exists()) {
            throw new StartupConfigException(
                    "STARTUP_CONFIG_ERROR: configured init-state file was not found: " + path
            );
        }

        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new StartupConfigException(
                    "STARTUP_CONFIG_ERROR: failed to read configured init-state file: " + path,
                    e
            );
        }
    }
}