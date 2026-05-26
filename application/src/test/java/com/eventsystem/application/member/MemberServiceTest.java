package com.eventsystem.application.member;

import com.eventsystem.application.appexceptions.MemberNotFoundException;
import com.eventsystem.domain.member.HashedCredentials;
import com.eventsystem.domain.member.Member;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.member.MemberStatus;
import com.eventsystem.domain.member.PersonalDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock private IMemberRepository members;
    @InjectMocks private MemberService service;

    private static final HashedCredentials CREDS = new HashedCredentials("h", "s", "BCrypt");
    private static final PersonalDetails DETAILS = new PersonalDetails(
            "Jon", "Snow", "jon@x", LocalDate.of(1990, 1, 1));

    private Member member(MemberId id) {
        return new Member(id, "jon", CREDS, DETAILS);
    }

    @Test
    void getDetailsReturnsDtoForSelf() {
        MemberId id = MemberId.generate();
        Member m = member(id);
        when(members.findById(id)).thenReturn(Optional.of(m));

        MemberDto dto = service.getDetails(id, id);

        assertThat(dto.memberId()).isEqualTo(id);
        assertThat(dto.username()).isEqualTo("jon");
        assertThat(dto.firstName()).isEqualTo("Jon");
        assertThat(dto.status()).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    void getDetailsRejectsActingOnOtherMember() {
        MemberId actor = MemberId.generate();
        MemberId other = MemberId.generate();
        assertThatThrownBy(() -> service.getDetails(actor, other))
                .isInstanceOf(SecurityException.class);
        verify(members, never()).findById(any());
    }

    @Test
    void getDetailsThrowsWhenMemberMissing() {
        MemberId id = MemberId.generate();
        when(members.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getDetails(id, id))
                .isInstanceOf(MemberNotFoundException.class);
    }

    @Test
    void updateDetailsAppliesNewDetailsAndSaves() {
        MemberId id = MemberId.generate();
        Member m = member(id);
        when(members.findById(id)).thenReturn(Optional.of(m));

        UpdateMemberDetailsRequest req = new UpdateMemberDetailsRequest(
                "Aegon", "Targaryen", "aegon@dragon", LocalDate.of(1990, 1, 1));
        MemberDto dto = service.updateDetails(id, id, req);

        assertThat(dto.firstName()).isEqualTo("Aegon");
        assertThat(dto.lastName()).isEqualTo("Targaryen");
        verify(members).save(m);
    }

    @Test
    void updateDetailsRejectsCrossMemberAction() {
        MemberId actor = MemberId.generate();
        MemberId other = MemberId.generate();
        UpdateMemberDetailsRequest req = new UpdateMemberDetailsRequest(
                "x", "y", "z@w", LocalDate.of(1990, 1, 1));
        assertThatThrownBy(() -> service.updateDetails(actor, other, req))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void updateDetailsRejectsCancelledMember() {
        MemberId id = MemberId.generate();
        Member m = member(id);
        m.cancel();
        when(members.findById(id)).thenReturn(Optional.of(m));

        UpdateMemberDetailsRequest req = new UpdateMemberDetailsRequest(
                "x", "y", "z@w", LocalDate.of(1990, 1, 1));
        assertThatThrownBy(() -> service.updateDetails(id, id, req))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cancelOwnAccountSetsStatusAndSaves() {
        MemberId id = MemberId.generate();
        Member m = member(id);
        when(members.findById(id)).thenReturn(Optional.of(m));

        service.cancelOwnAccount(id, id);

        assertThat(m.getStatus()).isEqualTo(MemberStatus.CANCELLED);
        verify(members).save(m);
    }

    @Test
    void cancelOwnAccountRejectsCrossMemberAction() {
        MemberId actor = MemberId.generate();
        MemberId other = MemberId.generate();
        assertThatThrownBy(() -> service.cancelOwnAccount(actor, other))
                .isInstanceOf(SecurityException.class);
    }
}
