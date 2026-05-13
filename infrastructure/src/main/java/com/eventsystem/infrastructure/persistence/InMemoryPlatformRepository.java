package com.eventsystem.infrastructure.persistence;

import com.eventsystem.domain.platform.Platform;
import com.eventsystem.domain.platform.PlatformRepository;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory adapter for the singleton {@link PlatformRepository}.
 *
 * Holds a single {@link Platform} instance behind an {@link AtomicReference}
 * so that reads and writes are visible across threads without a lock.
 */
public class InMemoryPlatformRepository implements PlatformRepository {

    private final AtomicReference<Platform> instance = new AtomicReference<>();

    @Override
    public Optional<Platform> findInstance() {
        return Optional.ofNullable(instance.get());
    }

    @Override
    public void save(Platform platform) {
        Objects.requireNonNull(platform, "platform must not be null");
        instance.set(platform);
    }

    /** Test-support: drop the stored instance. */
    public void clear() {
        instance.set(null);
    }
}
