package com.eventsystem.application.acceptance;

import com.eventsystem.application.appexceptions.NotAuthorizedException;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.platform.PlatformStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Acceptance tests for:
 *   UC 1 - Platform and Market Initialization
 *
 * Uses the real {@link com.eventsystem.application.admin.AdminService} with a
 * fake platform repository and a toggleable external-systems availability port.
 *
 * UAT-03 ("no admin defined in DB") is reinterpreted as "activation requested by
 * a non-administrator is rejected": the {@link com.eventsystem.domain.platform.Platform}
 * aggregate cannot be constructed without at least one admin, so an empty-admin
 * platform is not a representable state.
 */
class UC01_PlatformInitAcceptanceTest {

    // REQ: SYS-01
    // UC: UC 1 - Platform and Market Initialization
    // UAT: UAT-01 - Successful Initialization
    @Test
    void activateWithAvailableServicesAndAdmin_movesPlatformToActive() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();
        MemberId admin = app.initPlatformWithAdmin("admin-1");
        app.externalSystems.available = true;

        app.adminService.activate(admin);

        assertThat(app.platformRepo.findInstance().orElseThrow().getStatus())
                .isEqualTo(PlatformStatus.ACTIVE);
    }

    // REQ: SYS-01
    // UC: UC 1 - Platform and Market Initialization
    // UAT: UAT-02 - Init Fails - Service Down
    @Test
    void activateWhenExternalServicesUnavailable_isHaltedAndPlatformStaysInitializing() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();
        MemberId admin = app.initPlatformWithAdmin("admin-1");
        app.externalSystems.available = false;

        assertThatThrownBy(() -> app.adminService.activate(admin))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("external systems");

        assertThat(app.platformRepo.findInstance().orElseThrow().getStatus())
                .isEqualTo(PlatformStatus.INITIALIZING);
    }

    // REQ: SYS-01
    // UC: UC 1 - Platform and Market Initialization
    // UAT: UAT-03 - Init Fails - No Admin (reinterpreted: non-admin cannot activate)
    @Test
    void activateByNonAdmin_isRejected() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();
        app.initPlatformWithAdmin("admin-1");
        app.externalSystems.available = true;

        MemberId notAnAdmin = app.memberId("intruder-1");
        assertThatThrownBy(() -> app.adminService.activate(notAnAdmin))
                .isInstanceOf(NotAuthorizedException.class);

        assertThat(app.platformRepo.findInstance().orElseThrow().getStatus())
                .isEqualTo(PlatformStatus.INITIALIZING);
    }
}
