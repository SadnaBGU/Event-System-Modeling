package com.eventsystem.domain.company;

import com.eventsystem.domain.member.MemberId;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionCompanyTest {

    @Test
    void appointAndRemoveOwnerCascadesSubTree() {
        MemberId founder = MemberId.random();
        MemberId ownerA = MemberId.random();
        MemberId ownerB = MemberId.random();
        MemberId manager = MemberId.random();

        ProductionCompany company = ProductionCompany.create(founder, "Alpha", "desc", 4.5);

        company.appointOwner(founder, ownerA);
        company.appointOwner(ownerA, ownerB);
        company.appointManager(ownerB, manager, Set.of(Permission.VIEW_PURCHASE_HISTORY));

        assertThat(company.isOwner(ownerB)).isTrue();
        assertThat(company.isManager(manager)).isTrue();

        company.removeOwner(founder, ownerA);

        assertThat(company.isOwner(ownerA)).isFalse();
        assertThat(company.isOwner(ownerB)).isFalse();
        assertThat(company.isManager(manager)).isFalse();
    }

    @Test
    void managerPermissionCanBeChangedByOwningSubTreeOwner() {
        MemberId founder = MemberId.random();
        MemberId owner = MemberId.random();
        MemberId manager = MemberId.random();

        ProductionCompany company = ProductionCompany.create(founder, "Beta", "desc", 4.0);

        company.appointOwner(founder, owner);
        company.appointManager(owner, manager, Set.of(Permission.VIEW_PURCHASE_HISTORY));

        assertThat(company.hasPermission(manager, Permission.VIEW_PURCHASE_HISTORY)).isTrue();
        assertThat(company.hasPermission(manager, Permission.MODIFY_POLICIES)).isFalse();

        company.modifyManagerPermissions(owner, manager, Set.of(Permission.MODIFY_POLICIES));

        assertThat(company.hasPermission(manager, Permission.VIEW_PURCHASE_HISTORY)).isFalse();
        assertThat(company.hasPermission(manager, Permission.MODIFY_POLICIES)).isTrue();
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
