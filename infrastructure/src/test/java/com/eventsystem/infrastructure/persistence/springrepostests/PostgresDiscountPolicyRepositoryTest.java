package com.eventsystem.infrastructure.persistence.springrepostests;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.policy.discount.Discount;
import com.eventsystem.domain.policy.discount.DiscountPolicy;
import com.eventsystem.domain.policy.discount.DiscountPolicyId;
import com.eventsystem.infrastructure.persistence.springrepos.PostgresDiscountPolicyRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EntityScan(basePackages = "com.eventsystem.domain")
@Import(PostgresDiscountPolicyRepository.class)
class PostgresDiscountPolicyRepositoryTest extends BasePostgresTest {

    @Autowired
    private PostgresDiscountPolicyRepository repository;
    @Autowired
    private EntityManager em;

    private CompanyId companyId;
    private EventId eventId;

    @BeforeEach
    void setUp() {
        companyId = new CompanyId("COMP-1");
        eventId = new EventId("EV-1");

        DiscountPolicy inactive = DiscountPolicy.clearScope(companyId);
        
        DiscountPolicy activeCW = DiscountPolicy.clearScope(companyId);
        // התיקון: הוספת הנחה לפוליסה הכלל-חברתית לפני שמפעילים אותה
        activeCW.addDiscount(Discount.GeneralDiscount("5%", BigDecimal.valueOf(5), null));
        activeCW.setCompanyWide();
        activeCW.activate();
        
        DiscountPolicy activeEvent = DiscountPolicy.clearScope(companyId);
        activeEvent.addDiscount(Discount.GeneralDiscount("10%", BigDecimal.TEN, null));
        activeEvent.activateForEvent(eventId);
        activeEvent.activate();

        repository.save(inactive);
        repository.save(activeCW);
        repository.save(activeEvent);
        
        em.flush();
        em.clear();
    }

    @Test
    void findMethods_filterStreamsCorrectly() {
        assertThat(repository.findByCompanyId(companyId)).hasSize(3);
        
        List<DiscountPolicy> active = repository.findAllActive();
        assertThat(active).hasSize(2);
        
        List<DiscountPolicy> activeWithDiscounts = repository.findActiveWithVisibleDiscounts();
        assertThat(activeWithDiscounts).hasSize(2);
        
        assertThat(repository.findActiveByCompanyId(companyId)).hasSize(2);
        
        assertThat(repository.findApplicableToEvent(eventId)).hasSize(2);
        assertThat(repository.findApplicableToPurchase(companyId, eventId)).hasSize(2);
        
        assertThat(repository.findSpecificForEvent(eventId)).hasSize(1);
        assertThat(repository.findSingleEventPolicies(companyId)).hasSize(1);
    }

    @Test
    void crudOperations_workCorrectly() {
        DiscountPolicyId id = repository.findByCompanyId(companyId).get(0).id();
        
        assertThat(repository.findById(id)).isPresent();
        assertThat(repository.existsById(id)).isTrue();
        
        repository.deleteById(id);
        em.flush();
        em.clear();
        
        assertThat(repository.findById(id)).isEmpty();
        assertThat(repository.existsById(id)).isFalse();
    }
}