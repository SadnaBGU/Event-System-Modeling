package com.eventsystem.domain.member;

import java.util.Objects;

public final class Member {
    private final MemberId memberId;

    public Member(MemberId memberId) {
        this.memberId = Objects.requireNonNull(memberId, "memberId must not be null");
    }

    public MemberId memberId() {
        return memberId;
    }
}
