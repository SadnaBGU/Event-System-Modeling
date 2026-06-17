package com.eventsystem.infrastructure.persistence.springrepos;

import com.eventsystem.domain.platform.Platform;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataPlatformRepository extends JpaRepository<Platform, Long> {
    // singeleton
}
