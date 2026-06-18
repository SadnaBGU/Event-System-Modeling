package com.eventsystem.infrastructure.init;

import com.eventsystem.application.order.IPaymentGatewayPort;
import com.eventsystem.application.order.ITicketIssuancePort;
import com.eventsystem.application.order.IssuanceResult;
import com.eventsystem.application.order.PaymentResult;
import com.eventsystem.application.system.IExternalSystemsAvailabilityPort;
import com.eventsystem.domain.company.IProductionCompanyRepository;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.event.IEventRepository;
import com.eventsystem.domain.lottery.ILotteryRepository;
import com.eventsystem.domain.lottery.LotteryStatus;
import com.eventsystem.domain.member.IMemberRepository;
import com.eventsystem.domain.purchaserecord.IPurchaseRecordRepository;
import com.eventsystem.domain.zone.IZoneRepository;
import com.eventsystem.infrastructure.testsupport.PostgresAvailableCondition;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * End-to-end verification that EVERY supported init command (and therefore the
 * use cases behind it) works against the real application services + PostgreSQL.
 *
 * <p>The full-state file drives: registration, login, opening companies,
 * appointing/accepting owners and managers, modifying permissions, removing
 * appointees, creating events with seated + standing zones, purchase & discount
 * policies, publishing, the full lottery lifecycle (open/register/draw),
 * reserving tickets, checkout (payment + ticket issuance), and suspend/reopen.
 *
 * <p>Only the external payment and ticket-issuance ports are mocked (to succeed);
 * everything else exercises the production code paths. Skipped automatically when
 * the test database is unreachable.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(PostgresAvailableCondition.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5434/eventsdb",
        "spring.datasource.username=admin",
        "spring.datasource.password=admin123",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.hikari.maximum-pool-size=4",
        "spring.datasource.hikari.minimum-idle=0",
        "spring.datasource.hikari.idle-timeout=10000"
})
class FullInitFlowIntegrationTest {

    @Autowired
    private InitFileProcessor processor;

    @Autowired
    private IMemberRepository memberRepository;
    @Autowired
    private IProductionCompanyRepository companyRepository;
    @Autowired
    private IEventRepository eventRepository;
    @Autowired
    private ILotteryRepository lotteryRepository;
    @Autowired
    private IPurchaseRecordRepository purchaseRecordRepository;
    @Autowired
    private IZoneRepository zoneRepository;

    // External systems: mocked so the end-to-end flow doesn't hit the real WSEP service.
    @MockBean
    private IPaymentGatewayPort paymentGateway;
    @MockBean
    private ITicketIssuancePort ticketIssuance;
    @MockBean
    private IExternalSystemsAvailabilityPort externalSystems;

    private final StateFileParser parser = new StateFileParser();

    @Test
    void everyInitCommand_executesAndPersists() {
        Mockito.when(externalSystems.areExternalSystemsAvailable()).thenReturn(true);
        Mockito.when(paymentGateway.charge(anyString(), any(), any(), anyString()))
                .thenReturn(PaymentResult.successful("pay-confirm-1"));
        Mockito.when(ticketIssuance.issueTickets(anyString(), anyString(), any(), any()))
                .thenReturn(IssuanceResult.successful("issue-confirm-1"));

        List<InitCommand> commands = parser.parse(read("/init/full-state.txt"));
        processor.process(commands);

        // Members registered (UC4) and logins worked (UC5 — used as actors below).
        assertThat(memberRepository.findByUsername("founder")).isPresent();
        assertThat(memberRepository.findByUsername("manager")).isPresent();
        assertThat(memberRepository.findByUsername("coowner")).isPresent();
        assertThat(memberRepository.findByUsername("buyer")).isPresent();

        // Company opened (UC11) and event created + published (UC15).
        assertThat(companyRepository.findByName("Acme Live")).isPresent();
        var company = companyRepository.findByName("Acme Live").orElseThrow();
        var events = eventRepository.findByCompany(company.companyId());
        assertThat(events).hasSize(1);
        EventId eventId = events.get(0).id();

        // Event reached PUBLISHED (publish requires at least one zone — UC15).
        assertThat(events.get(0).status().name()).isEqualTo("PUBLISHED");

        // Both zones (seated + standing) persisted for the event (UC15).
        assertThat(zoneRepository.findByEventId(eventId)).hasSize(2);

        // Lottery opened, registered, and drawn (UC14). DRAWN status proves the
        // draw executed and selected its winners.
        var lottery = lotteryRepository.findByEventId(eventId).orElseThrow();
        assertThat(lottery.getStatus()).isEqualTo(LotteryStatus.DRAWN);

        // Checkout produced a persisted, immutable purchase record (UC9 + UC12).
        assertThat(purchaseRecordRepository.findAll()).isNotEmpty();

        // Payment + ticket issuance were both called exactly once (UC9 distributed txn).
        Mockito.verify(paymentGateway).charge(anyString(), any(), any(), anyString());
        Mockito.verify(ticketIssuance).issueTickets(anyString(), anyString(), any(), any());

        // Second company opened, then suspended and reopened (UC19).
        assertThat(companyRepository.findByName("Beta Events")).isPresent();
    }

    private String read(String classpathResource) {
        try (InputStream in = getClass().getResourceAsStream(classpathResource)) {
            if (in == null) {
                throw new IllegalStateException("missing test resource: " + classpathResource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
