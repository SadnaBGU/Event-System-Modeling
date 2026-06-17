package com.eventsystem.infrastructure.persistence.springrepostests;

import com.eventsystem.domain.company.CompanyId;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

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

    @BeforeEach
    void setUp() {
        companyId = new CompanyId("COMP-1");
    }

    @Test
    void saveAndFindById_persistsCorrectly() {
        PurchasePolicy policy = PurchasePolicy.newAllowAllPolicy(companyId, "Policy 1");
        repository.save(policy);
        
        em.flush();
        em.clear();

        Optional<PurchasePolicy> found = repository.findById(policy.id());
        assertThat(found).isPresent();
        assertThat(found.get().policyName()).isEqualTo("Policy 1");
    }

    @Test
    void existsById_returnsTrueOnlyWhenExists() {
        PurchasePolicy policy = PurchasePolicy.newAllowAllPolicy(companyId, "Exists");
        repository.save(policy);
        em.flush();
        em.clear();

        assertThat(repository.existsById(policy.id())).isTrue();
        assertThat(repository.existsById(PurchasePolicyId.random())).isFalse();
    }

    @Test
    void deleteById_removesRecord() {
        PurchasePolicy policy = PurchasePolicy.newAllowAllPolicy(companyId, "To Delete");
        repository.save(policy);
        em.flush();
        em.clear();

        repository.deleteById(policy.id());
        em.flush();
        em.clear();

        assertThat(repository.findById(policy.id())).isEmpty();
    }
}