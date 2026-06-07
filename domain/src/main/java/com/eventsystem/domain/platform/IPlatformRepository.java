package com.eventsystem.domain.platform;

import java.util.Optional;

/**
 * Repository interface (port) for the singleton {@link Platform} aggregate.
 * Implementations live in the infrastructure layer.
 */
public interface IPlatformRepository {

    Optional<Platform> findInstance();

    void save(Platform platform);
}
