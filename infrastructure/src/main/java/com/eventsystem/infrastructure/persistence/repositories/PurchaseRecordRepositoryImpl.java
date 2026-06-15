package com.eventsystem.infrastructure.persistence.repositories;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import com.eventsystem.domain.purchaserecord.IPurchaseRecordRepository;
import com.eventsystem.domain.purchaserecord.PurchaseRecord;
import com.eventsystem.infrastructure.persistence.mapper.PurchaseRecordMapper;
import com.eventsystem.infrastructure.persistence.springrepos.JpaPurchaseRecordRepository;


public class PurchaseRecordRepositoryImpl implements IPurchaseRecordRepository {

    private final JpaPurchaseRecordRepository jpaRepo;
    private final PurchaseRecordMapper mapper;

    public PurchaseRecordRepositoryImpl(JpaPurchaseRecordRepository jpaRepo,
                                        PurchaseRecordMapper mapper) {
        this.jpaRepo = jpaRepo;
        this.mapper = mapper;
    }

    @Override
    public void append(PurchaseRecord record) {
        jpaRepo.save(mapper.toEntity(record));
    }

    @Override
    public Optional<PurchaseRecord> findById(String recordId) {
        return jpaRepo.findById(recordId)
                .map(e -> mapper.toDomain(e));
    }

    @Override
    public List<PurchaseRecord> findByBuyer(String buyerId) {
        return jpaRepo.findByBuyerId(buyerId)
                .stream()
                .map(e -> mapper.toDomain(e))
                .collect(Collectors.toList());
    }

    @Override
    public List<PurchaseRecord> findByEvent(String eventId) {
        return jpaRepo.findByEventSnapshot_EventId(eventId)
                .stream()
                .map(e -> mapper.toDomain(e))
                .collect(Collectors.toList());
    }

    @Override
    public List<PurchaseRecord> findAll() {
        return jpaRepo.findAll()
                .stream()
                .map(e -> mapper.toDomain(e))
                .collect(Collectors.toList());
    }
}