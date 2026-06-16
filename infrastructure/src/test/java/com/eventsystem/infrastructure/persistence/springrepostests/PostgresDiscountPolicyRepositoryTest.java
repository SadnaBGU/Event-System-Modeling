package com.eventsystem.infrastructure.persistence.springrepostests;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.policy.discount.Discount;
import com.eventsystem.domain.policy.discount.DiscountPolicy;
import com.eventsystem.domain.policy.discount.DiscountPolicyId;
import com.eventsystem.domain.policy.rule.basic.AlwaysTruePolicy;
import com.eventsystem.infrastructure.persistence.springrepos.PostgresDiscountPolicyRepository; // וודא שזה שם ה-Repository שלך
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EntityScan(basePackages = "com.eventsystem.domain")
@Import(PostgresDiscountPolicyRepository.class)
class PostgresDiscountPolicyRepositoryTest extends BasePostgresTest {

    @Autowired
    private PostgresDiscountPolicyRepository discountPolicyRepository;

    @Autowired
    private EntityManager em;

    private CompanyId companyId;

    @BeforeEach
    void setUp() {
        companyId = new CompanyId("COMP-555");
    }

   @Test
    void saveAndFindById_savesDiscountsCorrectly() {
        // Arrange
        Discount discount = Discount.GeneralDiscount("Summer Sale", BigDecimal.valueOf(20), null);
        DiscountPolicy policy = DiscountPolicy.clearScope(companyId);
        policy.addDiscount(discount);
        
        // --- התיקון: הגדרת הפוליסה כחלה על כל החברה לפני ה-activate ---
        policy.setCompanyWide(); 
        policy.activate();
        // -----------------------------------------------------------

        discountPolicyRepository.save(policy);
        em.flush();
        em.clear();

        // Act
        Optional<DiscountPolicy> foundOpt = discountPolicyRepository.findById(policy.id());

        // Assert
        assertThat(foundOpt).isPresent();
        DiscountPolicy found = foundOpt.get();
        
        assertThat(found.discounts()).hasSize(1);
        assertThat(found.discounts().get(0).getDiscountName()).isEqualTo("Summer Sale");
        assertThat(found.isActive()).isTrue();
    }
    @Test
    void findById_returnsEmptyWhenNotFound() {
        Optional<DiscountPolicy> foundOpt = discountPolicyRepository.findById(DiscountPolicyId.random());
        assertThat(foundOpt).isEmpty();
    }

    // 3. עדכון נתונים קיימים
    @Test
    void update_modifiesExistingPolicyInDatabase() {
        // יצירה ראשונית (לא פעיל)
        DiscountPolicy policy = DiscountPolicy.clearScope(companyId);
        policy.addDiscount(Discount.GeneralDiscount("D1", BigDecimal.TEN, null));
        discountPolicyRepository.save(policy);
        em.flush(); em.clear();

        // עדכון
        DiscountPolicy savedPolicy = discountPolicyRepository.findById(policy.id()).orElseThrow();
        savedPolicy.setCompanyWide();
        savedPolicy.activate(); // משנה סטטוס לפעיל
        discountPolicyRepository.save(savedPolicy);
        em.flush(); em.clear();

        // בדיקה
        DiscountPolicy updatedPolicy = discountPolicyRepository.findById(policy.id()).orElseThrow();
        assertThat(updatedPolicy.isActive()).isTrue();
    }

    // 4. בדיקת מחיקה
    @Test
    void deleteById_removesPolicyFromDatabase() {
        DiscountPolicy policy = DiscountPolicy.clearScope(companyId);
        policy.addDiscount(Discount.GeneralDiscount("D1", BigDecimal.TEN, null));
        discountPolicyRepository.save(policy);
        em.flush(); em.clear();

        discountPolicyRepository.deleteById(policy.id());
        em.flush(); em.clear();

        assertThat(discountPolicyRepository.findById(policy.id())).isEmpty();
        assertThat(discountPolicyRepository.existsById(policy.id())).isFalse();
    }

    // 5. שאילתת חיפוש לפי CompanyId
    @Test
    void findByCompanyId_returnsOnlyMatchingCompany() {
        // פוליסה לחברה שלנו
        DiscountPolicy policy1 = DiscountPolicy.clearScope(companyId);
        policy1.addDiscount(Discount.GeneralDiscount("D1", BigDecimal.TEN, null));
        discountPolicyRepository.save(policy1);

        // פוליסה לחברה אחרת
        CompanyId otherCompany = new CompanyId("COMP-999");
        DiscountPolicy policy2 = DiscountPolicy.clearScope(otherCompany);
        policy2.addDiscount(Discount.GeneralDiscount("D2", BigDecimal.TEN, null));
        discountPolicyRepository.save(policy2);
        em.flush(); em.clear();

        List<DiscountPolicy> results = discountPolicyRepository.findByCompanyId(companyId);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).companyId()).isEqualTo(companyId);
    }

    // 6. שאילתת מציאת פוליסות פעילות בלבד
    @Test
    void findActive_returnsOnlyActivePolicies() {
        // פוליסה פעילה
        DiscountPolicy activePolicy = DiscountPolicy.clearScope(companyId);
        activePolicy.addDiscount(Discount.GeneralDiscount("Active", BigDecimal.TEN, null));
        activePolicy.setCompanyWide();
        activePolicy.activate();
        discountPolicyRepository.save(activePolicy);

        // פוליסה לא פעילה
        DiscountPolicy inactivePolicy = DiscountPolicy.clearScope(companyId);
        inactivePolicy.addDiscount(Discount.GeneralDiscount("Inactive", BigDecimal.TEN, null));
        // לא קוראים ל-activate()
        discountPolicyRepository.save(inactivePolicy);
        em.flush(); em.clear();

        List<DiscountPolicy> results = discountPolicyRepository.findActive();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo(activePolicy.id());
        assertThat(results.get(0).isActive()).isTrue();
    }

    // 7. בדיקת JSONB / חיפוש לפי אירוע (EventId)
    @Test
    void findApplicableToEvent_returnsMatchingPolicies() {
        EventId eventId = new EventId("EVT-123");
        
        DiscountPolicy policy = DiscountPolicy.clearScope(companyId);
        policy.addDiscount(Discount.GeneralDiscount("Event Discount", BigDecimal.TEN, null));
        policy.activateForEvent(eventId);
        policy.activate();
        discountPolicyRepository.save(policy);
        em.flush(); em.clear();

        List<DiscountPolicy> results = discountPolicyRepository.findApplicableToEvent(eventId);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo(policy.id());
    }
    @Test
    void findActiveWithVisibleDiscounts_returnsMatchingPolicies() {
        // Arrange
        DiscountPolicy policy = DiscountPolicy.clearScope(companyId);
        Discount discount = Discount.GeneralDiscount("Visible Discount", BigDecimal.valueOf(15), null);
        policy.addDiscount(discount);
        
        // התיקון: הגדרה שהפוליסה חלה על כל החברה לפני ההפעלה
        policy.setCompanyWide(); 
        policy.activate();
        
        discountPolicyRepository.save(policy);
        em.flush(); em.clear();

        // Act
        List<DiscountPolicy> results = discountPolicyRepository.findActiveWithVisibleDiscounts();

        // Assert
        assertThat(results).isNotEmpty();
        assertTrue(results.stream().anyMatch(p -> p.id().equals(policy.id())));
    }

    @Test
    void findActiveByCompanyId_returnsOnlyActiveForSpecificCompany() {
        // Arrange - פוליסה פעילה של החברה שלנו
        DiscountPolicy activeMine = DiscountPolicy.clearScope(companyId);
        activeMine.addDiscount(Discount.GeneralDiscount("Active Mine", BigDecimal.TEN, null));
        
        // התיקון: הגדרה שהפוליסה חלה על כל החברה לפני ההפעלה
        activeMine.setCompanyWide(); 
        activeMine.activate();
        
        discountPolicyRepository.save(activeMine);

        // Arrange - פוליסה לא פעילה של החברה שלנו
        DiscountPolicy inactiveMine = DiscountPolicy.clearScope(companyId);
        inactiveMine.addDiscount(Discount.GeneralDiscount("Inactive Mine", BigDecimal.TEN, null));
        discountPolicyRepository.save(inactiveMine);

        // Arrange - פוליסה פעילה של חברה אחרת
        CompanyId otherCompany = new CompanyId("COMP-OTHER");
        DiscountPolicy activeOther = DiscountPolicy.clearScope(otherCompany);
        activeOther.addDiscount(Discount.GeneralDiscount("Active Other", BigDecimal.TEN, null));
        
        // התיקון: הגדרה שהפוליסה חלה על כל החברה לפני ההפעלה
        activeOther.setCompanyWide(); 
        activeOther.activate();
        
        discountPolicyRepository.save(activeOther);

        em.flush(); em.clear();

        // Act
        List<DiscountPolicy> results = discountPolicyRepository.findActiveByCompanyId(companyId);

        // Assert
        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo(activeMine.id());
    }

    @Test
    void findApplicableToPurchase_returnsMatchingPoliciesAndTriggersLambda() {
        // Arrange
        EventId eventId = new EventId("EVT-PURCHASE-99");

        DiscountPolicy policy = DiscountPolicy.clearScope(companyId);
        policy.addDiscount(Discount.GeneralDiscount("Purchase Match", BigDecimal.valueOf(25), null));
        // מקשרים את הפוליסה ספציפית לאירוע הזה כדי שה-Lambda של הסינון תתפוס אותו
        policy.activateForEvent(eventId); 
        policy.activate();
        discountPolicyRepository.save(policy);

        em.flush(); em.clear();

        // Act - קריאה לפונקציה שמכילה את פונקציית הלמבדה
        List<DiscountPolicy> results = discountPolicyRepository.findApplicableToPurchase(companyId, eventId);

        // Assert
        assertThat(results).isNotEmpty();
        assertThat(results.get(0).id()).isEqualTo(policy.id());
    }
        
}
