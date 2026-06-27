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

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

class DatabaseResetServiceTest {

    @Test
    void resetDatabase_deletesRepositoriesInDependencyOrder() {
        SpringDataActiveOrderRepository activeOrders = mock(SpringDataActiveOrderRepository.class);
        SpringDataVirtualQueueRepository virtualQueues = mock(SpringDataVirtualQueueRepository.class);
        SpringDataLotteryRepository lotteries = mock(SpringDataLotteryRepository.class);
        SpringDataPurchaseRecordRepository purchaseRecords = mock(SpringDataPurchaseRecordRepository.class);
        SpringDataDiscountPolicyRepository discountPolicies = mock(SpringDataDiscountPolicyRepository.class);
        SpringDataPurchasePolicyRepository purchasePolicies = mock(SpringDataPurchasePolicyRepository.class);
        SpringDataZoneRepository zones = mock(SpringDataZoneRepository.class);
        SpringDataEventRepository events = mock(SpringDataEventRepository.class);
        SpringDataVenueRepository venues = mock(SpringDataVenueRepository.class);
        SpringDataProductionCompanyRepository companies = mock(SpringDataProductionCompanyRepository.class);
        SpringDataPlatformRepository platform = mock(SpringDataPlatformRepository.class);
        SpringDataMemberRepository members = mock(SpringDataMemberRepository.class);

        DBResetService service = new DBResetService(
                activeOrders,
                virtualQueues,
                lotteries,
                purchaseRecords,
                discountPolicies,
                purchasePolicies,
                zones,
                events,
                venues,
                companies,
                platform,
                members
        );

        assertThatCode(service::resetDatabase)
                .doesNotThrowAnyException();

        InOrder deleteOrder = inOrder(
                activeOrders,
                virtualQueues,
                lotteries,
                purchaseRecords,
                discountPolicies,
                purchasePolicies,
                zones,
                events,
                venues,
                companies,
                platform,
                members
        );

        deleteOrder.verify(activeOrders).deleteAll();
        deleteOrder.verify(virtualQueues).deleteAll();
        deleteOrder.verify(lotteries).deleteAll();
        deleteOrder.verify(purchaseRecords).deleteAll();
        deleteOrder.verify(discountPolicies).deleteAll();
        deleteOrder.verify(purchasePolicies).deleteAll();
        deleteOrder.verify(zones).deleteAll();
        deleteOrder.verify(events).deleteAll();
        deleteOrder.verify(venues).deleteAll();
        deleteOrder.verify(companies).deleteAll();
        deleteOrder.verify(platform).deleteAll();
        deleteOrder.verify(members).deleteAll();
    }
}