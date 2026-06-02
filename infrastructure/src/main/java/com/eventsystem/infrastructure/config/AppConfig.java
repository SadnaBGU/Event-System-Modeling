package com.eventsystem.infrastructure.config;

import com.eventsystem.application.admin.AdminService;
import com.eventsystem.application.admin.IPlatformRepository;
import com.eventsystem.application.auth.AuthService;
import com.eventsystem.application.company.ICompanyPermissionServicePort;
import com.eventsystem.application.company.IProductionCompanyRepository;
import com.eventsystem.application.company.ProductionCompanyService;
import com.eventsystem.application.event.EventPurchaseService;
import com.eventsystem.application.event.EventService;
import com.eventsystem.application.event.IEventManagementPort;
import com.eventsystem.application.event.IEventQueryPort;
import com.eventsystem.application.event.IEventRepository;
import com.eventsystem.application.event.IZoneRepository;
import com.eventsystem.application.event.ZoneService;
import com.eventsystem.application.lottery.ILotteryRepository;
import com.eventsystem.application.lottery.LotteryService;
import com.eventsystem.application.member.IMemberInformationPort;
import com.eventsystem.application.member.IMemberRepository;
import com.eventsystem.application.member.INotificationPort;
import com.eventsystem.application.member.MemberService;
import com.eventsystem.application.member.NotificationService;
import com.eventsystem.application.order.CheckoutSaga;
import com.eventsystem.application.order.IActiveOrderRepository;
import com.eventsystem.application.order.IPaymentGatewayPort;
import com.eventsystem.application.order.IPurchaseRecordRepository;
import com.eventsystem.application.order.ITicketIssuancePort;
import com.eventsystem.application.order.IVirtualQueueRepository;
import com.eventsystem.application.order.IssuanceResult;
import com.eventsystem.application.order.OrderService;
import com.eventsystem.application.order.PaymentResult;
import com.eventsystem.application.order.PurchaseHistoryService;
import com.eventsystem.application.order.QueueService;
import com.eventsystem.application.order.RefundResult;
import com.eventsystem.application.order.ReportService;
import com.eventsystem.application.policy.DiscountApplicationService;
import com.eventsystem.application.policy.IDiscountApplicationPort;
import com.eventsystem.application.policy.IDiscountPolicyRepository;
import com.eventsystem.application.policy.IPolicyManagementPort;
import com.eventsystem.application.policy.IPurchasePolicyRepository;
import com.eventsystem.application.policy.IPurchasePolicyValidationPort;
import com.eventsystem.application.policy.PolicyManagementService;
import com.eventsystem.application.policy.PurchasePolicyValidationService;
import com.eventsystem.application.policy.policybuilder.PolicyCommandAssembler;
import com.eventsystem.application.security.ITokenService;
import com.eventsystem.application.venue.IVenueRepository;
import com.eventsystem.application.venue.VenueManagementService;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.company.Permission;
import com.eventsystem.domain.member.Member;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.member.MemberStatus;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.OrderFactory;
import com.eventsystem.domain.shared.Money;
import com.eventsystem.application.event.EventCatalogService;
import com.eventsystem.infrastructure.persistence.InMemoryActiveOrderRepository;
import com.eventsystem.infrastructure.persistence.InMemoryDiscountPolicyRepository;
import com.eventsystem.infrastructure.persistence.InMemoryEventRepository;
import com.eventsystem.infrastructure.persistence.InMemoryLotteryRepository;
import com.eventsystem.infrastructure.persistence.InMemoryMemberRepository;
import com.eventsystem.infrastructure.persistence.InMemoryPlatformRepository;
import com.eventsystem.infrastructure.persistence.InMemoryProductionCompanyRepository;
import com.eventsystem.infrastructure.persistence.InMemoryPurchasePolicyRepository;
import com.eventsystem.infrastructure.persistence.InMemoryPurchaseRecordRepository;
import com.eventsystem.infrastructure.persistence.InMemoryVenueRepository;
import com.eventsystem.infrastructure.persistence.InMemoryVirtualQueueRepository;
import com.eventsystem.infrastructure.persistence.InMemoryZoneRepository;
import com.eventsystem.infrastructure.security.BCryptPasswordHasher;
import com.eventsystem.infrastructure.security.JwtTokenService;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Objects;

@Configuration
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
    public IMemberRepository memberRepository() {
        return new InMemoryMemberRepository();
    }

    @Bean
    public IPlatformRepository platformRepository() {
        return new InMemoryPlatformRepository();
    }

    @Bean
    public ILotteryRepository lotteryRepository() {
        return new InMemoryLotteryRepository();
    }

    @Bean
    public IActiveOrderRepository activeOrderRepository() {
        return new InMemoryActiveOrderRepository();
    }

    @Bean
    public IZoneRepository zoneRepository() {
        return new InMemoryZoneRepository();
    }

    @Bean
    public IEventRepository eventRepository() {
        return new InMemoryEventRepository();
    }

    @Bean
    public IProductionCompanyRepository productionCompanyRepository() {
        return new InMemoryProductionCompanyRepository();
    }

    @Bean
    public IPurchaseRecordRepository purchaseRecordRepository() {
        return new InMemoryPurchaseRecordRepository();
    }

    @Bean
    public IVenueRepository venueRepository() {
        return new InMemoryVenueRepository();
    }

    @Bean
    public IVirtualQueueRepository virtualQueueRepository() {
        return new InMemoryVirtualQueueRepository();
    }

    @Bean
    public IPurchasePolicyRepository purchasePolicyRepository() {
        return new InMemoryPurchasePolicyRepository();
    }

    @Bean
    public IDiscountPolicyRepository discountPolicyRepository() {
        return new InMemoryDiscountPolicyRepository();
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


    /**
     * Adapter from the member repository to the member-info port used by the policy services
     * when creating a PurchaseContext from a BuyerReference.
     */
    @Bean
    public IMemberInformationPort memberInformationPort(IMemberRepository memberRepository) {
        return new IMemberInformationPort() {
            @Override
            public LocalDate getMemberBirthdate(MemberId memberId) {
                Member member = loadMember(memberId);
                if (member.getPersonalDetails() == null) {
                    throw new IllegalStateException("member has no personal details: " + memberId);
                }
                return member.getPersonalDetails().dateOfBirth();
            }

            @Override
            public MemberStatus getMemberStatus(MemberId memberId) {
                return loadMember(memberId).getStatus();
            }

            private Member loadMember(MemberId memberId) {
                Objects.requireNonNull(memberId, "memberId must not be null");
                return memberRepository.findById(memberId)
                        .orElseThrow(() -> new IllegalArgumentException("member not found: " + memberId));
            }
        };
    }

    // ==========================================
    // 2. Application Services
    // ==========================================
    @Bean
    public AuthService authService(IMemberRepository memberRepo,
                                   BCryptPasswordHasher passwordHasher,
                                   ITokenService tokenService) {
        return new AuthService(memberRepo, passwordHasher, tokenService, tokenValidity);
    }

    @Bean
    public MemberService memberService(IMemberRepository memberRepo) {
        return new MemberService(memberRepo);
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
    public NotificationService notificationService(IMemberRepository memberRepo,
                                                   com.eventsystem.application.member.NotificationBroadcaster broadcaster) {
        return new NotificationService(memberRepo, broadcaster);
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
                                                                      IMemberInformationPort memberInformationPort) {
        return new PurchasePolicyValidationService(purchasePolicyRepository, eventManagementPort, memberInformationPort);
    }

    @Bean
    public IDiscountApplicationPort discountApplicationPort(IDiscountPolicyRepository discountPolicyRepository,
                                                            IEventManagementPort eventManagementPort,
                                                            IMemberInformationPort memberInformationPort) {
        return new DiscountApplicationService(discountPolicyRepository,eventManagementPort,memberInformationPort);
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
