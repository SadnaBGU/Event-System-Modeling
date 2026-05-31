package com.eventsystem.application.admin;

import com.eventsystem.application.appexceptions.NotAuthorizedException;
import com.eventsystem.application.member.IMemberRepository;
import com.eventsystem.domain.member.Member;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.member.MemberStatus;
import com.eventsystem.domain.platform.Platform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminSuspensionTest {

    @Mock private IPlatformRepository platformRepo;
    @Mock private IMemberRepository memberRepo;

    private AdminService service;
    private MemberId adminId;
    private MemberId targetId;
    private Platform platform;

    @BeforeEach
    void setUp() {
        service = new AdminService(platformRepo, memberRepo);
        adminId = MemberId.random();
        targetId = MemberId.random();

        platform = new Platform(adminId, Duration.ofMinutes(15), 100);
        platform.activate();

        lenient().when(platformRepo.findInstance()).thenReturn(Optional.of(platform));
    }

    // ── suspendMember (II.6.7) ───────────────────────────────────────────────

    @Test
    void suspendMember_temporary_suspendsMemberAndSaves() {
        Member target = new Member(targetId);
        when(memberRepo.findById(targetId)).thenReturn(Optional.of(target));

        service.suspendMember(adminId, targetId, Duration.ofDays(7));

        assertThat(target.getStatus()).isEqualTo(MemberStatus.SUSPENDED);
        assertThat(target.getSuspension()).isPresent();
        assertThat(target.getSuspension().get().isPermanent()).isFalse();
        verify(memberRepo).save(target);
    }

    @Test
    void suspendMember_permanent_nullDuration() {
        Member target = new Member(targetId);
        when(memberRepo.findById(targetId)).thenReturn(Optional.of(target));

        service.suspendMember(adminId, targetId, null);

        assertThat(target.getSuspension().get().isPermanent()).isTrue();
        verify(memberRepo).save(target);
    }

    @Test
    void suspendMember_notAdmin_throws() {
        MemberId nonAdmin = MemberId.random();
        assertThatExceptionOfType(NotAuthorizedException.class)
                .isThrownBy(() -> service.suspendMember(nonAdmin, targetId, Duration.ofDays(1)));
        verify(memberRepo, never()).save(any());
    }

    @Test
    void suspendMember_unknownTarget_throws() {
        when(memberRepo.findById(targetId)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.suspendMember(adminId, targetId, Duration.ofDays(1)));
        verify(memberRepo, never()).save(any());
    }

    // ── unsuspendMember (II.6.8) ─────────────────────────────────────────────

    @Test
    void unsuspendMember_liftsSuspension() {
        Member target = new Member(targetId);
        target.suspend(Instant.now(), Duration.ofDays(7));
        when(memberRepo.findById(targetId)).thenReturn(Optional.of(target));

        service.unsuspendMember(adminId, targetId);

        assertThat(target.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(target.getSuspension()).isEmpty();
        verify(memberRepo).save(target);
    }

    @Test
    void unsuspendMember_notSuspended_throws() {
        Member target = new Member(targetId);
        when(memberRepo.findById(targetId)).thenReturn(Optional.of(target));

        assertThatIllegalStateException()
                .isThrownBy(() -> service.unsuspendMember(adminId, targetId));
        verify(memberRepo, never()).save(any());
    }

    @Test
    void unsuspendMember_notAdmin_throws() {
        MemberId nonAdmin = MemberId.random();
        assertThatExceptionOfType(NotAuthorizedException.class)
                .isThrownBy(() -> service.unsuspendMember(nonAdmin, targetId));
    }

    // ── listSuspensions (II.6.9) ─────────────────────────────────────────────

    @Test
    void listSuspensions_returnsSuspendedMembers() {
        Member suspended = new Member(targetId);
        suspended.suspend(Instant.now(), Duration.ofDays(3));

        Member active = new Member(MemberId.random());

        when(memberRepo.findAll()).thenReturn(List.of(suspended, active));

        List<SuspensionDto> result = service.listSuspensions(adminId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).memberId()).isEqualTo(targetId.value());
        assertThat(result.get(0).duration()).isEqualTo("PT72H");
        assertThat(result.get(0).endsAt()).isNotNull();
    }

    @Test
    void listSuspensions_permanentSuspension_showsNullEndsAt() {
        Member suspended = new Member(targetId);
        suspended.suspend(Instant.now(), null);
        when(memberRepo.findAll()).thenReturn(List.of(suspended));

        List<SuspensionDto> result = service.listSuspensions(adminId);

        assertThat(result.get(0).duration()).isEqualTo("PERMANENT");
        assertThat(result.get(0).endsAt()).isNull();
    }

    @Test
    void listSuspensions_noSuspensions_returnsEmpty() {
        when(memberRepo.findAll()).thenReturn(List.of(new Member(targetId)));

        assertThat(service.listSuspensions(adminId)).isEmpty();
    }

    @Test
    void listSuspensions_notAdmin_throws() {
        MemberId nonAdmin = MemberId.random();
        assertThatExceptionOfType(NotAuthorizedException.class)
                .isThrownBy(() -> service.listSuspensions(nonAdmin));
    }
}
