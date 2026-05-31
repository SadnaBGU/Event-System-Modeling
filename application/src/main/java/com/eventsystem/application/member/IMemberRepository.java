package com.eventsystem.application.member;

import java.util.Collection;
import java.util.Optional;

import com.eventsystem.domain.member.Member;
import com.eventsystem.domain.member.MemberId;

/**
 * Repository interface (port) for the {@link Member} aggregate.
 * Implementations live in the infrastructure layer.
 */
public interface IMemberRepository {

    Optional<Member> findById(MemberId memberId);

    Optional<Member> findByUsername(String username);

    Collection<Member> findAll();

    void save(Member member);
}
