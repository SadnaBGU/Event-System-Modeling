package com.eventsystem.infrastructure.persistence.springrepos;

import com.eventsystem.domain.member.IMemberRepository;
import com.eventsystem.domain.member.Member;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.member.MemberStatus;

import java.util.Collection;
import java.util.Optional;

public class PostgresMemberRepository implements IMemberRepository {

    private final SpringDataMemberRepository jpaRepo;

    public PostgresMemberRepository(SpringDataMemberRepository jpaRepo) {
        this.jpaRepo = jpaRepo;
    }

    @SuppressWarnings("null")
    @Override
    public Optional<Member> findById(MemberId id) {
        return jpaRepo.findById(id);
    }

    @Override
    public Optional<Member> findByIdForUpdate(MemberId id) {
        return jpaRepo.findByIdForUpdate(id);
    }

    @Override
    public Optional<Member> findByUsername(String username) {
        return jpaRepo.findByUsername(username);
    }

    public Optional<Member> findActiveMembers() {
        return jpaRepo.findByStatus(MemberStatus.ACTIVE);
    }

    @SuppressWarnings("null")
    @Override
    public void save(Member member) {
        jpaRepo.save(member);
    }

    @Override
    public Collection<Member> findAll() {
        return jpaRepo.findAll();
    }
}
