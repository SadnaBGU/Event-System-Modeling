package com.eventsystem.application.member;

import com.eventsystem.domain.member.MemberId;

/** Thrown when a {@link MemberId} does not resolve to a stored member. */
public class MemberNotFoundException extends RuntimeException {
    public MemberNotFoundException(MemberId id) {
        super("Member not found: " + id.value());
    }
}
