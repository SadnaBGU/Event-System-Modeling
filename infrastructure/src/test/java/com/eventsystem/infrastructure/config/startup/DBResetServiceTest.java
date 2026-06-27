package com.eventsystem.infrastructure.config.startup;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DBResetServiceTest {

    @Test
    void resetDatabase_usesPostgresTruncateCascade() {
        EntityManager entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);

        when(entityManager.createNativeQuery(contains("TRUNCATE TABLE")))
                .thenReturn(query);
        when(query.executeUpdate()).thenReturn(0);

        DBResetService service = new DBResetService();
        ReflectionTestUtils.setField(service, "entityManager", entityManager);

        service.resetDatabase();

        verify(entityManager, times(2)).flush();
        verify(entityManager, times(2)).clear();
        verify(entityManager).createNativeQuery(contains("RESTART IDENTITY CASCADE"));
        verify(query).executeUpdate();
    }
}