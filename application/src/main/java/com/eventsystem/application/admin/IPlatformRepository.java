package com.eventsystem.application.admin;

import java.util.Optional;

import com.eventsystem.domain.platform.Platform;

/**
 * Repository interface (port) for the singleton {@link Platform} aggregate.
 * Implementations live in the infrastructure layer.
 */
public interface IPlatformRepository {

    Optional<Platform> findInstance();

    void save(Platform platform);
}
