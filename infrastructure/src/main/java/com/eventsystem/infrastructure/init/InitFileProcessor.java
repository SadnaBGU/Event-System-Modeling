package com.eventsystem.infrastructure.init;

import com.eventsystem.application.auth.AuthService;
import com.eventsystem.application.auth.LoginRequest;
import com.eventsystem.application.auth.LoginResponse;
import com.eventsystem.application.auth.RegisterMemberRequest;
import com.eventsystem.application.company.ProductionCompanyService;
import com.eventsystem.application.event.EventService;
import com.eventsystem.application.event.ZoneService;
import com.eventsystem.application.lottery.LotteryService;
import com.eventsystem.application.member.MemberService;
import com.eventsystem.application.order.ActiveOrderDTO;
import com.eventsystem.application.order.CheckoutSaga;
import com.eventsystem.application.order.OrderService;
import com.eventsystem.application.policy.PolicyManagementService;
import com.eventsystem.application.policy.policybuilder.DiscountCommand;
import com.eventsystem.application.policy.policybuilder.DiscountPolicyCommand;
import com.eventsystem.application.policy.policybuilder.PolicyOwnerCommand;
import com.eventsystem.application.policy.policybuilder.PolicyRuleCommand;
import com.eventsystem.application.policy.policybuilder.PolicyScopeCommand;
import com.eventsystem.application.policy.policybuilder.PurchasePolicyCommand;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.company.Permission;
import com.eventsystem.domain.event.EventDetails;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.lottery.LotteryId;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.BuyerType;
import com.eventsystem.domain.shared.Money;
import com.eventsystem.domain.zone.Row;
import com.eventsystem.domain.zone.Seat;
import com.eventsystem.domain.zone.SeatId;
import com.eventsystem.domain.zone.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Executes the commands of an initial-state file against the <b>application
 * layer</b> (never the repositories directly), so only legal use-case flows can
 * run — e.g. a company cannot be opened without first logging in (V3 appendix).
 *
 * <h2>Atomicity</h2>
 * {@link #process(List)} is annotated {@link Transactional}: the whole file runs
 * inside a single database transaction. If any command fails, the transaction is
 * rolled back and the exception propagates, so <b>nothing</b> is persisted and
 * application startup aborts ("all-or-nothing", team task 2.1).
 *
 * <h2>Supported commands</h2>
 * See {@code docs/INIT_FILE_README} / the project README for the full grammar.
 */
@Component
public class InitFileProcessor {

    private static final Logger log = LoggerFactory.getLogger(InitFileProcessor.class);

    private final MemberService memberService;
    private final AuthService authService;
    private final ProductionCompanyService companyService;
    private final EventService eventService;
    private final ZoneService zoneService;
    private final LotteryService lotteryService;
    private final OrderService orderService;
    private final CheckoutSaga checkoutSaga;
    private final PolicyManagementService policyService;

    public InitFileProcessor(MemberService memberService,
                             AuthService authService,
                             ProductionCompanyService companyService,
                             EventService eventService,
                             ZoneService zoneService,
                             LotteryService lotteryService,
                             OrderService orderService,
                             CheckoutSaga checkoutSaga,
                             PolicyManagementService policyService) {
        this.memberService = memberService;
        this.authService = authService;
        this.companyService = companyService;
        this.eventService = eventService;
        this.zoneService = zoneService;
        this.lotteryService = lotteryService;
        this.orderService = orderService;
        this.checkoutSaga = checkoutSaga;
        this.policyService = policyService;
    }

    /**
     * Run all commands in order inside one transaction. Any failure rolls back
     * every preceding command and rethrows as {@link InitFileException}.
     */
    @Transactional
    public void process(List<InitCommand> commands) {
        InitContext ctx = new InitContext();
        log.info("Init-state: executing {} command(s) in a single transaction", commands.size());
        for (InitCommand cmd : commands) {
            try {
                dispatch(cmd, ctx);
            } catch (InitFileException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new InitFileException(cmd.lineNumber(),
                        "command '" + cmd.name() + "' failed: " + e.getMessage(), e);
            }
        }
        log.info("Init-state: all {} command(s) executed successfully", commands.size());
    }

    private void dispatch(InitCommand cmd, InitContext ctx) {
        switch (cmd.name()) {
            case "register", "guest-registration" -> register(cmd, ctx);
            case "login" -> login(cmd, ctx);
            case "open-production-company" -> openCompany(cmd, ctx);
            case "appoint-owner" -> appointOwner(cmd, ctx);
            case "appoint-manager" -> appointManager(cmd, ctx);
            case "accept-appointment" -> acceptAppointment(cmd, ctx);
            case "modify-manager-permissions" -> modifyManagerPermissions(cmd, ctx);
            case "remove-appointee" -> removeAppointee(cmd, ctx);
            case "close-production-company" -> closeCompany(cmd, ctx);
            case "reopen-production-company" -> reopenCompany(cmd, ctx);
            case "create-event" -> createEvent(cmd, ctx);
            case "create-seated-zone" -> createSeatedZone(cmd, ctx);
            case "create-standing-zone" -> createStandingZone(cmd, ctx);
            case "publish-event" -> publishEvent(cmd, ctx);
            case "open-lottery" -> openLottery(cmd, ctx);
            case "register-lottery" -> registerLottery(cmd, ctx);
            case "draw-lottery" -> drawLottery(cmd, ctx);
            case "set-purchase-policy" -> setPurchasePolicy(cmd, ctx);
            case "set-discount-policy" -> setDiscountPolicy(cmd, ctx);
            case "reserve" -> reserve(cmd, ctx);
            case "checkout" -> checkout(cmd, ctx);
            default -> throw new InitFileException(cmd.lineNumber(),
                    "unknown command '" + cmd.name() + "'");
        }
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    private void register(InitCommand cmd, InitContext ctx) {
        requireArity(cmd, 6);
        String username = arg(cmd, 0);
        MemberId id = memberService.register(new RegisterMemberRequest(
                username,
                arg(cmd, 1),
                arg(cmd, 2),
                arg(cmd, 3),
                arg(cmd, 4),
                localDate(cmd, 5)));
        ctx.putMember(username, id);
    }

    private void login(InitCommand cmd, InitContext ctx) {
        requireArity(cmd, 2);
        String username = arg(cmd, 0);
        LoginResponse resp = memberService.login(new LoginRequest(username, arg(cmd, 1)));
        ctx.putMember(username, resp.memberId());
        ctx.putToken(username, resp.token());
    }

    // ── Company ───────────────────────────────────────────────────────────────

    private void openCompany(InitCommand cmd, InitContext ctx) {
        requireArity(cmd, 5);
        MemberId founder = actor(cmd, 0, ctx);
        String alias = arg(cmd, 1);
        CompanyId id = companyService.createCompany(
                founder, arg(cmd, 2), arg(cmd, 3), parseDouble(cmd, 4));
        ctx.putCompany(alias, id);
    }

    private void appointOwner(InitCommand cmd, InitContext ctx) {
        requireArity(cmd, 3);
        MemberId actor = actor(cmd, 0, ctx);
        CompanyId company = ctx.company(arg(cmd, 1), cmd.lineNumber());
        MemberId target = ctx.member(arg(cmd, 2), cmd.lineNumber());
        companyService.appointOwner(company, actor, target);
    }

    private void appointManager(InitCommand cmd, InitContext ctx) {
        requireArity(cmd, 4);
        MemberId actor = actor(cmd, 0, ctx);
        CompanyId company = ctx.company(arg(cmd, 1), cmd.lineNumber());
        MemberId target = ctx.member(arg(cmd, 2), cmd.lineNumber());
        companyService.appointManager(company, actor, target, permissions(cmd, 3));
    }

    private void acceptAppointment(InitCommand cmd, InitContext ctx) {
        requireArity(cmd, 2);
        MemberId target = ctx.member(arg(cmd, 0), cmd.lineNumber());
        CompanyId company = ctx.company(arg(cmd, 1), cmd.lineNumber());
        companyService.acceptAppointment(company, target);
    }

    private void modifyManagerPermissions(InitCommand cmd, InitContext ctx) {
        requireArity(cmd, 4);
        MemberId actor = actor(cmd, 0, ctx);
        CompanyId company = ctx.company(arg(cmd, 1), cmd.lineNumber());
        MemberId manager = ctx.member(arg(cmd, 2), cmd.lineNumber());
        companyService.modifyManagerPermissions(company, actor, manager, permissions(cmd, 3));
    }

    private void removeAppointee(InitCommand cmd, InitContext ctx) {
        requireArity(cmd, 3);
        MemberId actor = actor(cmd, 0, ctx);
        CompanyId company = ctx.company(arg(cmd, 1), cmd.lineNumber());
        MemberId target = ctx.member(arg(cmd, 2), cmd.lineNumber());
        companyService.removeAppointee(company, actor, target);
    }

    private void closeCompany(InitCommand cmd, InitContext ctx) {
        requireArity(cmd, 2);
        actor(cmd, 0, ctx); // must be logged in
        CompanyId company = ctx.company(arg(cmd, 1), cmd.lineNumber());
        companyService.suspendCompany(company);
    }

    private void reopenCompany(InitCommand cmd, InitContext ctx) {
        requireArity(cmd, 2);
        actor(cmd, 0, ctx); // must be logged in
        CompanyId company = ctx.company(arg(cmd, 1), cmd.lineNumber());
        companyService.reopenCompany(company);
    }

    // ── Events & zones ────────────────────────────────────────────────────────

    private void createEvent(InitCommand cmd, InitContext ctx) {
        requireArity(cmd, 8);
        MemberId actor = actor(cmd, 0, ctx);
        CompanyId company = ctx.company(arg(cmd, 1), cmd.lineNumber());
        String alias = arg(cmd, 2);
        List<LocalDateTime> dates = dateTimes(cmd, 4);
        EventDetails details = new EventDetails(
                arg(cmd, 3), dates, arg(cmd, 5), arg(cmd, 6), arg(cmd, 7));
        EventId id = eventService.createDraft(actor, company, details);
        ctx.putEvent(alias, id);
    }

    private void createSeatedZone(InitCommand cmd, InitContext ctx) {
        requireArity(cmd, 7);
        MemberId actor = actor(cmd, 0, ctx);
        String eventAlias = arg(cmd, 1);
        EventId event = ctx.event(eventAlias, cmd.lineNumber());
        String zoneAlias = arg(cmd, 2);
        Money price = money(cmd, 4, 5);
        List<Row> rows = rows(cmd, 6);
        ZoneId zoneId = zoneService.createSeatedZone(event, arg(cmd, 3), price, rows);
        eventService.addZone(actor, event, zoneId);
        ctx.putZone(zoneAlias, zoneId, eventAlias);
    }

    private void createStandingZone(InitCommand cmd, InitContext ctx) {
        requireArity(cmd, 7);
        MemberId actor = actor(cmd, 0, ctx);
        String eventAlias = arg(cmd, 1);
        EventId event = ctx.event(eventAlias, cmd.lineNumber());
        String zoneAlias = arg(cmd, 2);
        Money price = money(cmd, 4, 5);
        int capacity = parseInt(cmd, 6);
        ZoneId zoneId = zoneService.createStandingZone(event, arg(cmd, 3), price, capacity);
        eventService.addZone(actor, event, zoneId);
        ctx.putZone(zoneAlias, zoneId, eventAlias);
    }

    private void publishEvent(InitCommand cmd, InitContext ctx) {
        requireArity(cmd, 2);
        MemberId actor = actor(cmd, 0, ctx);
        EventId event = ctx.event(arg(cmd, 1), cmd.lineNumber());
        eventService.publish(actor, event);
    }

    // ── Lottery ───────────────────────────────────────────────────────────────

    private void openLottery(InitCommand cmd, InitContext ctx) {
        requireArity(cmd, 3);
        actor(cmd, 0, ctx); // must be logged in
        EventId event = ctx.event(arg(cmd, 1), cmd.lineNumber());
        LotteryId id = lotteryService.openLottery(event);
        ctx.putLottery(arg(cmd, 2), id);
    }

    private void registerLottery(InitCommand cmd, InitContext ctx) {
        requireArity(cmd, 2);
        MemberId member = ctx.member(arg(cmd, 0), cmd.lineNumber());
        LotteryId lottery = ctx.lottery(arg(cmd, 1), cmd.lineNumber());
        lotteryService.register(member, lottery);
    }

    private void drawLottery(InitCommand cmd, InitContext ctx) {
        requireArity(cmd, 3);
        actor(cmd, 0, ctx); // must be logged in
        LotteryId lottery = ctx.lottery(arg(cmd, 1), cmd.lineNumber());
        lotteryService.draw(lottery, parseInt(cmd, 2));
    }

    // ── Policies ──────────────────────────────────────────────────────────────

    private void setPurchasePolicy(InitCommand cmd, InitContext ctx) {
        requireArity(cmd, 5);
        MemberId actor = actor(cmd, 0, ctx);
        CompanyId company = ctx.company(arg(cmd, 1), cmd.lineNumber());
        PolicyRuleCommand rule = new PolicyRuleCommand(
                arg(cmd, 3).toUpperCase(), parseInt(cmd, 4), null, null, null, null);
        PurchasePolicyCommand command = new PurchasePolicyCommand(
                actor.value(),
                company.value(),
                arg(cmd, 2),
                new PolicyScopeCommand(true, Set.of()),
                rule,
                true,
                PolicyOwnerCommand.COMPANY);
        policyService.createPurchasePolicy(command);
    }

    private void setDiscountPolicy(InitCommand cmd, InitContext ctx) {
        requireArity(cmd, 5);
        MemberId actor = actor(cmd, 0, ctx);
        CompanyId company = ctx.company(arg(cmd, 1), cmd.lineNumber());
        DiscountCommand discount = new DiscountCommand(
                arg(cmd, 3),
                new BigDecimal(arg(cmd, 4)),
                new PolicyRuleCommand("ALLOW_ALL", null, null, null, null, null));
        DiscountPolicyCommand command = new DiscountPolicyCommand(
                actor.value(),
                company.value(),
                arg(cmd, 2),
                new PolicyScopeCommand(true, Set.of()),
                List.of(discount),
                false,
                true,
                PolicyOwnerCommand.COMPANY);
        policyService.createDiscountPolicy(command);
    }

    // ── Reservation & checkout ────────────────────────────────────────────────

    private void reserve(InitCommand cmd, InitContext ctx) {
        requireArity(cmd, 4);
        MemberId buyer = ctx.member(arg(cmd, 0), cmd.lineNumber());
        String orderAlias = arg(cmd, 1);
        String zoneAlias = arg(cmd, 2);
        int quantity = parseInt(cmd, 3);

        String eventAlias = ctx.zoneEvent(zoneAlias, cmd.lineNumber());
        EventId event = ctx.event(eventAlias, cmd.lineNumber());
        ZoneId zone = ctx.zone(zoneAlias, cmd.lineNumber());

        BuyerReference ref = new BuyerReference(BuyerType.MEMBER, null, buyer.value());
        ActiveOrderDTO order = orderService.createOrGetActiveOrder(
                ref, event.value(), Optional.empty());
        ctx.putOrder(orderAlias, order.orderId());
        orderService.addItemToOrder(order.orderId(), zone.value(), "GA", quantity);
    }

    private void checkout(InitCommand cmd, InitContext ctx) {
        requireArity(cmd, 3);
        String orderId = ctx.order(arg(cmd, 0), cmd.lineNumber());
        String paymentToken = arg(cmd, 1);
        String discountCode = arg(cmd, 2);
        checkoutSaga.executeCheckout(orderId, paymentToken,
                discountCode.isBlank() ? null : discountCode);
    }

    // ── Argument helpers ──────────────────────────────────────────────────────

    private MemberId actor(InitCommand cmd, int index, InitContext ctx) {
        String username = arg(cmd, index);
        String token = ctx.token(username, cmd.lineNumber());
        return authService.authenticate(token);
    }

    private Set<Permission> permissions(InitCommand cmd, int index) {
        String raw = arg(cmd, index).trim();
        if (raw.isEmpty() || raw.equalsIgnoreCase("NONE")) {
            return EnumSet.noneOf(Permission.class);
        }
        if (raw.equalsIgnoreCase("ALL")) {
            return EnumSet.allOf(Permission.class);
        }
        try {
            return Arrays.stream(raw.split("\\|"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> Permission.valueOf(s.toUpperCase()))
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(Permission.class)));
        } catch (IllegalArgumentException e) {
            throw new InitFileException(cmd.lineNumber(),
                    "invalid permission in '" + raw + "'. Valid: "
                            + Arrays.toString(Permission.values()) + ", or ALL/NONE");
        }
    }

    private Money money(InitCommand cmd, int amountIndex, int currencyIndex) {
        try {
            return Money.of(new BigDecimal(arg(cmd, amountIndex)), arg(cmd, currencyIndex));
        } catch (NumberFormatException e) {
            throw new InitFileException(cmd.lineNumber(),
                    "invalid price amount '" + arg(cmd, amountIndex) + "'");
        }
    }

    /** rowSpec format: {@code A:10|B:8} — row label : seat count, '|' separated. */
    private List<Row> rows(InitCommand cmd, int index) {
        String spec = arg(cmd, index).trim();
        if (spec.isEmpty()) {
            throw new InitFileException(cmd.lineNumber(), "seated zone needs a row spec like A:10|B:8");
        }
        List<Row> rows = new ArrayList<>();
        for (String part : spec.split("\\|")) {
            String[] kv = part.split(":");
            if (kv.length != 2) {
                throw new InitFileException(cmd.lineNumber(),
                        "invalid row spec '" + part + "' (expected label:count)");
            }
            String label = kv[0].trim();
            int count;
            try {
                count = Integer.parseInt(kv[1].trim());
            } catch (NumberFormatException e) {
                throw new InitFileException(cmd.lineNumber(),
                        "invalid seat count in row spec '" + part + "'");
            }
            if (count < 1) {
                throw new InitFileException(cmd.lineNumber(),
                        "row '" + label + "' must have at least one seat");
            }
            List<Seat> seats = new ArrayList<>();
            for (int n = 1; n <= count; n++) {
                seats.add(new Seat(SeatId.random(), label, n));
            }
            rows.add(new Row(label, seats));
        }
        return rows;
    }

    private List<LocalDateTime> dateTimes(InitCommand cmd, int index) {
        String raw = arg(cmd, index).trim();
        List<LocalDateTime> dates = new ArrayList<>();
        for (String part : raw.split("\\|")) {
            String value = part.trim();
            try {
                dates.add(LocalDateTime.parse(value));
            } catch (RuntimeException e) {
                throw new InitFileException(cmd.lineNumber(),
                        "invalid date-time '" + value + "' (expected ISO e.g. 2026-09-01T20:00)");
            }
        }
        return dates;
    }

    private LocalDate localDate(InitCommand cmd, int index) {
        String value = arg(cmd, index);
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException e) {
            throw new InitFileException(cmd.lineNumber(),
                    "invalid date '" + value + "' (expected yyyy-MM-dd)");
        }
    }

    private int parseInt(InitCommand cmd, int index) {
        String value = arg(cmd, index);
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new InitFileException(cmd.lineNumber(), "expected an integer but got '" + value + "'");
        }
    }

    private double parseDouble(InitCommand cmd, int index) {
        String value = arg(cmd, index);
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            throw new InitFileException(cmd.lineNumber(), "expected a number but got '" + value + "'");
        }
    }

    private String arg(InitCommand cmd, int index) {
        if (index >= cmd.arity()) {
            throw new InitFileException(cmd.lineNumber(),
                    "command '" + cmd.name() + "' is missing argument #" + (index + 1));
        }
        return cmd.args().get(index);
    }

    private void requireArity(InitCommand cmd, int expected) {
        if (cmd.arity() != expected) {
            throw new InitFileException(cmd.lineNumber(),
                    "command '" + cmd.name() + "' expects " + expected
                            + " argument(s) but got " + cmd.arity());
        }
    }
}
