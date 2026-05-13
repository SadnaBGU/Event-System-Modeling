package com.eventsystem.domain.platform;

import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.shared.ProviderId;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformTest {

    private Platform newPlatform(MemberId admin) {
        return new Platform(admin, Duration.ofMinutes(15), 100);
    }

    @Test
    void newPlatformIsInitializingWithInitialAdmin() {
        MemberId admin = MemberId.generate();
        Platform p = newPlatform(admin);
        assertThat(p.getStatus()).isEqualTo(PlatformStatus.INITIALIZING);
        assertThat(p.getSystemAdmins()).containsExactly(admin);
        assertThat(p.isAdmin(admin)).isTrue();
        assertThat(p.getDefaultReservationTimeout()).isEqualTo(Duration.ofMinutes(15));
        assertThat(p.getQueueLoadThreshold()).isEqualTo(100);
        assertThat(p.getPaymentProviders()).isEmpty();
        assertThat(p.getIssuanceProviders()).isEmpty();
    }

    @Test
    void constructorRejectsNullInitialAdmin() {
        assertThatThrownBy(() -> new Platform(null, Duration.ofMinutes(15), 100))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructorRejectsNonPositiveTimeout() {
        MemberId admin = MemberId.generate();
        assertThatThrownBy(() -> new Platform(admin, Duration.ZERO, 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Platform(admin, Duration.ofSeconds(-1), 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructorRejectsNegativeQueueThreshold() {
        MemberId admin = MemberId.generate();
        assertThatThrownBy(() -> new Platform(admin, Duration.ofMinutes(1), -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void activateTransitionsFromInitializingToActive() {
        Platform p = newPlatform(MemberId.generate());
        p.activate();
        assertThat(p.getStatus()).isEqualTo(PlatformStatus.ACTIVE);
    }

    @Test
    void activateFailsIfNotInitializing() {
        Platform p = newPlatform(MemberId.generate());
        p.activate();
        assertThatThrownBy(p::activate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shutdownIsAlwaysAllowed() {
        Platform p = newPlatform(MemberId.generate());
        p.shutdown();
        assertThat(p.getStatus()).isEqualTo(PlatformStatus.SHUTDOWN);
    }

    @Test
    void addAdminAddsToSet() {
        Platform p = newPlatform(MemberId.generate());
        MemberId second = MemberId.generate();
        p.addAdmin(second);
        assertThat(p.getSystemAdmins()).hasSize(2).contains(second);
    }

    @Test
    void addAdminIsIdempotent() {
        MemberId admin = MemberId.generate();
        Platform p = newPlatform(admin);
        p.addAdmin(admin);
        assertThat(p.getSystemAdmins()).hasSize(1);
    }

    @Test
    void removeAdminRejectsRemovingTheLastOne() {
        MemberId admin = MemberId.generate();
        Platform p = newPlatform(admin);
        assertThatThrownBy(() -> p.removeAdmin(admin))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void removeAdminWorksWhenMultipleAdminsExist() {
        MemberId a = MemberId.generate();
        MemberId b = MemberId.generate();
        Platform p = newPlatform(a);
        p.addAdmin(b);
        p.removeAdmin(a);
        assertThat(p.getSystemAdmins()).containsExactly(b);
    }

    @Test
    void removeAdminIgnoresUnknownMember() {
        MemberId admin = MemberId.generate();
        Platform p = newPlatform(admin);
        p.removeAdmin(MemberId.generate());
        assertThat(p.getSystemAdmins()).containsExactly(admin);
    }

    @Test
    void paymentProvidersAreManaged() {
        Platform p = newPlatform(MemberId.generate());
        ProviderId visa = new ProviderId("visa");
        p.addPaymentProvider(visa);
        assertThat(p.getPaymentProviders()).containsExactly(visa);
        p.removePaymentProvider(visa);
        assertThat(p.getPaymentProviders()).isEmpty();
    }

    @Test
    void issuanceProvidersAreManaged() {
        Platform p = newPlatform(MemberId.generate());
        ProviderId ticketmaster = new ProviderId("ticketmaster");
        p.addIssuanceProvider(ticketmaster);
        assertThat(p.getIssuanceProviders()).containsExactly(ticketmaster);
        p.removeIssuanceProvider(ticketmaster);
        assertThat(p.getIssuanceProviders()).isEmpty();
    }

    @Test
    void setDefaultReservationTimeoutValidatesPositive() {
        Platform p = newPlatform(MemberId.generate());
        p.setDefaultReservationTimeout(Duration.ofMinutes(5));
        assertThat(p.getDefaultReservationTimeout()).isEqualTo(Duration.ofMinutes(5));
        assertThatThrownBy(() -> p.setDefaultReservationTimeout(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void setQueueLoadThresholdValidatesNonNegative() {
        Platform p = newPlatform(MemberId.generate());
        p.setQueueLoadThreshold(0);
        assertThat(p.getQueueLoadThreshold()).isZero();
        assertThatThrownBy(() -> p.setQueueLoadThreshold(-5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void collectionsAreUnmodifiable() {
        Platform p = newPlatform(MemberId.generate());
        assertThatThrownBy(() -> p.getSystemAdmins().add(MemberId.generate()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> p.getPaymentProviders().add(new ProviderId("x")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> p.getIssuanceProviders().add(new ProviderId("x")))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
