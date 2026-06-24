package com.eventsystem.application.acceptance;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.company.CompanyStatus;
import com.eventsystem.domain.company.ProductionCompany;
import com.eventsystem.domain.domainexceptions.CompanyDomainException;
import com.eventsystem.domain.member.MemberId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Acceptance tests for production-company lifecycle:
 *   UC 11 - Open New Production Company
 *   UC 19 - Suspend and Reopen Production Company
 *
 * UAT-58 (suspend a company that has future events with sold tickets ->
 * WARNING + explicit confirmation) is intentionally not covered here: the
 * domain/application layer does not model the "warning then confirm" flow or
 * sold-ticket lookup, so it needs a product decision before it can be tested.
 */
class UC11_19_CompanyLifecycleAcceptanceTest {

    private ProductionCompany company(ApplicationAcceptanceFixture app, CompanyId companyId) {
        return app.companies.findById(companyId).orElseThrow();
    }

    // REQ: PRD-01
    // UC: UC 11 - Open New Production Company
    // UAT: UAT-33 - Open Company Success
    @Test
    void openCompanyWithValidDetails_createsActiveCompanyWithFounderAsOwner() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();
        MemberId founder = app.memberId("founder-1");

        CompanyId companyId = app.createCompanyWithFounder(founder.value());

        ProductionCompany created = company(app, companyId);
        assertThat(created.status()).isEqualTo(CompanyStatus.ACTIVE);
        assertThat(created.founderId()).isEqualTo(founder);
        // Founder acts as an owner: holds full management authority.
        assertThat(app.companyService.canManageEvents(founder, companyId)).isTrue();
        assertThat(app.companyService.canManagePurchasePolicies(founder, companyId)).isTrue();
    }

    // REQ: PRD-01
    // UC: UC 11 - Open New Production Company
    // UAT: UAT-34 - Open Company Duplicate
    @Test
    void openCompanyWithDuplicateName_isRejected() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();
        MemberId founder1 = app.memberId("founder-1");
        MemberId founder2 = app.memberId("founder-2");
        app.saveMember(founder1.value());
        app.saveMember(founder2.value());

        app.companyService.createCompany(founder1, "Acme Productions", "Live shows", 4.0);

        assertThatThrownBy(() ->
                app.companyService.createCompany(founder2, "Acme Productions", "Other shows", 3.0))
                .isInstanceOf(CompanyDomainException.class)
                .hasMessageContaining("name already exists");

        // Only the first company persisted.
        assertThat(app.companies.findByName("Acme Productions")).isPresent();
        assertThat(app.companies.findAll()).hasSize(1);
    }

    // REQ: PRD-12
    // UC: UC 19 - Suspend and Reopen Production Company
    // UAT: UAT-57 - Suspend Success
    @Test
    void founderSuspendsCompany_statusBecomesSuspendedAndManagementIsBlocked() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();
        MemberId founder = app.memberId("founder-1");
        CompanyId companyId = app.createCompanyWithFounder(founder.value());

        app.companyService.suspendCompany(companyId);

        assertThat(company(app, companyId).status()).isEqualTo(CompanyStatus.SUSPENDED);
        // Permissions are only granted while ACTIVE.
        assertThat(app.companyService.canManageEvents(founder, companyId)).isFalse();
    }

    // REQ: PRD-12
    // UC: UC 19 - Suspend and Reopen Production Company
    // UAT: UAT-59 - Reopen Success
    @Test
    void founderReopensSuspendedCompany_statusBecomesActiveAgain() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();
        MemberId founder = app.memberId("founder-1");
        CompanyId companyId = app.createCompanyWithFounder(founder.value());

        app.companyService.suspendCompany(companyId);
        app.companyService.reopenCompany(companyId);

        assertThat(company(app, companyId).status()).isEqualTo(CompanyStatus.ACTIVE);
        assertThat(app.companyService.canManageEvents(founder, companyId)).isTrue();
    }

    // REQ: PRD-12
    // UC: UC 19 - Suspend and Reopen Production Company
    // UAT: UAT-60 - Reopen Admin-Closed
    @Test
    void founderCannotReopenAdminClosedCompany() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();
        MemberId founder = app.memberId("founder-1");
        CompanyId companyId = app.createCompanyWithFounder(founder.value());

        app.companyService.adminCloseCompany(companyId);

        assertThatThrownBy(() -> app.companyService.reopenCompany(companyId))
                .isInstanceOf(CompanyDomainException.class)
                .hasMessageContaining("admin-closed");

        assertThat(company(app, companyId).status()).isEqualTo(CompanyStatus.ADMIN_CLOSED);
    }
}
