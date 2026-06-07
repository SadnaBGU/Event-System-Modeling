package com.eventsystem.infrastructure.config;

import com.eventsystem.domain.member.IMemberRepository;
import com.eventsystem.domain.member.Member;
import com.eventsystem.domain.member.MemberId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AppConfigBeanTest {

    @Test
    void memberInformationPort_whenMemberMissing_throws() {
        AppConfig cfg = new AppConfig();
        IMemberRepository repo = mock(IMemberRepository.class);
        when(repo.findById(new MemberId("no-user"))).thenReturn(Optional.empty());

        var port = cfg.memberInformationPort(repo);
        assertThrows(IllegalArgumentException.class, () -> port.getMemberStatus(new MemberId("no-user")));
    }

    @Test
    void memberInformationPort_whenNoPersonalDetails_throws() {
        AppConfig cfg = new AppConfig();
        IMemberRepository repo = mock(IMemberRepository.class);
        Member stub = new Member(new MemberId("m-1"));
        when(repo.findById(new MemberId("m-1"))).thenReturn(Optional.of(stub));

        var port = cfg.memberInformationPort(repo);
        assertThrows(IllegalStateException.class, () -> port.getMemberBirthdate(new MemberId("m-1")));
    }
}
