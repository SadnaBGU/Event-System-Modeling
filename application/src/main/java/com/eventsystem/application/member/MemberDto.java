package com.eventsystem.application.member;

import com.eventsystem.domain.member.Member;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.member.MemberStatus;

import java.time.LocalDate;

/** Read-only projection of a {@link com.eventsystem.domain.member.Member} for outside callers. */
public record MemberDto(
        MemberId memberId,
        String username,
        String firstName,
        String lastName,
        String email,
        LocalDate dateOfBirth,
        MemberStatus status) {

    public static MemberDto from(Member m) {
        return new MemberDto(
                m.getMemberId(),
                m.getUsername(),
                m.getPersonalDetails().firstName(),
                m.getPersonalDetails().lastName(),
                m.getPersonalDetails().email(),
                m.getPersonalDetails().dateOfBirth(),
                m.getStatus());
    }
}
