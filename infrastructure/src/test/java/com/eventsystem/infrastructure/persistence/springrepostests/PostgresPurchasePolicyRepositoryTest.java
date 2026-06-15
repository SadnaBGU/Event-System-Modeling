package com.eventsystem.infrastructure.persistence.springrepostests;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.policy.purchase.PurchasePolicy;
import com.eventsystem.domain.policy.rule.PolicyType;
import com.eventsystem.infrastructure.persistence.springrepos.PostgresPurchasePolicyRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EntityScan(basePackages = "com.eventsystem.domain")
// ודא שזה אכן השם של מחלקת ה-Repository שלך בתשתית:
@Import(PostgresPurchasePolicyRepository.class) 
class PostgresPurchasePolicyRepositoryTest extends BasePostgresTest {

    @Autowired
    private PostgresPurchasePolicyRepository policyRepository;

    @Autowired
    private EntityManager em;

    private PurchasePolicy testPolicy;
    private CompanyId companyId;

    @BeforeEach
    void setUp() {
        companyId = new CompanyId("COMP-555");
        
        // יצירת פוליסת רכישה בסיסית: אישור רכישה של מקסימום 5 כרטיסים
        testPolicy = PurchasePolicy.newMaxTicketPolicy(companyId, "VIP Ticket Limit", 5);
    }

    @Test
    void saveAndFindById_savesPolicyScopeAndTreeAsJson() {
        // Arrange
        policyRepository.save(testPolicy);
        
        em.flush();
        em.clear();

        // Act
        Optional<PurchasePolicy> foundOpt = policyRepository.findById(testPolicy.id());

        // Assert
        assertThat(foundOpt).isPresent();
        PurchasePolicy found = foundOpt.get();

        assertThat(found.policyName()).isEqualTo("VIP Ticket Limit");
        assertThat(found.companyId()).isEqualTo(companyId);
        
        // 1. בדיקה שה-Scope (אובייקט בפני עצמו) נשמר ונשלף מ-JSON
        assertThat(found.scope()).isNotNull();
        assertThat(found.scope().isCompanyWide()).isFalse();

        // 2. בדיקה שעץ הפוליסות הומר בהצלחה מ-JSON בחזרה ל-IPolicy
        assertThat(found.policy()).isNotNull();
        assertThat(found.policy().type()).isEqualTo(PolicyType.MAX_TICKETS);
    }
}
