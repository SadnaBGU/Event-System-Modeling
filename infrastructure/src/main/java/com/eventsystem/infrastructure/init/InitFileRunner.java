package com.eventsystem.infrastructure.init;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Reads the path of the initial-state file from configuration and replays it
 * through {@link InitFileProcessor} once, at application startup.
 *
 * <p>Activated only when {@code eventsystem.init.enabled=true}; the file path is
 * taken from {@code eventsystem.init.state-file} (a filesystem path by default,
 * or a {@code classpath:}/{@code file:} prefixed resource). Neither value is
 * hard-coded in the system (V3 §2.a.i).</p>
 *
 * <p>{@link Order} 100 places this after the admin/platform bootstrap so the
 * platform already exists when the script runs. Any failure propagates and
 * aborts startup.</p>
 */
@Component
@ConditionalOnProperty(name = "eventsystem.init.enabled", havingValue = "true")
@Order(100)
public class InitFileRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(InitFileRunner.class);

    private final InitFileProcessor processor;
    private final ResourceLoader resourceLoader;
    private final StateFileParser parser = new StateFileParser();

    @Value("${eventsystem.init.state-file:}")
    private String stateFilePath;

    public InitFileRunner(InitFileProcessor processor, ResourceLoader resourceLoader) {
        this.processor = processor;
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void run(String... args) {
        if (stateFilePath == null || stateFilePath.isBlank()) {
            throw new IllegalStateException(
                    "eventsystem.init.enabled=true but eventsystem.init.state-file is not set");
        }

        String content = readFile(stateFilePath);
        List<InitCommand> commands = parser.parse(content);
        log.info("Init-state: loaded {} command(s) from {}", commands.size(), stateFilePath);

        // Single transaction inside the processor — all-or-nothing.
        processor.process(commands);
        log.info("Init-state: initialization from {} completed", stateFilePath);
    }

    private String readFile(String path) {
        String location = (path.contains(":")) ? path : "file:" + path;
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException("Init-state file not found: " + path);
        }
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read init-state file: " + path, e);
        }
    }
}
