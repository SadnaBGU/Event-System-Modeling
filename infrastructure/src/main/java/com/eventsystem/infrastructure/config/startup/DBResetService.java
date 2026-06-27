package com.eventsystem.infrastructure.config.startup;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DBResetService {

    private static final Logger logger = LoggerFactory.getLogger(DBResetService.class);

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * REQ: SYS-01, SYS-02, PERS-06, PERS-07
     *
     * Startup reset is an infrastructure operation, not a business use-case delete.
     * Use PostgreSQL TRUNCATE so EMPTY_DB / INIT_FILE starts from a truly clean state,
     * without loading versioned entities and without optimistic-lock conflicts with
     * startup/background cleanup jobs.
     */
    @Transactional
    public void resetDatabase() {
        logger.warn("STARTUP_DB_RESET_START: truncating persisted state before bootstrap.");

        entityManager.flush();
        entityManager.clear();

        entityManager
                .createNativeQuery("""
                        TRUNCATE TABLE
                            active_order_items,
                            active_orders,
                            virtual_queues,
                            lottery_registrations,
                            lottery_winners,
                            lotteries,
                            purchase_record_discounts,
                            purchase_record_items,
                            purchase_records,
                            discount_policy_discounts,
                            discount_policies,
                            purchase_policies,
                            event_dates,
                            event_zones,
                            seats,
                            zones,
                            events,
                            venues,
                            platform_system_admins,
                            platform_payment_providers,
                            platform_issuance_providers,
                            platform_config,
                            notifications,
                            production_companies,
                            members
                        RESTART IDENTITY CASCADE
                        """)
                .executeUpdate();

        entityManager.flush();
        entityManager.clear();

        logger.warn("STARTUP_DB_RESET_SUCCESS: persisted state was truncated.");
    }
}