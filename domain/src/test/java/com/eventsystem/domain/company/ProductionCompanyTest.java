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
}
