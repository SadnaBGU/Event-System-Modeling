package com.eventsystem.infrastructure.config;

import com.eventsystem.application.admin.AdminService;
import com.eventsystem.application.auth.AuthService;
import com.eventsystem.application.company.ICompanyPermissionServicePort;
import com.eventsystem.application.company.ProductionCompanyService;
import com.eventsystem.application.event.EventPurchaseService;
import com.eventsystem.application.event.EventService;
import com.eventsystem.application.event.IEventManagementPort;
import com.eventsystem.application.event.IEventQueryPort;
import com.eventsystem.application.event.ZoneService;
import com.eventsystem.application.lottery.LotteryService;
import com.eventsystem.application.member.IMemberInformationPort;
import com.eventsystem.application.member.INotificationPort;
import com.eventsystem.application.member.MemberService;
import com.eventsystem.application.order.CheckoutSaga;
import com.eventsystem.application.order.IPaymentGatewayPort;
import com.eventsystem.application.order.ITicketIssuancePort;
import com.eventsystem.application.order.IssuanceResult;
import com.eventsystem.application.order.OrderService;
import com.eventsystem.application.order.PaymentResult;
import com.eventsystem.application.order.PurchaseHistoryService;
import com.eventsystem.application.order.QueueService;
import com.eventsystem.application.order.RefundResult;
import com.eventsystem.application.order.ReportService;
import com.eventsystem.application.policy.DiscountApplicationService;
import com.eventsystem.application.policy.IDiscountApplicationPort;
import com.eventsystem.application.policy.IPolicyManagementPort;
import com.eventsystem.application.policy.IPurchasePolicyValidationPort;
import com.eventsystem.application.policy.PolicyManagementService;
import com.eventsystem.application.policy.PurchasePolicyValidationService;
import com.eventsystem.application.policy.policybuilder.PolicyCommandAssembler;
import com.eventsystem.application.security.IPasswordHasher;
import com.eventsystem.application.security.ITokenService;
import com.eventsystem.application.venue.VenueManagementService;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.company.IProductionCompanyRepository;
import com.eventsystem.domain.company.Permission;
import com.eventsystem.domain.event.IEventRepository;
import com.eventsystem.domain.lottery.ILotteryRepository;
import com.eventsystem.domain.member.IMemberRepository;
import com.eventsystem.domain.member.Member;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.member.MemberStatus;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.IActiveOrderRepository;
import com.eventsystem.domain.order.OrderFactory;
import com.eventsystem.domain.platform.IPlatformRepository;
import com.eventsystem.domain.policy.discount.IDiscountPolicyRepository;
import com.eventsystem.domain.policy.purchase.IPurchasePolicyRepository;
import com.eventsystem.domain.purchaserecord.IPurchaseRecordRepository;
import com.eventsystem.domain.queue.IVirtualQueueRepository;
import com.eventsystem.domain.shared.Money;
import com.eventsystem.domain.venue.IVenueRepository;
import com.eventsystem.domain.zone.IZoneRepository;
import com.eventsystem.application.event.EventCatalogService;
import com.eventsystem.infrastructure.notifications.NotificationPortImpl;
import com.eventsystem.infrastructure.persistence.inmemoryrepos.InMemoryActiveOrderRepository;
import com.eventsystem.infrastructure.persistence.inmemoryrepos.InMemoryDiscountPolicyRepository;
import com.eventsystem.infrastructure.persistence.inmemoryrepos.InMemoryEventRepository;
import com.eventsystem.infrastructure.persistence.inmemoryrepos.InMemoryLotteryRepository;
import com.eventsystem.infrastructure.persistence.inmemoryrepos.InMemoryMemberRepository;
import com.eventsystem.infrastructure.persistence.inmemoryrepos.InMemoryPlatformRepository;
import com.eventsystem.infrastructure.persistence.inmemoryrepos.InMemoryProductionCompanyRepository;
import com.eventsystem.infrastructure.persistence.inmemoryrepos.InMemoryPurchasePolicyRepository;
import com.eventsystem.infrastructure.persistence.inmemoryrepos.InMemoryPurchaseRecordRepository;
import com.eventsystem.infrastructure.persistence.inmemoryrepos.InMemoryVenueRepository;
import com.eventsystem.infrastructure.persistence.inmemoryrepos.InMemoryVirtualQueueRepository;
import com.eventsystem.infrastructure.persistence.inmemoryrepos.InMemoryZoneRepository;
import com.eventsystem.infrastructure.security.BCryptPasswordHasher;
import com.eventsystem.infrastructure.security.JwtTokenService;
import com.eventsystem.infrastructure.persistence.springrepos.SpringDataZoneRepository;
import com.eventsystem.infrastructure.persistence.springrepos.PostgresZoneRepository;
import com.eventsystem.infrastructure.persistence.springrepos.PostgresActiveOrderRepository;
import com.eventsystem.infrastructure.persistence.springrepos.PostgresDiscountPolicyRepository;
import com.eventsystem.infrastructure.persistence.springrepos.PostgresEventRepository;
import com.eventsystem.infrastructure.persistence.springrepos.PostgresMemberRepository;
import com.eventsystem.infrastructure.persistence.springrepos.PostgresPurchaseRecordRepository;
import com.eventsystem.infrastructure.persistence.springrepos.SpringDataActiveOrderRepository;
import com.eventsystem.infrastructure.persistence.springrepos.SpringDataDiscountPolicyRepository;
import com.eventsystem.infrastructure.persistence.springrepos.SpringDataEventRepository;
import com.eventsystem.infrastructure.persistence.springrepos.SpringDataLotteryRepository;
import com.eventsystem.infrastructure.persistence.springrepos.SpringDataMemberRepository;
import com.eventsystem.infrastructure.persistence.springrepos.SpringDataPlatformRepository;
import com.eventsystem.infrastructure.persistence.springrepos.SpringDataProductionCompanyRepository;
import com.eventsystem.infrastructure.persistence.springrepos.SpringDataPurchasePolicyRepository;
import com.eventsystem.infrastructure.persistence.springrepos.SpringDataPurchaseRecordRepository;
import com.eventsystem.infrastructure.persistence.springrepos.PostgresLotteryRepository;
import com.eventsystem.infrastructure.persistence.springrepos.PostgresPlatformRepository;
import com.eventsystem.infrastructure.persistence.springrepos.PostgresProductionCompanyRepository;
import com.eventsystem.infrastructure.persistence.springrepos.PostgresPurchasePolicyRepository;
import com.eventsystem.infrastructure.persistence.springrepos.SpringDataVirtualQueueRepository;
import com.eventsystem.infrastructure.persistence.springrepos.PostgresVirtualQueueRepository;
import com.eventsystem.infrastructure.persistence.springrepos.SpringDataVenueRepository;
import com.eventsystem.infrastructure.persistence.springrepos.PostgresVenueRepository;


import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Objects;

@Configuration
@EnableJpaRepositories(basePackages = "com.eventsystem.infrastructure.persistence.springrepos")
// @ComponentScan(basePackages = { 
//     "com.eventsystem.domain.*", 
//     "com.eventsystem.infrastructure.*" 
// })
@EntityScan(basePackages = {"com.eventsystem.domain"})
public class AppConfig {

    // --- Configuration constants ---
    private final String jwtSecret = "CHANGE_ME_DEV_ONLY_0123456789abcdef";
    private final Duration tokenValidity = Duration.ofHours(1);
    private final int bcryptStrength = 12;
    private final Duration lotteryCodeValidity = Duration.ofMinutes(15);

    @Bean
    public BootstrapProperties bootstrapProperties() {
        return new BootstrapProperties(
                new BootstrapProperties.Admin(
                        "admin",
                        "changeme123",
                        "Initial",
                        "Admin",
                        "admin@eventsystem.local",
                        LocalDate.of(1990, 1, 1)),
                Duration.ofMinutes(15),
                100);
    }

    // ==========================================
    // 1. Adapters (Repositories & Security)
    // ==========================================

    @Bean
    public IPlatformRepository platformRepository(SpringDataPlatformRepository jpaRepo) {
        return new PostgresPlatformRepository(jpaRepo);
    }

    @Bean
    public ILotteryRepository lotteryRepository(SpringDataLotteryRepository springDataLotteryRepo) {
        return new PostgresLotteryRepository(springDataLotteryRepo);
    }

    @Bean
    public IActiveOrderRepository activeOrderRepository(SpringDataActiveOrderRepository springDataActiveOrderRepo) {
        return new PostgresActiveOrderRepository(springDataActiveOrderRepo);
    }

    @Bean
    public IZoneRepository zoneRepository(SpringDataZoneRepository springDataZoneRepo) {
        return new PostgresZoneRepository(springDataZoneRepo);
    }

    @Bean
    public IEventRepository eventRepository(SpringDataEventRepository springDataEventRepo) {
        return new PostgresEventRepository(springDataEventRepo);
    }

    @Bean
    public IProductionCompanyRepository productionCompanyRepository(SpringDataProductionCompanyRepository jpaRepo) {
        return new PostgresProductionCompanyRepository(jpaRepo);
    }

    @Bean
    public IPurchaseRecordRepository purchaseRecordRepository(SpringDataPurchaseRecordRepository springDataPurchaseRecordRepo) {
        return new PostgresPurchaseRecordRepository(springDataPurchaseRecordRepo);
    }

    @Bean
    public IVenueRepository venueRepository(SpringDataVenueRepository springDataVenueRepo) {
        return new PostgresVenueRepository(springDataVenueRepo);
    }

    @Bean
    public IMemberRepository memberRepository(SpringDataMemberRepository springDataMemberRepo) {
        return new PostgresMemberRepository(springDataMemberRepo);
    }

    @Bean
    public IVirtualQueueRepository virtualQueueRepository(SpringDataVirtualQueueRepository springDataVirtualQueueRepo) {
        return new PostgresVirtualQueueRepository(springDataVirtualQueueRepo);
    }

    @Bean
    public IDiscountPolicyRepository discountPolicyRepository(SpringDataDiscountPolicyRepository springDataRepo) {
        return new PostgresDiscountPolicyRepository(springDataRepo);
    }

    @Bean
    public IPurchasePolicyRepository purchasePolicyRepository(SpringDataPurchasePolicyRepository springDataRepo) {
        return new PostgresPurchasePolicyRepository(springDataRepo);
    }

    @Bean
    public BCryptPasswordHasher passwordHasher() {
        return new BCryptPasswordHasher(bcryptStrength);
    }

    @Bean
    public ITokenService tokenService() {
        return new JwtTokenService(jwtSecret);
    }

    @Bean
    public OrderFactory orderFactory() {
        return new OrderFactory();
    }

    @Bean
    public PolicyCommandAssembler policyCommandAssembler() {
        return new PolicyCommandAssembler();
    }



    /**
     * Adapter from the company aggregate/repository to the permission port used by
     * EventService, DiscountPolicyService, PurchasePolicyService, and EventPurchaseService.
     */
    @Bean
    public ICompanyPermissionServicePort companyPermissionServicePort(
            IProductionCompanyRepository productionCompanyRepository,
            IMemberRepository memberRepository
    ) {
        return new ICompanyPermissionServicePort() {
            @Override
            public boolean canManageEvents(MemberId actorId, CompanyId companyId) {
                return hasCompanyPermission(actorId, companyId, Permission.EVENT_INVENTORY_MANAGEMENT);
            }

            @Override
            public boolean canManageDiscountPolicies(MemberId actorId, CompanyId companyId) {
                return hasCompanyPermission(actorId, companyId, Permission.MODIFY_POLICIES);
            }

            @Override
            public boolean canManagePurchasePolicies(MemberId actorId, CompanyId companyId) {
                return hasCompanyPermission(actorId, companyId, Permission.MODIFY_POLICIES);
            }

            @Override
            public String getCompanyName(CompanyId companyId) {
                Objects.requireNonNull(companyId, "companyId must not be null");
                return productionCompanyRepository.findById(companyId)
                        .map(company -> company.companyDetails().name())
                        .orElseThrow(() -> new IllegalArgumentException("company not found: " + companyId));
            }

            private boolean hasCompanyPermission(MemberId actorId, CompanyId companyId, Permission permission) {
                Objects.requireNonNull(actorId, "actorId must not be null");
                Objects.requireNonNull(companyId, "companyId must not be null");
                Objects.requireNonNull(permission, "permission must not be null");

                if (memberRepository.findById(actorId).isEmpty()) {
                    return false;
                }

                return productionCompanyRepository.findById(companyId)
                        .map(company -> company.hasPermission(actorId, permission))
                        .orElse(false);
            }
        };
    }

    // ==========================================
    // 2. Application Services
    // ==========================================
    @Bean
    public AuthService authService(ITokenService tokenService) {
        return new AuthService(tokenService);
    }

    @Bean
    public MemberService memberService(IMemberRepository memberRepo, 
                                        IPasswordHasher passwordHasher,
                                        ITokenService tokenService
    ) {
        return new MemberService(memberRepo, passwordHasher, tokenService, this.tokenValidity);
    }

    @Bean
    public AdminService adminService(IPlatformRepository platformRepo, IMemberRepository memberRepo) {
        return new AdminService(platformRepo, memberRepo);
    }

    @Bean
    public LotteryService lotteryService(ILotteryRepository lotteryRepo) {
        return new LotteryService(lotteryRepo, new SecureRandom(), Clock.systemUTC(), lotteryCodeValidity);
    }

    @Bean
    public QueueService queueService(IVirtualQueueRepository virtualQueueRepo, INotificationPort notificationService) {
        return new QueueService(virtualQueueRepo, notificationService);
    }

    @Bean
    public OrderService orderService(IActiveOrderRepository orderRepo,
                                     IZoneRepository zoneRepo,
                                     OrderFactory orderFactory,
                                     ILotteryRepository lotteryRepo) {
        return new OrderService(orderRepo, zoneRepo, orderFactory, lotteryRepo);
    }

    @Bean
    public PurchaseHistoryService purchaseHistoryService(IPurchaseRecordRepository purchaseRecordRepo) {
        return new PurchaseHistoryService(purchaseRecordRepo);
    }

    @Bean
    public ProductionCompanyService productionCompanyService(IProductionCompanyRepository productionCompanyRepo,
                                                             IMemberRepository memberRepo) {
        return new ProductionCompanyService(productionCompanyRepo, memberRepo);
    }

    @Bean
    public EventService eventService(IEventRepository eventRepo,
                                     ICompanyPermissionServicePort companyPermissionServicePort) {
        return new EventService(eventRepo, companyPermissionServicePort);
    }

    @Bean
    public EventCatalogService eventCatalogService(IEventRepository eventRepo,
                                                   IZoneRepository zoneRepo,
                                                   IProductionCompanyRepository productionCompanyRepository) {
        return new EventCatalogService(eventRepo, zoneRepo, productionCompanyRepository);
    }

    @Bean
    public ReportService reportService(IPurchaseRecordRepository purchaseRecordRepo) {
        return new ReportService(purchaseRecordRepo);
    }

    @Bean
    public ZoneService zoneService(IZoneRepository zoneRepo) {
        return new ZoneService(zoneRepo);
    }

    @Bean
    public VenueManagementService venueManagementService(IVenueRepository venueRepo, IMemberRepository memberRepo) {
        return new VenueManagementService(venueRepo, memberRepo);
    }

    @Bean
    public NotificationPortImpl notificationService(IMemberRepository memberRepo,
                                                   com.eventsystem.application.member.NotificationBroadcaster broadcaster) {
        return new NotificationPortImpl(memberRepo, broadcaster);
    }

    @Bean
    public IEventQueryPort eventPurchaseService(IEventRepository eventRepo,
                                                IZoneRepository zoneRepo,
                                                ICompanyPermissionServicePort companyPermissionServicePort) {
        return new EventPurchaseService(eventRepo, zoneRepo, companyPermissionServicePort);
    }

    @Bean
    public IPurchasePolicyValidationPort purchasePolicyValidationPort(IPurchasePolicyRepository purchasePolicyRepository,
                                                                      IEventManagementPort eventManagementPort,
                                                                      MemberService memberService) {
        return new PurchasePolicyValidationService(purchasePolicyRepository, eventManagementPort, memberService);
    }

    @Bean
    public IDiscountApplicationPort discountApplicationPort(IDiscountPolicyRepository discountPolicyRepository,
                                                            IEventManagementPort eventManagementPort,
                                                            MemberService memberService) {
        return new DiscountApplicationService(discountPolicyRepository,eventManagementPort,memberService);
    }

    @Bean
    public PolicyManagementService policyManagementService(IPurchasePolicyRepository purchasePolicyRepository,
                                                        IDiscountPolicyRepository discountPolicyRepository,
                                                        ICompanyPermissionServicePort companyPermissionServicePort,
                                                        IEventManagementPort eventManagementPort,
                                                        PolicyCommandAssembler policyAssembler) {
        return new PolicyManagementService(purchasePolicyRepository, discountPolicyRepository,
                                            companyPermissionServicePort, eventManagementPort, policyAssembler);
    }

    @Bean
    public IPaymentGatewayPort paymentGateway() {
        return new IPaymentGatewayPort() {
            @Override
            public PaymentResult charge(String orderId, Money amount, BuyerReference buyer, String token) {
                return PaymentResult.successful("DUMMY-TXN-" + System.currentTimeMillis());
            }

            @Override
            public RefundResult refund(String transactionId, Money amount, String reason) {
                return new RefundResult(true, reason);
            }
        };
    }

    @Bean
    public ITicketIssuancePort ticketIssuance() {
        return new ITicketIssuancePort() {
            @Override
            public IssuanceResult issueTickets(String eventId,
                                               String orderId,
                                               java.util.List<com.eventsystem.domain.order.OrderItem> items,
                                               BuyerReference buyer) {
                return IssuanceResult.successful("DUMMY-TICKET-BATCH-" + System.currentTimeMillis());
            }
        };
    }

    @Bean
    public CheckoutSaga checkoutSaga(IActiveOrderRepository orderRepo,
                                     IPurchaseRecordRepository purchaseRecordRepo,
                                     IPaymentGatewayPort paymentGateway,
                                     ITicketIssuancePort ticketIssuance,
                                     INotificationPort notificationService,
                                     IZoneRepository zoneRepo,
                                     IPurchasePolicyValidationPort purchasePolicyPort,
                                     IDiscountApplicationPort discountPort,
                                     IEventQueryPort eventQueryPort) {
        return new CheckoutSaga(
                orderRepo,
                purchaseRecordRepo,
                paymentGateway,
                ticketIssuance,
                notificationService,
                zoneRepo,
                purchasePolicyPort,
                discountPort,
                eventQueryPort
        );
    }

    // ==========================================
    // 3. Bootstrap Runner
    // ==========================================
    @Bean
    public CommandLineRunner runAdminBootstrap(IPlatformRepository platformRepo,
                                               IMemberRepository memberRepo,
                                               BCryptPasswordHasher passwordHasher,
                                               BootstrapProperties props) {
        return args -> new AdminBootstrap(platformRepo, memberRepo, passwordHasher, props).run();
    }
}
