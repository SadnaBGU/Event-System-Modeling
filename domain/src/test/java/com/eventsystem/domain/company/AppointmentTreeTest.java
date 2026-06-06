package com.eventsystem.domain.company;

import com.eventsystem.domain.domainexceptions.CompanyDomainException;
import com.eventsystem.domain.member.MemberId;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppointmentTreeTest {

    @Test
    void appointOwner_requiresAcceptedOwnerAsAppointer() {
        AppointmentTree tree = new AppointmentTree(MemberId.random());

        assertThatThrownBy(() -> tree.appointOwner(MemberId.random(), MemberId.random()))
                .isInstanceOf(CompanyDomainException.class)
                .hasMessageContaining("appointer is not an owner");
    }

    @Test
    void acceptAppointment_rejectsAlreadyAcceptedAndMissingTarget() {
        MemberId founder = MemberId.random();
        AppointmentTree tree = new AppointmentTree(founder);

        assertThatThrownBy(() -> tree.acceptAppointment(founder))
                .isInstanceOf(CompanyDomainException.class)
                .hasMessageContaining("already accepted");

        assertThatThrownBy(() -> tree.acceptAppointment(MemberId.random()))
                .isInstanceOf(CompanyDomainException.class)
                .hasMessageContaining("no pending appointment");
    }

    @Test
    void removeOwner_rejectsFounderRemoval_and_nonSubtreeRemoval() {
        MemberId founder = MemberId.random();
        MemberId ownerA = MemberId.random();
        MemberId ownerB = MemberId.random();

        AppointmentTree tree = new AppointmentTree(founder);
        tree.appointOwner(founder, ownerA);
        tree.acceptAppointment(ownerA);
        tree.appointOwner(founder, ownerB);
        tree.acceptAppointment(ownerB);

        assertThatThrownBy(() -> tree.removeOwner(founder, founder))
                .isInstanceOf(CompanyDomainException.class)
                .hasMessageContaining("cannot remove the founder owner");

        assertThatThrownBy(() -> tree.removeOwner(ownerA, ownerB))
                .isInstanceOf(CompanyDomainException.class)
                .hasMessageContaining("not in remover subtree");
    }

    @Test
    void removeManager_reassignsChildManagers_toParentOwner() {
        MemberId founder = MemberId.random();
        MemberId managerA = MemberId.random();
        MemberId managerB = MemberId.random();

        AppointmentTree tree = new AppointmentTree(founder);
        tree.appointManager(founder, managerA, Set.of(Permission.EVENT_INVENTORY_MANAGEMENT));
        tree.acceptAppointment(managerA);
        tree.appointManagerToManager(managerA, managerB, Set.of(Permission.VIEW_PURCHASE_HISTORY));
        tree.acceptAppointment(managerB);

        assertThat(tree.findManager(managerA)).isPresent();
        assertThat(tree.findManager(managerB)).isPresent();

        tree.removeManager(founder, managerA);

        assertThat(tree.findManager(managerA)).isEmpty();
        assertThat(tree.findManager(managerB)).isPresent();
        assertThat(tree.isManagerInOwnerSubTree(founder, managerB)).isTrue();
    }

    @Test
    void getAppointmentSubTree_and_getAllMemberIds_includeOwnersAndManagers() {
        MemberId founder = MemberId.random();
        MemberId owner = MemberId.random();
        MemberId manager = MemberId.random();

        AppointmentTree tree = new AppointmentTree(founder);
        tree.appointOwner(founder, owner);
        tree.acceptAppointment(owner);
        tree.appointManager(owner, manager, Set.of(Permission.VIEW_PURCHASE_HISTORY));
        tree.acceptAppointment(manager);

        assertThat(tree.getAppointmentSubTree(founder))
                .contains(founder, owner, manager);
        assertThat(tree.getAllMemberIds())
                .contains(founder, owner, manager);
        assertThat(tree.countOwners()).isEqualTo(2);
    }
    @Test
    void removeManager_throwsIfNotInSubtree() {
        MemberId founder = MemberId.random();
        MemberId manager1 = MemberId.random();
        AppointmentTree tree = new AppointmentTree(founder);
        
        
        assertThatThrownBy(() -> tree.removeManager(founder, manager1))
            .isInstanceOf(CompanyDomainException.class)
            .hasMessageContaining("target manager is not in remover subtree");
    }

   @Test
    void modifyPermissions_throwsIfNotInSubtree() {
        MemberId founder = MemberId.random();
        MemberId ownerA = MemberId.random();
        MemberId ownerB = MemberId.random();
        MemberId managerA = MemberId.random();

        AppointmentTree tree = new AppointmentTree(founder);

        // המייסד ממנה שני בעלים שונים (שני ענפים נפרדים)
        tree.appointOwner(founder, ownerA);
        tree.acceptAppointment(ownerA);
        tree.appointOwner(founder, ownerB);
        tree.acceptAppointment(ownerB);

        // בעלים א' ממנה מנהל משלו
        tree.appointManager(ownerA, managerA, Set.of(Permission.VIEW_PURCHASE_HISTORY));
        tree.acceptAppointment(managerA);

        
        assertThatThrownBy(() -> tree.modifyManagerPermissions(ownerB, managerA, Set.of()))
            .isInstanceOf(CompanyDomainException.class)
            .hasMessageContaining("manager is not in owner subtree");
    }
    
    @Test
    void validationTestsForValueObjects() {
        // test for CompanyId ,CompanyDetails
        assertThatThrownBy(() -> new CompanyId("  "))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CompanyDetails(" ", "desc", 4.0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CompanyDetails("name", "desc", 6.0))
            .isInstanceOf(IllegalArgumentException.class);
    }
    @Test
    void invalidActorsThrowExceptions() {
        AppointmentTree tree = new AppointmentTree(MemberId.random());
        MemberId unknown = MemberId.random();
        MemberId target = MemberId.random();

        
        assertThatThrownBy(() -> tree.appointManager(unknown, target, Set.of(Permission.VIEW_PURCHASE_HISTORY)))
            .isInstanceOf(CompanyDomainException.class)
            .hasMessageContaining("actor is not an owner");

        assertThatThrownBy(() -> tree.appointManagerToManager(unknown, target, Set.of(Permission.VIEW_PURCHASE_HISTORY)))
            .isInstanceOf(CompanyDomainException.class)
            .hasMessageContaining("appointer is not a manager");

        assertThatThrownBy(() -> tree.removeOwner(unknown, target))
            .isInstanceOf(CompanyDomainException.class)
            .hasMessageContaining("actor is not an owner");

        assertThatThrownBy(() -> tree.removeManager(unknown, target))
            .isInstanceOf(CompanyDomainException.class)
            .hasMessageContaining("actor is not an owner");

        assertThatThrownBy(() -> tree.isManagerInOwnerSubTree(unknown, target))
            .isInstanceOf(CompanyDomainException.class)
            .hasMessageContaining("owner not found");
    }

    @Test
    void ensureNotAlreadyAppointed_ManagerCannotBeAppointedAgain() {
        MemberId founder = MemberId.random();
        AppointmentTree tree = new AppointmentTree(founder);
        MemberId manager = MemberId.random();
        
        
        tree.appointManager(founder, manager, Set.of(Permission.VIEW_PURCHASE_HISTORY));
        
        // מנסים למנות כבעלים אדם שהוא כבר מנהל
        assertThatThrownBy(() -> tree.appointOwner(founder, manager))
            .isInstanceOf(CompanyDomainException.class)
            .hasMessageContaining("already has an appointment");
    }

    @Test
    void acceptAppointment_WorksForManagers() {
        MemberId founder = MemberId.random();
        AppointmentTree tree = new AppointmentTree(founder);
        MemberId manager = MemberId.random();
        
        
        tree.appointManager(founder, manager, Set.of(Permission.VIEW_PURCHASE_HISTORY));
        tree.acceptAppointment(manager); // אישור מינוי תקין של מנהל
        
        // ניסיון לאשר שוב זורק שגיאה
        assertThatThrownBy(() -> tree.acceptAppointment(manager))
            .isInstanceOf(CompanyDomainException.class)
            .hasMessageContaining("already accepted");
    }
}
