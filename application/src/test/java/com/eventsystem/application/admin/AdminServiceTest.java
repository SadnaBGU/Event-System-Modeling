package com.eventsystem.application.admin;

import com.eventsystem.application.appexceptions.NotAuthorizedException;
import com.eventsystem.domain.member.HashedCredentials;
import com.eventsystem.domain.member.Member;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.member.MemberRepository;
import com.eventsystem.domain.member.PersonalDetails;
import com.eventsystem.domain.platform.Platform;
import com.eventsystem.domain.platform.PlatformRepository;
import com.eventsystem.domain.platform.PlatformStatus;
import com.eventsystem.domain.shared.ProviderId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock private PlatformRepository platformRepo;
    @Mock private MemberRepository memberRepo;
    @InjectMocks private AdminService service;

    private final MemberId admin = MemberId.generate();
    private final MemberId nonAdmin = MemberId.generate();

    private Platform platformWithAdmin() {
        return new Platform(admin, Duration.ofMinutes(15), 100);
    }

    private Member memberOf(MemberId id) {
        return new Member(id, "u" + id.value().substring(0, 4),
                new HashedCredentials("h", "s", "BCrypt"),
                new PersonalDetails("F", "L", "e@x", LocalDate.of(1990, 1, 1)));
    }

    @Test
    void getPlatformReturnsDtoForAdmin() {
        Platform p = platformWithAdmin();
        when(platformRepo.findInstance()).thenReturn(Optional.of(p));

        PlatformDto dto = service.getPlatform(admin);

        assertThat(dto.status()).isEqualTo(PlatformStatus.INITIALIZING);
        assertThat(dto.systemAdmins()).contains(admin);
    }

    @Test
    void anyOperationRejectsNonAdmin() {
        when(platformRepo.findInstance()).thenReturn(Optional.of(platformWithAdmin()));
        assertThatThrownBy(() -> service.getPlatform(nonAdmin))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void anyOperationFailsIfPlatformNotInitialised() {
        when(platformRepo.findInstance()).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getPlatform(admin))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void activateTransitionsAndSaves() {
        Platform p = platformWithAdmin();
        when(platformRepo.findInstance()).thenReturn(Optional.of(p));
        service.activate(admin);
        assertThat(p.getStatus()).isEqualTo(PlatformStatus.ACTIVE);
        verify(platformRepo).save(p);
    }

    @Test
    void shutdownTransitionsAndSaves() {
        Platform p = platformWithAdmin();
        when(platformRepo.findInstance()).thenReturn(Optional.of(p));
        service.shutdown(admin);
        assertThat(p.getStatus()).isEqualTo(PlatformStatus.SHUTDOWN);
        verify(platformRepo).save(p);
    }

    @Test
    void addAdminPromotesExistingMember() {
        Platform p = platformWithAdmin();
        MemberId newAdmin = MemberId.generate();
        when(platformRepo.findInstance()).thenReturn(Optional.of(p));
        when(memberRepo.findById(newAdmin)).thenReturn(Optional.of(memberOf(newAdmin)));

        service.addAdmin(admin, newAdmin);

        assertThat(p.getSystemAdmins()).contains(newAdmin);
        verify(platformRepo).save(p);
    }

    @Test
    void addAdminRejectsUnknownMember() {
        Platform p = platformWithAdmin();
        MemberId ghost = MemberId.generate();
        when(memberRepo.findById(ghost)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addAdmin(admin, ghost))
                .isInstanceOf(IllegalArgumentException.class);
        verify(platformRepo, never()).save(any());
    }

    @Test
    void removeAdminPropagatesLastAdminGuard() {
        Platform p = platformWithAdmin();
        when(platformRepo.findInstance()).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.removeAdmin(admin, admin))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void paymentProviderManagement() {
        Platform p = platformWithAdmin();
        ProviderId visa = new ProviderId("visa");
        when(platformRepo.findInstance()).thenReturn(Optional.of(p));

        service.addPaymentProvider(admin, visa);
        assertThat(p.getPaymentProviders()).contains(visa);

        service.removePaymentProvider(admin, visa);
        assertThat(p.getPaymentProviders()).isEmpty();
    }

    @Test
    void issuanceProviderManagement() {
        Platform p = platformWithAdmin();
        ProviderId tm = new ProviderId("tm");
        when(platformRepo.findInstance()).thenReturn(Optional.of(p));

        service.addIssuanceProvider(admin, tm);
        assertThat(p.getIssuanceProviders()).contains(tm);

        service.removeIssuanceProvider(admin, tm);
        assertThat(p.getIssuanceProviders()).isEmpty();
    }

    @Test
    void setDefaultReservationTimeoutAppliesToAggregate() {
        Platform p = platformWithAdmin();
        when(platformRepo.findInstance()).thenReturn(Optional.of(p));

        service.setDefaultReservationTimeout(admin, Duration.ofMinutes(30));

        assertThat(p.getDefaultReservationTimeout()).isEqualTo(Duration.ofMinutes(30));
        verify(platformRepo).save(p);
    }

    @Test
    void setQueueLoadThresholdAppliesToAggregate() {
        Platform p = platformWithAdmin();
        when(platformRepo.findInstance()).thenReturn(Optional.of(p));

        service.setQueueLoadThreshold(admin, 250);

        assertThat(p.getQueueLoadThreshold()).isEqualTo(250);
    }
}
