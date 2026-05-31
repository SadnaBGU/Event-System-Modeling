package com.eventsystem.application.member;

import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.member.MemberStatus;

import java.time.LocalDate;

public interface IMemberInformationPort {

    LocalDate getMemberBirthdate(MemberId memberId);
    MemberStatus getMemberStatus(MemberId memberId);
}
