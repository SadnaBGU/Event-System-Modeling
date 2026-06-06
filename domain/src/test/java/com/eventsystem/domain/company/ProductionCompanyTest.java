package com.eventsystem.domain.company;

import com.eventsystem.domain.domainexceptions.CompanyDomainException;
import com.eventsystem.domain.member.MemberId;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionCompanyTest {

    @Test
    void appointAndRemoveOwnerReassignsSubTree() {
        MemberId founder = MemberId.random();
        MemberId ownerA = MemberId.random();
        MemberId ownerB = MemberId.random();
        MemberId manager = MemberId.random();

        ProductionCompany company = ProductionCompany.create(founder, "Alpha", "desc", 4.5);

        company.appointOwner(founder, ownerA);
        company.acceptAppointment(ownerA);
        company.appointOwner(ownerA, ownerB);
        company.acceptAppointment(ownerB);
        company.appointManager(ownerB, manager, Set.of(Permission.VIEW_PURCHASE_HISTORY));
        company.acceptAppointment(manager);

        assertThat(company.isOwner(ownerB)).isTrue();
        assertThat(company.isManager(manager)).isTrue();

        // When removing ownerA, ownerB should be reassigned to founder (ownerA's appointer)
        company.removeOwner(founder, ownerA);

        assertThat(company.isOwner(ownerA)).isFalse();
        // ownerB and manager should still exist but reassigned
        assertThat(company.isOwner(ownerB)).isTrue();
        assertThat(company.isManager(manager)).isTrue();
    }

    @Test
    void managerPermissionCanBeChangedByOwningSubTreeOwner() {
        MemberId founder = MemberId.random();
        MemberId owner = MemberId.random();
        MemberId manager = MemberId.random();

        ProductionCompany company = ProductionCompany.create(founder, "Beta", "desc", 4.0);

        company.appointOwner(founder, owner);
        company.acceptAppointment(owner);
        company.appointManager(owner, manager, Set.of(Permission.VIEW_PURCHASE_HISTORY));
        company.acceptAppointment(manager);

        assertThat(company.hasPermission(manager, Permission.VIEW_PURCHASE_HISTORY)).isTrue();
        assertThat(company.hasPermission(manager, Permission.MODIFY_POLICIES)).isFalse();

        company.modifyManagerPermissions(owner, manager, Set.of(Permission.MODIFY_POLICIES));

        assertThat(company.hasPermission(manager, Permission.VIEW_PURCHASE_HISTORY)).isFalse();
        assertThat(company.hasPermission(manager, Permission.MODIFY_POLICIES)).isTrue();
    }

    @Test
    void managerCanAppointSubordinateManager() {
        MemberId founder = MemberId.random();
        MemberId manager1 = MemberId.random();
        MemberId manager2 = MemberId.random();

        ProductionCompany company = ProductionCompany.create(founder, "Manager Hierarchy", "desc", 4.5);

        company.appointManager(founder, manager1, Set.of(Permission.EVENT_INVENTORY_MANAGEMENT));
        company.acceptAppointment(manager1);
        company.appointManagerToManager(manager1, manager2, Set.of(Permission.VENUE_CONFIGURATION));
        company.acceptAppointment(manager2);

        assertThat(company.isManager(manager1)).isTrue();
        assertThat(company.isManager(manager2)).isTrue();
        assertThat(company.hasPermission(manager1, Permission.EVENT_INVENTORY_MANAGEMENT)).isTrue();
        assertThat(company.hasPermission(manager2, Permission.VENUE_CONFIGURATION)).isTrue();
    }

    @Test
    void removingManagerReassignsChildManagersToParent() {
        MemberId founder = MemberId.random();
        MemberId manager1 = MemberId.random();
        MemberId manager2 = MemberId.random();
        MemberId manager3 = MemberId.random();

        ProductionCompany company = ProductionCompany.create(founder, "Reassign Test", "desc", 4.2);

        company.appointManager(founder, manager1, Set.of(Permission.EVENT_INVENTORY_MANAGEMENT));
        company.acceptAppointment(manager1);
        company.appointManagerToManager(manager1, manager2, Set.of(Permission.VENUE_CONFIGURATION));
        company.acceptAppointment(manager2);
        company.appointManagerToManager(manager1, manager3, Set.of(Permission.VIEW_PURCHASE_HISTORY));
        company.acceptAppointment(manager3);

        // Remove manager1 - manager2 and manager3 should be reassigned to founder
        company.removeManager(founder, manager1);

        assertThat(company.isManager(manager1)).isFalse();
        // manager2 and manager3 should still exist and be reassigned to founder
        assertThat(company.isManager(manager2)).isTrue();
        assertThat(company.isManager(manager3)).isTrue();
    }

    @Test
    void duplicateAppointmentIsRejected() {
        MemberId founder = MemberId.random();
        MemberId target = MemberId.random();
        ProductionCompany company = ProductionCompany.create(founder, "Gamma", "desc", 3.8);

        company.appointOwner(founder, target);

        assertThatThrownBy(() -> company.appointManager(founder, target, Set.of(Permission.MODIFY_POLICIES)))
                .isInstanceOf(CompanyDomainException.class)
                .hasMessageContaining("already has an appointment");
    }
    @Test
    void stateTransitionsAndRequireActive() {
        MemberId founder = MemberId.random();
        ProductionCompany company = ProductionCompany.create(founder, "Test", "desc", 5.0);

        // בדיקת השהיית חברה
        company.suspend();
        assertThat(company.status()).isEqualTo(CompanyStatus.SUSPENDED);

        // בדיקה שפעולות נחסמות כשהחברה לא פעילה 
        assertThatThrownBy(() -> company.updateName("New Name"))
            .isInstanceOf(CompanyDomainException.class)
            .hasMessageContaining("company is not active");
            
        // אין הרשאות כשהחברה מושהית
        assertThat(company.hasPermission(founder, Permission.MODIFY_POLICIES)).isFalse();

        // בדיקת פתיחה מחדש
        company.reopen();
        assertThat(company.status()).isEqualTo(CompanyStatus.ACTIVE);
        assertThat(company.hasPermission(founder, Permission.MODIFY_POLICIES)).isTrue();

        // בדיקת סגירת אדמין
        company.adminClose();
        assertThat(company.status()).isEqualTo(CompanyStatus.ADMIN_CLOSED);
        assertThatThrownBy(() -> company.reopen())
            .isInstanceOf(CompanyDomainException.class)
            .hasMessageContaining("admin-closed company cannot be reopened");

        // בדיקת סגירה לצמיתות (Terminate)
        ProductionCompany company2 = ProductionCompany.create(founder, "Test2", "desc", 5.0);
        company2.terminate();
        assertThat(company2.status()).isEqualTo(CompanyStatus.TERMINATED);
        assertThatThrownBy(() -> company2.suspend())
            .isInstanceOf(CompanyDomainException.class)
            .hasMessageContaining("terminated company cannot be suspended");
        assertThatThrownBy(() -> company2.reopen())
            .isInstanceOf(CompanyDomainException.class)
            .hasMessageContaining("terminated company cannot be reopened");
    }

    @Test
    void updateCompanyDetailsValidations() {
        MemberId founder = MemberId.random();
        ProductionCompany company = ProductionCompany.create(founder, "Name", "Desc", 3.0);

        
        company.updateName("New Name");
        assertThat(company.companyDetails().name()).isEqualTo("New Name");

        company.updateDescription("New Desc");
        assertThat(company.companyDetails().description()).isEqualTo("New Desc");

        company.updateRating(4.5);
        assertThat(company.companyDetails().rating()).isEqualTo(4.5);

        
        assertThatThrownBy(() -> company.updateName(""))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> company.updateDescription(""))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> company.updateRating(6.0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> company.updateRating(-1.0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void relinquishOwnership_throwsForFounder_butWorksForNormalOwner() {
        MemberId founder = MemberId.random();
        MemberId owner = MemberId.random();
        ProductionCompany company = ProductionCompany.create(founder, "Name", "Desc", 3.0);
        
        
        assertThatThrownBy(() -> company.relinquishOwnership(founder))
            .isInstanceOf(CompanyDomainException.class)
            .hasMessageContaining("founder cannot relinquish ownership");

        // בעלים רגיל כן יכול לוותר
        company.appointOwner(founder, owner);
        company.acceptAppointment(owner);
        company.relinquishOwnership(owner);
        
        assertThat(company.isOwner(owner)).isFalse();
        
    }
    @Test
    void inactiveCompany_BlocksPermissionsAndAcceptance() {
        MemberId founder = MemberId.random();
        MemberId target = MemberId.random();
        ProductionCompany company = ProductionCompany.create(founder, "Test", "desc", 5.0);
        
        company.appointOwner(founder, target); 
        company.suspend(); 
        
        
        assertThat(company.hasPermission(founder, Permission.MODIFY_POLICIES)).isFalse();
        
        
        assertThatThrownBy(() -> company.acceptAppointment(target))
            .isInstanceOf(CompanyDomainException.class)
            .hasMessageContaining("company is not active");
    }

    @Test
    void queriesReturnFalseForUnknownMembers() {
        MemberId founder = MemberId.random();
        ProductionCompany company = ProductionCompany.create(founder, "Test", "desc", 5.0);
        
        MemberId unknown = MemberId.random();
        
        // validate that the aggregate correctly returns false for unknown members in isOwner/isManager/hasPermission
        assertThat(company.isOwner(unknown)).isFalse();
        assertThat(company.isManager(unknown)).isFalse();
        assertThat(company.hasPermission(unknown, Permission.VIEW_PURCHASE_HISTORY)).isFalse();
        
        
        assertThat(company.getAppointmentSubTree(founder)).containsExactly(founder);
    }
}
