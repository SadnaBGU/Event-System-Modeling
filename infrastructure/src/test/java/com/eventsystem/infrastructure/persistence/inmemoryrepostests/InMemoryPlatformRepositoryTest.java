package com.eventsystem.infrastructure.persistence.inmemoryrepostests;

import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.platform.Platform;
import com.eventsystem.infrastructure.persistence.inmemoryrepos.InMemoryPlatformRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryPlatformRepositoryTest {

    private InMemoryPlatformRepository repo;

    @BeforeEach
    void setUp() {
        repo = new InMemoryPlatformRepository();
    }

    @Test
    void findInstanceReturnsEmptyInitially() {
        assertThat(repo.findInstance()).isEmpty();
    }

    @Test
    void saveThenFindReturnsSameInstance() {
        Platform p = new Platform(MemberId.generate(), Duration.ofMinutes(15), 100);
        repo.save(p);
        assertThat(repo.findInstance()).contains(p);
    }

    @Test
    void saveOverwritesPreviousInstance() {
        Platform first = new Platform(MemberId.generate(), Duration.ofMinutes(5), 50);
        Platform second = new Platform(MemberId.generate(), Duration.ofMinutes(10), 200);
        repo.save(first);
        repo.save(second);
        assertThat(repo.findInstance()).contains(second);
    }

    @Test
    void clearDropsInstance() {
        repo.save(new Platform(MemberId.generate(), Duration.ofMinutes(15), 100));
        repo.clear();
        assertThat(repo.findInstance()).isEmpty();
    }

    @Test
    void saveRejectsNull() {
        assertThatThrownBy(() -> repo.save(null)).isInstanceOf(NullPointerException.class);
    }
}
