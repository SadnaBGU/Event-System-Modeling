package com.eventsystem.application.acceptance;

import com.eventsystem.application.policy.policybuilder.PolicyOwnerCommand;
import com.eventsystem.application.policy.policybuilder.PolicyRuleCommand;
import com.eventsystem.application.policy.policybuilder.PolicyScopeCommand;
import com.eventsystem.application.policy.policybuilder.PurchasePolicyCommand;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.company.Permission;
import com.eventsystem.domain.domainexceptions.CompanyDomainException;
import com.eventsystem.domain.member.MemberId;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UC17_20_RolesPermissionsAcceptanceTest {

    private static PurchasePolicyCommand companyWidePolicyCommand(
            MemberId actor,
            CompanyId companyId
    ) {
        return new PurchasePolicyCommand(
                actor.value(),
                companyId.value(),
                "Company max 4",
                new PolicyScopeCommand(true, Set.of()),
                new PolicyRuleCommand("MAX_TICKETS", 4, null, null, null, null),
                true,
                PolicyOwnerCommand.COMPANY
        );
    }

    // REQ: PRD-06, PRD-15
    // UC: UC 17 - Manage Company Roles + UC 20 - Authorized Management Action
    // UAT: UAT-50 - Appoint Manager, UAT-61 - Authorized Action Success
    @Test
    void founderAppointsManagerWithPolicyPermission_managerCanCreateCompanyWidePolicyAfterAccepting() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();

        MemberId founder = app.memberId("founder-1");
        MemberId manager = app.memberId("manager-1");
        CompanyId companyId = app.createCompanyWithFounder(founder.value());
        app.createMemberIfMissing(manager.value());

        app.companyService.appointManager(
                companyId,
                founder,
                manager,
                Set.of(Permission.MODIFY_POLICIES)
        );

        assertThat(app.companyService.canManagePurchasePolicies(manager, companyId)).isFalse();

        app.companyService.acceptAppointment(companyId, manager);

        assertThat(app.companyService.canManagePurchasePolicies(manager, companyId)).isTrue();

        app.policyManagementService.createPurchasePolicy(
                companyWidePolicyCommand(manager, companyId)
        );

        assertThat(app.realPurchasePolicies.findByCompanyId(companyId)).hasSize(1);
    }

    // REQ: PRD-07
    // UC: UC 17 - Manage Company Roles
    // UAT: UAT-51 - Appoint Owner
    @Test
    void founderAppointsOwner_targetCanActAsOwnerAfterAccepting() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();

        MemberId founder = app.memberId("founder-1");
        MemberId owner = app.memberId("owner-1");
        CompanyId companyId = app.createCompanyWithFounder(founder.value());
        app.createMemberIfMissing(owner.value());

        app.companyService.appointOwner(companyId, founder, owner);

        assertThat(app.companyService.canManagePurchasePolicies(owner, companyId)).isFalse();

        app.companyService.acceptAppointment(companyId, owner);

        assertThat(app.companyService.canManagePurchasePolicies(owner, companyId)).isTrue();
        assertThat(app.companyService.canManageEvents(owner, companyId)).isTrue();

        app.policyManagementService.createPurchasePolicy(
                companyWidePolicyCommand(owner, companyId)
        );

        assertThat(app.realPurchasePolicies.findByCompanyId(companyId)).hasSize(1);
    }

    // REQ: PRD-07, INV-13, TST-13
    // UC: UC 17 - Manage Company Roles
    // UAT: UAT-52 reinterpret - invalid owner appointment / duplicate appointment
    @Test
    void duplicateOwnerAppointment_isRejected() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();

        MemberId founder = app.memberId("founder-1");
        MemberId owner = app.memberId("owner-1");
        CompanyId companyId = app.createCompanyWithFounder(founder.value());
        app.createMemberIfMissing(owner.value());

        app.companyService.appointOwner(companyId, founder, owner);

        assertThatThrownBy(() ->
                app.companyService.appointOwner(companyId, founder, owner)
        )
                .isInstanceOf(CompanyDomainException.class);

        app.companyService.acceptAppointment(companyId, owner);
        assertThat(app.companyService.canManagePurchasePolicies(owner, companyId)).isTrue();
    }

    // REQ: PRD-08
    // UC: UC 17 - Manage Company Roles
    // UAT: UAT-53 - Remove Owner
    @Test
    void founderRemovesOwner_removedOwnerLosesPermissions() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();

        MemberId founder = app.memberId("founder-1");
        MemberId owner = app.memberId("owner-1");
        CompanyId companyId = app.createCompanyWithFounder(founder.value());
        app.createMemberIfMissing(owner.value());

        app.companyService.appointOwner(companyId, founder, owner);
        app.companyService.acceptAppointment(companyId, owner);

        assertThat(app.companyService.canManagePurchasePolicies(owner, companyId)).isTrue();

        app.companyService.removeAppointee(companyId, founder, owner);

        assertThat(app.companyService.canManagePurchasePolicies(owner, companyId)).isFalse();
        assertThat(app.companyService.canManageEvents(owner, companyId)).isFalse();
    }

    // REQ: PRD-09
    // UC: UC 17 - Manage Company Roles
    // UAT: UAT-54 - Owner Resigns
    @Test
    void nonFounderOwnerResigns_losesOwnerPermissionsAndFounderRemainsAuthorized() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();

        MemberId founder = app.memberId("founder-1");
        MemberId owner = app.memberId("owner-1");
        CompanyId companyId = app.createCompanyWithFounder(founder.value());
        app.createMemberIfMissing(owner.value());

        app.companyService.appointOwner(companyId, founder, owner);
        app.companyService.acceptAppointment(companyId, owner);

        assertThat(app.companyService.canManagePurchasePolicies(owner, companyId)).isTrue();

        app.companyService.relinquishOwnership(companyId, owner);

        assertThat(app.companyService.canManagePurchasePolicies(owner, companyId)).isFalse();
        assertThat(app.companyService.canManagePurchasePolicies(founder, companyId)).isTrue();
    }

    // REQ: PRD-15
    // UC: UC 20 - Perform Authorized Management Action
    // UAT: UAT-62 - Unauthorized Action Denied
    @Test
    void managerWithoutPolicyPermission_cannotModifyCompanyWidePolicy() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();

        MemberId founder = app.memberId("founder-1");
        MemberId manager = app.memberId("manager-1");
        CompanyId companyId = app.createCompanyWithFounder(founder.value());
        app.createMemberIfMissing(manager.value());

        app.companyService.appointManager(
                companyId,
                founder,
                manager,
                Set.of(Permission.EVENT_INVENTORY_MANAGEMENT)
        );
        app.companyService.acceptAppointment(companyId, manager);

        assertThat(app.companyService.canManageEvents(manager, companyId)).isTrue();
        assertThat(app.companyService.canManagePurchasePolicies(manager, companyId)).isFalse();

        assertThatThrownBy(() ->
                app.policyManagementService.createPurchasePolicy(
                        companyWidePolicyCommand(manager, companyId)
                )
        )
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("not allowed to manage purchase policies");

        assertThat(app.realPurchasePolicies.findByCompanyId(companyId)).isEmpty();
    }

    // REQ: PRD-10, PRD-15
    // UC: UC 20 - Perform Authorized Management Action
    // UAT: UAT-63 - Permission Revoked Mid-Action
    @Test
    void managerPermissionRevokedBeforeSubmit_actionIsDeniedAtSubmissionTime() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();

        MemberId founder = app.memberId("founder-1");
        MemberId manager = app.memberId("manager-1");
        CompanyId companyId = app.createCompanyWithFounder(founder.value());
        app.createMemberIfMissing(manager.value());

        app.companyService.appointManager(
                companyId,
                founder,
                manager,
                Set.of(Permission.MODIFY_POLICIES)
        );
        app.companyService.acceptAppointment(companyId, manager);

        PurchasePolicyCommand commandPreparedBeforeRevocation =
                companyWidePolicyCommand(manager, companyId);

        assertThat(app.companyService.canManagePurchasePolicies(manager, companyId)).isTrue();

        app.companyService.modifyManagerPermissions(
                companyId,
                founder,
                manager,
                Set.of(Permission.EVENT_INVENTORY_MANAGEMENT)
        );

        assertThat(app.companyService.canManagePurchasePolicies(manager, companyId)).isFalse();

        assertThatThrownBy(() ->
                app.policyManagementService.createPurchasePolicy(commandPreparedBeforeRevocation)
        )
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("not allowed to manage purchase policies");

        assertThat(app.realPurchasePolicies.findByCompanyId(companyId)).isEmpty();
    }
}