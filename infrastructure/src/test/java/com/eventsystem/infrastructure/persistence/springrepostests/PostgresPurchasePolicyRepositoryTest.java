package com.eventsystem.infrastructure.persistence.springrepostests;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.policy.purchase.PurchasePolicy;
import com.eventsystem.domain.policy.purchase.PurchasePolicyId;
import com.eventsystem.infrastructure.persistence.springrepos.PostgresPurchasePolicyRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EntityScan(basePackages = "com.eventsystem.domain")
@Import(PostgresPurchasePolicyRepository.class)
class PostgresPurchasePolicyRepositoryTest extends BasePostgresTest {

    @Autowired
    private PostgresPurchasePolicyRepository repository;
    @Autowired
    private EntityManager em;

    private CompanyId companyId;
    private EventId eventId;

    @BeforeEach
    void setUp() {
        companyId = new CompanyId("COMP-1");
        eventId = new EventId("EV-1");

        // נשמור 3 סוגי פוליסות כדי לכסות את כל סינוני ה-Streams (true/false)
        PurchasePolicy inactive = PurchasePolicy.newMaxTicketPolicy(companyId, "Inactive", 5);
        
        PurchasePolicy activeCompanyWide = PurchasePolicy.newMaxTicketPolicy(companyId, "Active CW", 5);
        activeCompanyWide.setCompanyWide();
        
        PurchasePolicy activeEventSpecific = PurchasePolicy.newMaxTicketPolicy(companyId, "Active Event", 5);
        activeEventSpecific.activateForEvent(eventId);

        repository.save(inactive);
        repository.save(activeCompanyWide);
        repository.save(activeEventSpecific);
        
        em.flush();
        em.clear();
    }

    @Test
    void findMethods_filterStreamsCorrectly() {
        // findByCompanyId - אמור להחזיר את כולם (3 פוליסות)
        List<PurchasePolicy> allCompany = repository.findByCompanyId(companyId);
        assertThat(allCompany).hasSize(3);

        // findActiveByCompanyId - מסנן את הלא-פעילים (אמור להחזיר 2)
        List<PurchasePolicy> activeOnly = repository.findActiveByCompanyId(companyId);
        assertThat(activeOnly).hasSize(2).allMatch(PurchasePolicy::isActive);

        // findApplicableToEvent - מחזיר את הכלל חברתי ואת הספציפי לאירוע (2)
        List<PurchasePolicy> applicableToEv1 = repository.findApplicableToEvent(eventId);
        assertThat(applicableToEv1).hasSize(2);
        
        // findApplicableToEvent לאירוע אחר - יחזיר רק את הכלל חברתי (1)
        List<PurchasePolicy> applicableToEv2 = repository.findApplicableToEvent(new EventId("EV-2"));
        assertThat(applicableToEv2).hasSize(1);

        // findApplicableToPurchase - מחזיר את הכלל חברתי ואת הספציפי לאירוע השייכים לחברה
        List<PurchasePolicy> applicablePurchase = repository.findApplicableToPurchase(companyId, eventId);
        assertThat(applicablePurchase).hasSize(2);

        // findSingleEventPolicies - מחזיר רק את הספציפי לאירוע בודד
        List<PurchasePolicy> singleEvent = repository.findSingleEventPolicies(companyId);
        assertThat(singleEvent).hasSize(1);
        assertThat(singleEvent.get(0).policyName()).isEqualTo("Active Event");

        // findSpecificForEvent - מחזיר רק את הפוליסה הספציפית לאירוע הזה (לא את הכלל חברתי)
        List<PurchasePolicy> specificEvent = repository.findSpecificForEvent(eventId);
        assertThat(specificEvent).hasSize(1);
    }

    @Test
    void crudOperations_workCorrectly() {
        PurchasePolicyId id = repository.findByCompanyId(companyId).get(0).id();
        
        assertThat(repository.findById(id)).isPresent();
        assertThat(repository.existsById(id)).isTrue();
        
        repository.deleteById(id);
        em.flush();
        em.clear();
        
        assertThat(repository.findById(id)).isEmpty();
        assertThat(repository.existsById(id)).isFalse();
    }

    @Test
    void save_null_throwsException() {
        assertThatThrownBy(() -> repository.save(null)).isInstanceOf(NullPointerException.class);
    }
}