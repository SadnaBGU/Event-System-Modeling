package com.eventsystem.domain.member;

import java.util.Optional;

/**
 * Repository interface (port) for the {@link Member} aggregate.
 * Implementations live in the infrastructure layer.
 */
public interface MemberRepository {

    Optional<Member> findById(MemberId memberId);

    Optional<Member> findByUsername(String username);

    void save(Member member);
}
