package com.eventsystem.application.acceptance;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.company.CompanyStatus;
import com.eventsystem.domain.domainexceptions.CompanyDomainException;
import com.eventsystem.domain.member.MemberId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Acceptance tests for:
 *   UC 21 - Admin Closes Production Company
 *
 * Note on scope: the UAT-64 acceptance criteria also list Roles_Revoked and
 * Notifications_Dispatched. The current application/domain layer only models the
 * status transition to ADMIN_CLOSED (which already blocks all management because
 * permissions require an ACTIVE company); automatic global role revocation and
 * closure notifications are not implemented yet, so those effects are not
 * asserted here.
 *
 * UC 22 (Admin Removes/Bans Member) is intentionally not covered: there is no
 * admin-facing "ban" operation that disables the account and revokes company
 * roles globally; it needs a product/implementation decision first.
 */
class UC21_AdminCompanyClosureAcceptanceTest {

    // REQ: ADM-01
    // UC: UC 21 - Admin Closes Production Company
    // UAT: UAT-64 - Admin Close Company
    @Test
    void adminClosesActiveCompany_statusBecomesAdminClosedAndManagementIsBlocked() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();
        MemberId founder = app.memberId("founder-1");
        CompanyId companyId = app.createCompanyWithFounder(founder.value());

        app.companyService.adminCloseCompany(companyId);

        assertThat(app.companies.findById(companyId).orElseThrow().status())
                .isEqualTo(CompanyStatus.ADMIN_CLOSED);
        // An admin-closed company grants no management permissions.
        assertThat(app.companyService.canManageEvents(founder, companyId)).isFalse();
    }

    // REQ: ADM-01
    // UC: UC 21 - Admin Closes Production Company
    // UAT: UAT-65 - Admin Close Fail
    @Test
    void adminClosesNonExistentCompany_isRejected() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();

        assertThatThrownBy(() -> app.companyService.adminCloseCompany(app.companyId("ghost-company")))
                .isInstanceOf(CompanyDomainException.class)
                .hasMessageContaining("not found");
    }
}