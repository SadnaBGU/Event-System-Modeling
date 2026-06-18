package com.eventsystem.infrastructure.testsupport;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * JUnit 5 execution condition that disables a DB-backed test class when the
 * PostgreSQL test database (docker-compose service on {@code 127.0.0.1:5434}) is
 * not reachable.
 *
 * <p>It is evaluated <b>before</b> the Spring context loads, so when the DB is
 * down the test is <em>skipped</em> rather than erroring — keeping {@code mvn test}
 * green on machines without the test database (team task 2.2: migrate tests to a
 * real DB without breaking the build). Start the DB with
 * {@code docker compose up -d} to actually run these tests.</p>
 */
public class PostgresAvailableCondition implements ExecutionCondition {

    private static final String HOST = "127.0.0.1";
    private static final int PORT = 5434;
    private static final int TIMEOUT_MS = 1000;

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(HOST, PORT), TIMEOUT_MS);
            return ConditionEvaluationResult.enabled(
                    "PostgreSQL test database reachable on " + HOST + ":" + PORT);
        } catch (IOException e) {
            return ConditionEvaluationResult.disabled(
                    "PostgreSQL test database not reachable on " + HOST + ":" + PORT
                            + " — skipping DB integration test (run 'docker compose up -d').");
        }
    }
}
