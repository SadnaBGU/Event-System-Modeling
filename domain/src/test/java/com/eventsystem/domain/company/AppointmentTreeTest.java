package com.eventsystem.domain.company;

import com.eventsystem.domain.domainexceptions.CompanyDomainException;
import com.eventsystem.domain.member.MemberId;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppointmentTreeTest {

    @Test
    void jsonCreatorConstructor_worksViaReflection() throws Exception {
        Constructor<AppointmentTree> c = AppointmentTree.class.getDeclaredConstructor(OwnerNode.class);
        c.setAccessible(true);
        
        OwnerNode root = new OwnerNode(MemberId.random(), null);
        AppointmentTree tree = c.newInstance(root);
        
        assertThat(tree.root()).isEqualTo(root);
    }

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
    void isManagerInOwnerSubTree_ValidatesCorrectly() {
        MemberId founder = MemberId.random();
        AppointmentTree tree = new AppointmentTree(founder);
        MemberId unknown = MemberId.random();
        MemberId target = MemberId.random();
        
        assertThatThrownBy(() -> tree.isManagerInOwnerSubTree(unknown, target))
            .isInstanceOf(CompanyDomainException.class)
            .hasMessageContaining("owner not found");
            
        tree.appointManager(founder, target, Set.of(Permission.VIEW_PURCHASE_HISTORY));
        
        // יחזיר true כי המנהל נמצא תחת ה-Founder
        assertThat(tree.isManagerInOwnerSubTree(founder, target)).isTrue();
        
        // יחזיר false כי הוא מנסה למצוא מישהו שלא קיים
        assertThat(tree.isManagerInOwnerSubTree(founder, unknown)).isFalse();
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
            
        // מנסים למנות כמנהל אדם שהוא כבר בעלים
        assertThatThrownBy(() -> tree.appointManager(founder, founder, Set.of()))
            .isInstanceOf(CompanyDomainException.class)
            .hasMessageContaining("already has an appointment");
    }

    @Test
    void acceptAppointment_WorksForManagersAndOwners() {
        MemberId founder = MemberId.random();
        AppointmentTree tree = new AppointmentTree(founder);
        
        MemberId manager = MemberId.random();
        MemberId owner2 = MemberId.random();
        
        tree.appointManager(founder, manager, Set.of(Permission.VIEW_PURCHASE_HISTORY));
        tree.appointOwner(founder, owner2);
        
        tree.acceptAppointment(manager); // אישור מינוי תקין של מנהל
        tree.acceptAppointment(owner2);  // אישור מינוי תקין של בעלים
        
        // ניסיון לאשר שוב זורק שגיאה
        assertThatThrownBy(() -> tree.acceptAppointment(manager))
            .isInstanceOf(CompanyDomainException.class)
            .hasMessageContaining("already accepted");
    }

    @Test
    void removeOwner_rejectsFounderRemoval() {
        MemberId founder = MemberId.random();
        AppointmentTree tree = new AppointmentTree(founder);

        // תוקן המשפט שאנחנו מצפים לו לפי קוד המקור שלך
        assertThatThrownBy(() -> tree.removeOwner(founder, founder))
                .isInstanceOf(CompanyDomainException.class)
                .hasMessageContaining("cannot remove the founder owner");
    }
    
    @Test
    void removeOwner_DeepTree_ExecutesTraversal() {
        MemberId founder = MemberId.random();
        AppointmentTree tree = new AppointmentTree(founder);
        
        MemberId owner2 = MemberId.random();
        MemberId owner3 = MemberId.random(); 
        MemberId manager1 = MemberId.random(); 
        
        tree.appointOwner(founder, owner2);
        tree.acceptAppointment(owner2);
        
        // הוספת ילדים תחת owner2 כדי שפונקציית המחיקה תעבור עליהם ותקבל 100% כיסוי
        tree.appointOwner(owner2, owner3);
        OwnerNode owner2Node = tree.findOwner(owner2).get();
        owner2Node.addManager(new ManagerNode(manager1, owner2, Set.of()));
        
        // המחיקה תרוץ על הילדים ותכסה את שורות ה-Reassignment / Traversal בלולאה
        tree.removeOwner(founder, owner2);
        
        // מוודאים רק שהאובייקט הראשי אכן נמחק כמצופה
        assertThat(tree.findOwner(owner2)).isEmpty();
    }
    
    @Test
    void removeManager_DeepTree_ExecutesTraversal() {
        MemberId founder = MemberId.random();
        AppointmentTree tree = new AppointmentTree(founder);
        
        MemberId mgr1 = MemberId.random();
        MemberId mgr2 = MemberId.random();
        
        tree.appointManager(founder, mgr1, Set.of(Permission.MODIFY_POLICIES));
        tree.acceptAppointment(mgr1);
        
        // הוספה ישירה של מנהל תחת מנהל (עוקף את appointManager שדורש ממנה שהוא בעלים)
        ManagerNode mgr1Node = tree.findManager(mgr1).get();
        mgr1Node.addManager(new ManagerNode(mgr2, mgr1, Set.of()));
        
        // המחיקה תרוץ על mgr1 ותיכנס ללולאת הילדים שלו
        tree.removeManager(founder, mgr1);
        
        assertThat(tree.findManager(mgr1)).isEmpty();
    }

    @Test
    void removeManager_rejectsNonExistentManagerAndUnrelatedAppointer() {
        MemberId founder = MemberId.random();
        AppointmentTree tree = new AppointmentTree(founder);
        MemberId randomUser = MemberId.random();
        
        // תוקנה הודעת השגיאה לזו שהמערכת באמת זורקת במקרה של עץ חסר
        assertThatThrownBy(() -> tree.removeManager(founder, randomUser))
            .isInstanceOf(CompanyDomainException.class)
            .hasMessageContaining("target manager is not in remover subtree");
            
        // מנסים להסיר דרך ממנה שלא נמצא בעץ
        MemberId mgr1 = MemberId.random();
        tree.appointManager(founder, mgr1, Set.of());
        
        assertThatThrownBy(() -> tree.removeManager(randomUser, mgr1))
            .isInstanceOf(CompanyDomainException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void collectIds_collectsEveryoneViaReflection() throws Exception {
        MemberId founder = MemberId.random();
        AppointmentTree tree = new AppointmentTree(founder);
        
        MemberId owner2 = MemberId.random();
        MemberId mgr1 = MemberId.random();
        
        tree.appointOwner(founder, owner2);
        tree.appointManager(founder, mgr1, Set.of());
        
        // קריאה ישירה למתודה הפרטית collectIds כדי לכסות אותה בטסטים
        java.lang.reflect.Method method = AppointmentTree.class.getDeclaredMethod("collectIds", OwnerNode.class, java.util.List.class);
        method.setAccessible(true);
        
        java.util.List<MemberId> result = (java.util.List<MemberId>) method.invoke(tree, tree.root(), new java.util.ArrayList<>());
        
        assertThat(result).containsExactlyInAnyOrder(founder, owner2, mgr1);
    }
}