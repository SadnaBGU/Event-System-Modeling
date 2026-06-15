package com.eventsystem.infrastructure.persistence.springrepostests;

import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.platform.Platform;
import com.eventsystem.domain.platform.PlatformStatus;
import com.eventsystem.infrastructure.persistence.springrepos.PostgresPlatformRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresPlatformRepository.class)
class PostgresPlatformRepositoryTest {

    @Autowired
    private PostgresPlatformRepository platformRepository;

    @Test
    void saveAndFindInstance_persistsChanges() {
        // Arrange: יצירת פלטפורמה חדשה
        MemberId admin = new MemberId("ADMIN-1");
        Platform platform = new Platform(admin, Duration.ofMinutes(10), 100);
        platform.activate();
        
        platformRepository.save(platform);

        // Act
        Optional<Platform> foundOpt = platformRepository.findInstance();

        // Assert
        assertThat(foundOpt).isPresent();
        Platform found = foundOpt.get();
        assertThat(found.getStatus()).isEqualTo(PlatformStatus.ACTIVE);
        assertThat(found.isAdmin(admin)).isTrue();
        assertThat(found.getQueueLoadThreshold()).isEqualTo(100);
    }
}