package com.eventsystem.infrastructure.persistence.springrepos;

import com.eventsystem.domain.platform.IPlatformRepository;
import com.eventsystem.domain.platform.Platform;
import java.util.Optional;
import java.util.Objects;

public class PostgresPlatformRepository implements IPlatformRepository {

    private final SpringDataPlatformRepository jpaRepository;

    public PostgresPlatformRepository(SpringDataPlatformRepository jpaRepository) {
        this.jpaRepository = Objects.requireNonNull(jpaRepository);
    }

    @Override
    public Optional<Platform> findInstance() {
        // שולפים את המופע היחיד (לפי המזהה שנקבע בבנייה)
        return jpaRepository.findById(1L);
    }

    @SuppressWarnings("null")
    @Override
    public void save(Platform platform) {
        jpaRepository.save(platform);
    }
}
