package com.eventsystem.domain.company;

import com.eventsystem.domain.member.MemberId;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OwnerNodeTest {

    @Test
    void constructorsAndGetters() {
        MemberId member = MemberId.random();
        MemberId appointer = MemberId.random();
        
        OwnerNode node1 = new OwnerNode(member, appointer);
        assertThat(node1.memberId()).isEqualTo(member);
        assertThat(node1.appointerId()).isEqualTo(appointer);
        assertThat(node1.isAccepted()).isTrue();
        
        OwnerNode node2 = new OwnerNode(member, appointer, false);
        assertThat(node2.isAccepted()).isFalse();
        node2.accept();
        assertThat(node2.isAccepted()).isTrue();
    }

    @Test
    void manageChildren() {
        OwnerNode parent = new OwnerNode(MemberId.random(), null);
        
        // הוספה והסרה של בעלים תחתיו
        MemberId childOwnerId = MemberId.random();
        OwnerNode childOwner = new OwnerNode(childOwnerId, parent.memberId());
        parent.addOwner(childOwner);
        assertThat(parent.appointedOwners()).containsExactly(childOwner);
        assertThat(parent.removeOwner(childOwnerId)).isPresent();
        assertThat(parent.removeOwner(MemberId.random())).isEmpty();

        // הוספה והסרה של מנהלים תחתיו
        MemberId childManagerId = MemberId.random();
        ManagerNode childManager = new ManagerNode(childManagerId, parent.memberId(), Set.of(Permission.VIEW_PURCHASE_HISTORY));
        parent.addManager(childManager);
        assertThat(parent.appointedManagers()).containsExactly(childManager);
        assertThat(parent.removeManager(childManagerId)).isPresent();
        assertThat(parent.removeManager(MemberId.random())).isEmpty();
    }
    
    @Test
    void nullValidations() {
        assertThatThrownBy(() -> new OwnerNode(null, MemberId.random())).isInstanceOf(NullPointerException.class);
    }
}