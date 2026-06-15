package com.eventsystem.infrastructure.persistence.repositories;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import com.eventsystem.domain.member.IMemberRepository;
import com.eventsystem.domain.member.Member;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.infrastructure.persistence.entities.MemberEntity;
import com.eventsystem.infrastructure.persistence.mapper.MemberMapper;
import com.eventsystem.infrastructure.persistence.springrepos.JpaMemberRepository;


public class MemberRepositoryImpl implements IMemberRepository {

    private final JpaMemberRepository jpaRepo;
    private final MemberMapper mapper;

    public MemberRepositoryImpl(JpaMemberRepository jpaRepo,
                                MemberMapper mapper) {
        this.jpaRepo = jpaRepo;
        this.mapper = mapper;
    }

    @Override
    public Optional<Member> findById(MemberId memberId) {
        return jpaRepo.findById(memberId.value())
                .map(e -> mapper.toDomain(e));
    }

    @Override
    public Optional<Member> findByUsername(String username) {
        return jpaRepo.findByUsername(username)
                .map(e -> mapper.toDomain(e));
    }

    @Override
    public Collection<Member> findAll() {
        return jpaRepo.findAll()
                .stream()
                .map(e -> mapper.toDomain(e))
                .collect(Collectors.toList());
    }

    @Override
    public void save(Member member) {
        MemberEntity entity = mapper.toEntity(member);
        jpaRepo.save(entity);
    }
}