package com.eventsystem.application.acceptance;

import com.eventsystem.application.admin.BanResult;
import com.eventsystem.application.appexceptions.NotAuthorizedException;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.company.Permission;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.member.MemberStatus;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Acceptance tests for:
 *   UC 22 - Admin Removes/Bans Member
 *
 * Banning disables the account (status CANCELLED, so login is rejected) and
 * revokes the member's company roles globally. A member who is the sole founder
 * of a company is reported as orphaned rather than having the founder role
 * silently removed.
 */
class UC22_AdminBanMemberAcceptanceTest {

    // REQ: ADM-02
    // UC: UC 22 - Admin Removes/Bans Member
    // UAT: UAT-66 - Admin Ban Member
    @Test
    void adminBansMember_disablesAccountAndRevokesCompanyRolesGlobally() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();
        MemberId admin = app.initPlatformWithAdmin("admin-1");

        MemberId founder = app.memberId("founder-1");
        MemberId manager = app.memberId("manager-1");
        CompanyId companyId = app.createCompanyWithFounder(founder.value());
        app.createMemberIfMissing(manager.value());
        app.companyService.appointManager(
                companyId, founder, manager, Set.of(Permission.EVENT_INVENTORY_MANAGEMENT));
        app.companyService.acceptAppointment(companyId, manager);
        assertThat(app.companyService.canManageEvents(manager, companyId)).isTrue();

        BanResult result = app.adminMemberBanService.banMember(admin, manager);

        assertThat(app.members.findById(manager).orElseThrow().getStatus())
                .isEqualTo(MemberStatus.CANCELLED);
        assertThat(app.companyService.canManageEvents(manager, companyId)).isFalse();
        assertThat(result.orphanedCompanies()).isEmpty();
    }

    // REQ: ADM-02
    // UC: UC 22 - Admin Removes/Bans Member
    // UAT: UAT-67 - Ban Sole Founder
    @Test
    void adminBansSoleFounder_disablesAccountAndReportsOrphanedCompany() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();
        MemberId admin = app.initPlatformWithAdmin("admin-1");

        MemberId founder = app.memberId("founder-1");
        CompanyId companyId = app.createCompanyWithFounder(founder.value());

        BanResult result = app.adminMemberBanService.banMember(admin, founder);

        assertThat(app.members.findById(founder).orElseThrow().getStatus())
                .isEqualTo(MemberStatus.CANCELLED);
        assertThat(result.hasOrphanedCompanies()).isTrue();
        assertThat(result.orphanedCompanies()).contains(companyId);
    }

    // REQ: ADM-02
    // UC: UC 22 - Admin Removes/Bans Member
    // UAT: UAT-66 support - only an admin may ban
    @Test
    void nonAdminCannotBanMember() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();
        app.initPlatformWithAdmin("admin-1");

        MemberId notAnAdmin = app.memberId("member-1");
        app.createMemberIfMissing("victim-1");

        assertThatThrownBy(() -> app.adminMemberBanService.banMember(notAnAdmin, app.memberId("victim-1")))
                .isInstanceOf(NotAuthorizedException.class);

        assertThat(app.members.findById(app.memberId("victim-1")).orElseThrow().getStatus())
                .isEqualTo(MemberStatus.ACTIVE);
    }
}
