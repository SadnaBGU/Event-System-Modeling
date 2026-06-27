package com.eventsystem.infrastructure.config.startup;

import com.eventsystem.infrastructure.persistence.springrepos.SpringDataActiveOrderRepository;
import com.eventsystem.infrastructure.persistence.springrepos.SpringDataDiscountPolicyRepository;
import com.eventsystem.infrastructure.persistence.springrepos.SpringDataEventRepository;
import com.eventsystem.infrastructure.persistence.springrepos.SpringDataLotteryRepository;
import com.eventsystem.infrastructure.persistence.springrepos.SpringDataMemberRepository;
import com.eventsystem.infrastructure.persistence.springrepos.SpringDataPlatformRepository;
import com.eventsystem.infrastructure.persistence.springrepos.SpringDataProductionCompanyRepository;
import com.eventsystem.infrastructure.persistence.springrepos.SpringDataPurchasePolicyRepository;
import com.eventsystem.infrastructure.persistence.springrepos.SpringDataPurchaseRecordRepository;
import com.eventsystem.infrastructure.persistence.springrepos.SpringDataVenueRepository;
import com.eventsystem.infrastructure.persistence.springrepos.SpringDataVirtualQueueRepository;
import com.eventsystem.infrastructure.persistence.springrepos.SpringDataZoneRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Infrastructure-only startup utility.
 *
 * Used by startup modes EMPTY_DB and INIT_FILE to guarantee a clean persisted
 * state before AdminBootstrap recreates the required platform/admin baseline.
 */
@Service
public class DBResetService {

    private static final Logger log = LoggerFactory.getLogger(DBResetService.class);

    private final SpringDataActiveOrderRepository activeOrders;
    private final SpringDataVirtualQueueRepository virtualQueues;
    private final SpringDataLotteryRepository lotteries;
    private final SpringDataPurchaseRecordRepository purchaseRecords;
    private final SpringDataDiscountPolicyRepository discountPolicies;
    private final SpringDataPurchasePolicyRepository purchasePolicies;
    private final SpringDataZoneRepository zones;
    private final SpringDataEventRepository events;
    private final SpringDataVenueRepository venues;
    private final SpringDataProductionCompanyRepository companies;
    private final SpringDataPlatformRepository platform;
    private final SpringDataMemberRepository members;

    public DBResetService(
            SpringDataActiveOrderRepository activeOrders,
            SpringDataVirtualQueueRepository virtualQueues,
            SpringDataLotteryRepository lotteries,
            SpringDataPurchaseRecordRepository purchaseRecords,
            SpringDataDiscountPolicyRepository discountPolicies,
            SpringDataPurchasePolicyRepository purchasePolicies,
            SpringDataZoneRepository zones,
            SpringDataEventRepository events,
            SpringDataVenueRepository venues,
            SpringDataProductionCompanyRepository companies,
            SpringDataPlatformRepository platform,
            SpringDataMemberRepository members) {
        this.activeOrders = activeOrders;
        this.virtualQueues = virtualQueues;
        this.lotteries = lotteries;
        this.purchaseRecords = purchaseRecords;
        this.discountPolicies = discountPolicies;
        this.purchasePolicies = purchasePolicies;
        this.zones = zones;
        this.events = events;
        this.venues = venues;
        this.companies = companies;
        this.platform = platform;
        this.members = members;
    }

    @Transactional
    public void resetDatabase() {
        log.warn("STARTUP_DB_RESET_START: deleting persisted state before bootstrap.");

        /*
         * Important:
         * Use deleteAll() instead of deleteAllInBatch().
         * deleteAllInBatch() is faster, but it bypasses entity cascade handling and can
         * break on element collections / join tables. Startup reset is not performance
         * critical, so deleteAll() is safer.
         *
         * Delete dependent aggregates before their parents.
         */
        activeOrders.deleteAll();
        virtualQueues.deleteAll();
        lotteries.deleteAll();
        purchaseRecords.deleteAll();

        discountPolicies.deleteAll();
        purchasePolicies.deleteAll();

        zones.deleteAll();
        events.deleteAll();
        venues.deleteAll();

        companies.deleteAll();
        platform.deleteAll();
        members.deleteAll();

        /*
         * Flush after the full ordered delete sequence so startup fails immediately if
         * mapping/order is wrong instead of failing later during admin bootstrap.
         */
        activeOrders.flush();
        virtualQueues.flush();
        lotteries.flush();
        purchaseRecords.flush();
        discountPolicies.flush();
        purchasePolicies.flush();
        zones.flush();
        events.flush();
        venues.flush();
        companies.flush();
        platform.flush();
        members.flush();

        log.warn("STARTUP_DB_RESET_SUCCESS: persisted state deleted.");
    }
}