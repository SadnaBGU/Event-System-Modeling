package com.eventsystem.domain.company;

import com.eventsystem.domain.member.MemberId;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManagerNodeTest {

    @Test
    void constructorsAndGetters() {
        MemberId member = MemberId.random();
        MemberId appointer = MemberId.random();
        
        // בדיקת הבנאי עם 3 פרמטרים
        ManagerNode node1 = new ManagerNode(member, appointer, Set.of(Permission.VIEW_PURCHASE_HISTORY));
        assertThat(node1.memberId()).isEqualTo(member);
        assertThat(node1.appointerId()).isEqualTo(appointer);
        assertThat(node1.isAccepted()).isTrue();
        assertThat(node1.permissions()).containsExactly(Permission.VIEW_PURCHASE_HISTORY);
        assertThat(node1.appointedManagers()).isEmpty();

        // בדיקת הבנאי עם 4 פרמטרים 
        ManagerNode node2 = new ManagerNode(member, appointer, Set.of(Permission.MODIFY_POLICIES), false);
        assertThat(node2.isAccepted()).isFalse();
        node2.accept();
        assertThat(node2.isAccepted()).isTrue();
    }

    @Test
    void emptyPermissions_CreatesEmptyEnumSet() {
        ManagerNode node = new ManagerNode(MemberId.random(), MemberId.random(), Set.of());
        assertThat(node.permissions()).isEmpty();
    }

    @Test
    void jsonCreatorConstructor_worksViaReflection() throws Exception {
        Constructor<ManagerNode> c = ManagerNode.class.getDeclaredConstructor(
                MemberId.class, MemberId.class, Set.class, boolean.class, List.class);
        c.setAccessible(true);
        
        MemberId m1 = MemberId.random();
        ManagerNode node = c.newInstance(m1, MemberId.random(), Set.of(), true, null);
        
        assertThat(node.memberId()).isEqualTo(m1);
        assertThat(node.appointedManagers()).isEmpty();
    }

    @Test
    void permissionsManagement() {
        ManagerNode node = new ManagerNode(MemberId.random(), MemberId.random(), Set.of(Permission.VIEW_PURCHASE_HISTORY));
        
        assertThat(node.hasPermission(Permission.VIEW_PURCHASE_HISTORY)).isTrue();
        assertThat(node.hasPermission(Permission.MODIFY_POLICIES)).isFalse();
        
        node.replacePermissions(Set.of(Permission.MODIFY_POLICIES));
        
        assertThat(node.hasPermission(Permission.VIEW_PURCHASE_HISTORY)).isFalse();
        assertThat(node.hasPermission(Permission.MODIFY_POLICIES)).isTrue();
        
        assertThatThrownBy(() -> node.replacePermissions(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void manageChildManagers() {
        ManagerNode parent = new ManagerNode(MemberId.random(), MemberId.random(), Set.of(Permission.VIEW_PURCHASE_HISTORY));
        MemberId childId = MemberId.random();
        ManagerNode child = new ManagerNode(childId, parent.memberId(), Set.of(Permission.VENUE_CONFIGURATION));

        parent.addManager(child);
        assertThat(parent.appointedManagers()).containsExactly(child);

        // הסרת מנהל קיים
        assertThat(parent.removeManager(childId)).isPresent().contains(child);
        assertThat(parent.appointedManagers()).isEmpty();

        // ניסיון הסרת מנהל שלא קיים 
        assertThat(parent.removeManager(MemberId.random())).isEmpty();
    }

    @Test
    void nullValidations() {
        MemberId validId = MemberId.random();
        Set<Permission> validPerms = Set.of(Permission.VIEW_PURCHASE_HISTORY);

        assertThatThrownBy(() -> new ManagerNode(null, validId, validPerms)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ManagerNode(validId, null, validPerms)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ManagerNode(validId, validId, null)).isInstanceOf(NullPointerException.class);
    }
}