package com.eventsystem.domain.member;

import java.util.Collection;
import java.util.Optional;

/**
 * Repository interface (port) for the {@link Member} aggregate.
 * Implementations live in the infrastructure layer.
 */
public interface IMemberRepository {

    Optional<Member> findById(MemberId memberId);

    default Optional<Member> findByIdForUpdate(MemberId memberId) {
        return findById(memberId);
    }

    Optional<Member> findByUsername(String username);

    Collection<Member> findAll();

    void save(Member member);
}
