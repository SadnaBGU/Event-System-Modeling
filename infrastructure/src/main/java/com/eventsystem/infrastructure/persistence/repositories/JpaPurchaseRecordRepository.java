package com.eventsystem.infrastructure.persistence.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.eventsystem.infrastructure.persistence.entities.PurchaseRecordEntity;

public interface JpaPurchaseRecordRepository
        extends JpaRepository<PurchaseRecordEntity, String> {

    List<PurchaseRecordEntity> findByBuyerId(String buyerId);

    List<PurchaseRecordEntity> findByEventSnapshot_EventId(String eventId);
}