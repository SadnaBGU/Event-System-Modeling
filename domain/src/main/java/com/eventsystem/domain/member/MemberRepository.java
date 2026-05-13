package com.eventsystem.domain.member;

import java.util.Optional;

public interface MemberRepository {
    Optional<Member> findById(MemberId memberId);
}
