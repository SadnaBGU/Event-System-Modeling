package com.eventsystem.infrastructure.persistence.springrepos;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.eventsystem.infrastructure.persistence.entities.MemberEntity;

public interface JpaMemberRepository
        extends JpaRepository<MemberEntity, String> {

    Optional<MemberEntity> findByUsername(String username);
}