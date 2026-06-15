package com.eventsystem.infrastructure.persistence.springrepostests;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.policy.discount.Discount;
import com.eventsystem.domain.policy.discount.DiscountPolicy;
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
}
