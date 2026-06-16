package com.eventsystem.infrastructure.persistence.springrepostests;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.policy.purchase.PurchasePolicy;
import com.eventsystem.domain.policy.purchase.PurchasePolicyId;
import com.eventsystem.infrastructure.persistence.springrepos.PostgresPurchasePolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EntityScan(basePackages = "com.eventsystem.domain")
@Import(PostgresPurchasePolicyRepository.class)
class PostgresPurchasePolicyRepositoryTest extends BasePostgresTest {

    @Autowired
    private PostgresPurchasePolicyRepository purchasePolicyRepository;

    @Autowired
    private EntityManager em;

    private CompanyId companyId;

    @BeforeEach
    void setUp() {
        companyId = new CompanyId("COMP-PURCHASE-123");
    }

    @Test
    void saveAndFindById_savesPolicyCorrectly() {
        // Arrange - יצירת פוליסה שחלה על כל החברה (ולכן היא נחשבת פעילה)
        PurchasePolicy policy = PurchasePolicy.newAllowAllPolicy(companyId, "Global Allow Policy");
        policy.setCompanyWide();

        purchasePolicyRepository.save(policy);
        em.flush(); em.clear();

        // Act
        Optional<PurchasePolicy> foundOpt = purchasePolicyRepository.findById(policy.id());

        // Assert
        assertThat(foundOpt).isPresent();
        assertThat(foundOpt.get().isActive()).isTrue();
        assertThat(foundOpt.get().policyName()).isEqualTo("Global Allow Policy");
    }

    @Test
    void findById_returnsEmptyWhenNotFound() {
        // Act
        Optional<PurchasePolicy> foundOpt = purchasePolicyRepository.findById(PurchasePolicyId.random());

        // Assert
        assertThat(foundOpt).isEmpty();
    }

    @Test
    void update_modifiesExistingPolicyInDatabase() {
        // Arrange - יצירת פוליסה לא פעילה (ללא Scope)
        PurchasePolicy policy = PurchasePolicy.newAllowAllPolicy(companyId, "Temp Policy");
        purchasePolicyRepository.save(policy);
        em.flush(); em.clear();

        // Act - שליפה, שינוי ה-Scope לעדכון סטטוס והפעלה
        PurchasePolicy savedPolicy = purchasePolicyRepository.findById(policy.id()).orElseThrow();
        savedPolicy.setNameTo("Updated Policy");
        savedPolicy.setCompanyWide(); // הופך את isActive ל-true
        purchasePolicyRepository.save(savedPolicy);
        em.flush(); em.clear();

        // Assert
        PurchasePolicy updatedPolicy = purchasePolicyRepository.findById(policy.id()).orElseThrow();
        assertThat(updatedPolicy.isActive()).isTrue();
        assertThat(updatedPolicy.policyName()).isEqualTo("Updated Policy");
    }

    @Test
    void deleteById_and_existsById_workCorrectly() {
        // Arrange
        PurchasePolicy policy = PurchasePolicy.newAllowAllPolicy(companyId, "To Be Deleted");
        policy.setCompanyWide();
        purchasePolicyRepository.save(policy);
        em.flush(); em.clear();

        PurchasePolicyId policyId = policy.id();

        // Assert - בדיקה שהפוליסה קיימת
        assertThat(purchasePolicyRepository.existsById(policyId)).isTrue();

        // Act - מחיקה
        purchasePolicyRepository.deleteById(policyId);
        em.flush(); em.clear();

        // Assert - בדיקה שהפוליסה נמחקה
        assertThat(purchasePolicyRepository.existsById(policyId)).isFalse();
        assertThat(purchasePolicyRepository.findById(policyId)).isEmpty();
    }

    @Test
    void findByCompanyId_returnsPoliciesForSpecificCompany() {
        // Arrange - החברה שלנו
        PurchasePolicy myPolicy = PurchasePolicy.newAllowAllPolicy(companyId, "My Company Policy");
        myPolicy.setCompanyWide();
        purchasePolicyRepository.save(myPolicy);

        // Arrange - חברה אחרת
        CompanyId otherCompany = new CompanyId("COMP-OTHER-999");
        PurchasePolicy otherPolicy = PurchasePolicy.newAllowAllPolicy(otherCompany, "Other Company Policy");
        otherPolicy.setCompanyWide();
        purchasePolicyRepository.save(otherPolicy);

        em.flush(); em.clear();

        // Act
        List<PurchasePolicy> results = purchasePolicyRepository.findByCompanyId(companyId);

        // Assert
        assertThat(results).hasSize(1);
        assertThat(results.get(0).companyId()).isEqualTo(companyId);
    }

    @Test
    void findActiveByCompanyId_returnsOnlyActivePolicies() {
        // Arrange - פוליסה פעילה
        PurchasePolicy activePolicy = PurchasePolicy.newAllowAllPolicy(companyId, "Active Policy");
        activePolicy.setCompanyWide();
        purchasePolicyRepository.save(activePolicy);

        // Arrange - פוליסה לא פעילה (ללא Scope)
        PurchasePolicy inactivePolicy = PurchasePolicy.newAllowAllPolicy(companyId, "Inactive Policy");
        purchasePolicyRepository.save(inactivePolicy);

        em.flush(); em.clear();

        // Act
        List<PurchasePolicy> results = purchasePolicyRepository.findActiveByCompanyId(companyId);

        // Assert
        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo(activePolicy.id());
        assertThat(results.get(0).isActive()).isTrue();
    }

    @Test
    void findApplicableToEvent_returnsMatchingPoliciesAndTriggersLambda() {
        // Arrange
        EventId eventId = new EventId("EVT-111");
        PurchasePolicy policy = PurchasePolicy.newAllowAllPolicy(companyId, "Event Policy");
        policy.activateForEvent(eventId); // מפעיל את הפוליסה ספציפית לאירוע הזה
        purchasePolicyRepository.save(policy);

        em.flush(); em.clear();

        // Act
        List<PurchasePolicy> results = purchasePolicyRepository.findApplicableToEvent(eventId);

        // Assert
        assertThat(results).isNotEmpty();
        assertThat(results.get(0).id()).isEqualTo(policy.id());
    }

    @Test
    void findApplicableToPurchase_returnsMatchingPoliciesAndTriggersLambda() {
        // Arrange
        EventId eventId = new EventId("EVT-PURCHASE-222");
        PurchasePolicy policy = PurchasePolicy.newAllowAllPolicy(companyId, "Purchase Policy");
        policy.activateForEvent(eventId);
        purchasePolicyRepository.save(policy);

        em.flush(); em.clear();

        // Act
        List<PurchasePolicy> results = purchasePolicyRepository.findApplicableToPurchase(companyId, eventId);

        // Assert
        assertThat(results).isNotEmpty();
        assertThat(results.get(0).id()).isEqualTo(policy.id());
    }
}