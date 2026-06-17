package com.eventsystem.infrastructure.config;

import com.eventsystem.application.company.ICompanyPermissionServicePort;
import com.eventsystem.application.order.IPaymentGatewayPort;
import com.eventsystem.application.order.ITicketIssuancePort;
import com.eventsystem.application.order.IssuanceResult;
import com.eventsystem.application.order.PaymentResult;
import com.eventsystem.application.order.RefundResult;
import com.eventsystem.domain.company.CompanyDetails;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.company.IProductionCompanyRepository;
import com.eventsystem.domain.company.Permission;
import com.eventsystem.domain.company.ProductionCompany;
import com.eventsystem.domain.member.IMemberRepository;
import com.eventsystem.domain.member.Member;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.BuyerType;
import com.eventsystem.domain.shared.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppConfigBeansTest {

    @Mock
    private IProductionCompanyRepository productionCompanyRepository;

    @Mock
    private IMemberRepository memberRepository;

    private final AppConfig appConfig = new AppConfig();

    @Test
    void companyPermissionServicePort_FullLifecycleAndBranches() {
        ICompanyPermissionServicePort port = appConfig.companyPermissionServicePort(productionCompanyRepository, memberRepository);
        
        MemberId actorId = MemberId.random();
        CompanyId companyId = CompanyId.random();

        // 1. בדיקת גטר לשם החברה (נמצאה ולא נמצאה)
        ProductionCompany mockCompany = mock(ProductionCompany.class);
        CompanyDetails mockDetails = mock(CompanyDetails.class);
        when(mockDetails.name()).thenReturn("Mega Events");
        when(mockCompany.companyDetails()).thenReturn(mockDetails);
        when(productionCompanyRepository.findById(companyId)).thenReturn(Optional.of(mockCompany));
        
        assertThat(port.getCompanyName(companyId)).isEqualTo("Mega Events");
        
        when(productionCompanyRepository.findById(companyId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> port.getCompanyName(companyId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("company not found");

        // 2. בדיקת הרשאות - מקרה שבו ה-Member בכלל לא קיים במערכת (הענף של isEmpty)
        when(memberRepository.findById(actorId)).thenReturn(Optional.empty());
        assertThat(port.canManageEvents(actorId, companyId)).isFalse();

        // 3. בדיקת הרשאות - ה-Member קיים, אבל החברה לא קיימת
        when(memberRepository.findById(actorId)).thenReturn(Optional.of(mock(Member.class)));
        when(productionCompanyRepository.findById(companyId)).thenReturn(Optional.empty());
        assertThat(port.canManageDiscountPolicies(actorId, companyId)).isFalse();

        // 4. בדיקת הרשאות - הכל קיים (בודק החזרת True וגם False מה-Stream)
        when(productionCompanyRepository.findById(companyId)).thenReturn(Optional.of(mockCompany));
        
        when(mockCompany.hasPermission(actorId, Permission.EVENT_INVENTORY_MANAGEMENT)).thenReturn(true);
        assertThat(port.canManageEvents(actorId, companyId)).isTrue();
        
        when(mockCompany.hasPermission(actorId, Permission.MODIFY_POLICIES)).thenReturn(false);
        assertThat(port.canManagePurchasePolicies(actorId, companyId)).isFalse();

        // 5. בדיקת ולידציות של NullPointerException
        assertThatThrownBy(() -> port.canManageEvents(null, companyId)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> port.canManageEvents(actorId, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> port.getCompanyName(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void paymentGatewayDummy_ExecutesSuccessfully() {
        IPaymentGatewayPort gateway = appConfig.paymentGateway();
        BuyerReference buyer = new BuyerReference(BuyerType.GUEST, "sess", null);
        Money money = new Money(BigDecimal.TEN, "USD");

        PaymentResult chargeResult = gateway.charge("ORD-1", money, buyer, "token");
        assertThat(chargeResult.success()).isTrue();
        assertThat(chargeResult.transactionId()).contains("DUMMY-TXN");

        RefundResult refundResult = gateway.refund("TXN-1", money, "Customer request");
        assertThat(refundResult.success()).isTrue();
        assertThat(refundResult.errorMessage()).isEqualTo("Customer request");
    }

    @Test
    void ticketIssuanceDummy_ExecutesSuccessfully() {
        ITicketIssuancePort issuance = appConfig.ticketIssuance();
        BuyerReference buyer = new BuyerReference(BuyerType.GUEST, "sess", null);

        IssuanceResult result = issuance.issueTickets("EV-1", "ORD-1", List.of(), buyer);
        assertThat(result.success()).isTrue();
    }

    @Test
    void bootstrapPropertiesBean_InitializesCorrectly() {
        BootstrapProperties properties = appConfig.bootstrapProperties();
        assertThat(properties.admin().username()).isEqualTo("admin");
        assertThat(properties.queueLoadThreshold()).isEqualTo(100);
    }
}