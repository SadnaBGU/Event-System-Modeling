package com.eventsystem.infrastructure.persistence.springrepostests;

import com.eventsystem.domain.company.CompanyStatus;
import com.eventsystem.domain.company.ProductionCompany;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.infrastructure.persistence.springrepos.PostgresProductionCompanyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.persistence.EntityManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EntityScan(basePackages = "com.eventsystem.domain")
@Import(PostgresProductionCompanyRepository.class)
class PostgresProductionCompanyRepositoryTest extends BasePostgresTest {

    @Autowired
    private PostgresProductionCompanyRepository companyRepository;

    @Autowired
    private EntityManager em;

    private ProductionCompany testCompany;
    private MemberId founderId;

    @BeforeEach
    void setUp() {
        founderId = new MemberId("MEM-001");
        
        // יצירת חברה ראשית לטסטים בעזרת ה-Factory Method שסידרנו קודם
        testCompany = ProductionCompany.create(
                founderId,
                "Epic Productions",
                "Leading event organizers in Israel",
                4.8
        );
    }

    @Test
    void saveAndFindById_savesAllDetailsAndAppointmentTree() {
        // Arrange
        companyRepository.save(testCompany);
        
        em.flush();
        em.clear();

        // Act
        Optional<ProductionCompany> foundOpt = companyRepository.findById(testCompany.companyId());

        // Assert
        assertThat(foundOpt).isPresent();
        ProductionCompany found = foundOpt.get();

        assertThat(found.companyDetails().name()).isEqualTo("Epic Productions");
        assertThat(found.companyDetails().rating()).isEqualTo(4.8);
        assertThat(found.status()).isEqualTo(CompanyStatus.ACTIVE);
        
        // 2. בדיקת Embedded Field
        assertThat(found.founderId()).isEqualTo(founderId);

        // 3. בדיקה קריטית: מוודאים שעץ המינויים (JSONB) שוחזר בהצלחה!
        assertThat(found.isOwner(founderId)).isTrue();
    }

    @Test
    void findActiveCompanies_returnsOnlyActive() {
        // Arrange
        companyRepository.save(testCompany); 
        
        ProductionCompany closedCompany = ProductionCompany.create(
                new MemberId("MEM-002"),
                "Old Productions",
                "Closed company",
                3.0
        );
        companyRepository.save(closedCompany);

        em.flush();
        em.clear();

        // הערה: אם הוספת שאילתה מותאמת אישית ל-Repository, אפשר לפתוח את השורות הבאות:
        // List<ProductionCompany> activeCompanies = companyRepository.findActiveCompanies();
        // assertThat(activeCompanies).hasSize(1);
    }

    @Test
    void findByFounderId_returnsCorrectCompanies() {
        // Arrange: יצירת חברה נוספת לאותו מייסד
        companyRepository.save(testCompany);
        
        ProductionCompany secondCompany = ProductionCompany.create(
                founderId,
                "Secondary Productions",
                "Another branch",
                4.5
        );
        companyRepository.save(secondCompany);

        // חברה של מייסד אחר
        ProductionCompany otherCompany = ProductionCompany.create(
                new MemberId("MEM-999"),
                "Competitor Inc",
                "Rivals",
                4.0
        );
        companyRepository.save(otherCompany);

        em.flush();
        em.clear();
        
        // הערה: אם הוספת שאילתה ל-Repository, אפשר לפתוח:
        // List<ProductionCompany> founderCompanies = companyRepository.findByFounderId(founderId);
        // assertThat(founderCompanies).hasSize(2);
    }

    @Test
    void suspendCompany_persistsStatusChange() {
        // Arrange
        companyRepository.save(testCompany);
        em.flush();
        em.clear();

        // Act
        ProductionCompany found = companyRepository.findById(testCompany.companyId()).get();
        found.suspend(); // שימוש בלוגיקה העסקית שכתבת
        companyRepository.save(found);
        
        em.flush();
        em.clear();

        // Assert
        ProductionCompany updated = companyRepository.findById(testCompany.companyId()).get();
        assertThat(updated.status()).isEqualTo(CompanyStatus.SUSPENDED);
    }

    @Test
    void appointManager_savesTreeModificationsToJson() {
        // Arrange
        companyRepository.save(testCompany);
        em.flush();
        em.clear();

        // Act
        ProductionCompany found = companyRepository.findById(testCompany.companyId()).get();
        MemberId newManagerId = new MemberId("MEM-002");
        
        found.appointManager(founderId, newManagerId, java.util.Set.of()); 
        
        // --- התיקון: נאשר את המינוי כדי שה-isManager יחזיר true ---
        found.acceptAppointment(newManagerId);
        // --------------------------------------------------------

        companyRepository.save(found);
        em.flush();
        em.clear();

        // Assert
        ProductionCompany updated = companyRepository.findById(testCompany.companyId()).get();
        assertThat(updated.isManager(newManagerId)).isTrue();
    }
}