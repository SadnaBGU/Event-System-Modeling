package com.eventsystem.infrastructure.init;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.lottery.LotteryId;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.zone.ZoneId;

import java.util.HashMap;
import java.util.Map;

/**
 * Mutable symbol table threaded through the execution of one initial-state file.
 *
 * <p>The state file refers to entities by human-readable aliases (a username, a
 * company alias, an event alias, ...). As commands execute they register the
 * generated identifiers here so later commands can resolve them — mirroring the
 * doc's {@code rina's token} style references.</p>
 *
 * <p>Lookups that miss raise {@link InitFileException} so a typo in the script
 * aborts the whole transaction.</p>
 */
final class InitContext {

    private final Map<String, MemberId> membersByName = new HashMap<>();
    private final Map<String, String> tokensByName = new HashMap<>();
    private final Map<String, CompanyId> companies = new HashMap<>();
    private final Map<String, EventId> events = new HashMap<>();
    private final Map<String, ZoneId> zones = new HashMap<>();
    private final Map<String, String> zoneEventAlias = new HashMap<>();
    private final Map<String, LotteryId> lotteries = new HashMap<>();
    private final Map<String, String> orders = new HashMap<>();

    // ── Members & tokens ──────────────────────────────────────────────────────

    void putMember(String username, MemberId id) {
        membersByName.put(username, id);
    }

    MemberId member(String username, int line) {
        MemberId id = membersByName.get(username);
        if (id == null) {
            throw new InitFileException(line, "unknown member '" + username
                    + "' (must be registered earlier in the file)");
        }
        return id;
    }

    void putToken(String username, String token) {
        tokensByName.put(username, token);
    }

    String token(String username, int line) {
        String token = tokensByName.get(username);
        if (token == null) {
            throw new InitFileException(line, "member '" + username
                    + "' is not logged in (call login(...) before authenticated actions)");
        }
        return token;
    }

    // ── Companies ─────────────────────────────────────────────────────────────

    void putCompany(String alias, CompanyId id) {
        companies.put(alias, id);
    }

    CompanyId company(String alias, int line) {
        CompanyId id = companies.get(alias);
        if (id == null) {
            throw new InitFileException(line, "unknown company alias '" + alias + "'");
        }
        return id;
    }

    // ── Events ────────────────────────────────────────────────────────────────

    void putEvent(String alias, EventId id) {
        events.put(alias, id);
    }

    EventId event(String alias, int line) {
        EventId id = events.get(alias);
        if (id == null) {
            throw new InitFileException(line, "unknown event alias '" + alias + "'");
        }
        return id;
    }

    // ── Zones ─────────────────────────────────────────────────────────────────

    void putZone(String alias, ZoneId id, String eventAlias) {
        zones.put(alias, id);
        zoneEventAlias.put(alias, eventAlias);
    }

    ZoneId zone(String alias, int line) {
        ZoneId id = zones.get(alias);
        if (id == null) {
            throw new InitFileException(line, "unknown zone alias '" + alias + "'");
        }
        return id;
    }

    String zoneEvent(String zoneAlias, int line) {
        String eventAlias = zoneEventAlias.get(zoneAlias);
        if (eventAlias == null) {
            throw new InitFileException(line, "unknown zone alias '" + zoneAlias + "'");
        }
        return eventAlias;
    }

    // ── Lotteries ─────────────────────────────────────────────────────────────

    void putLottery(String alias, LotteryId id) {
        lotteries.put(alias, id);
    }

    LotteryId lottery(String alias, int line) {
        LotteryId id = lotteries.get(alias);
        if (id == null) {
            throw new InitFileException(line, "unknown lottery alias '" + alias + "'");
        }
        return id;
    }

    // ── Orders ────────────────────────────────────────────────────────────────

    void putOrder(String alias, String orderId) {
        orders.put(alias, orderId);
    }

    String order(String alias, int line) {
        String orderId = orders.get(alias);
        if (orderId == null) {
            throw new InitFileException(line, "unknown order alias '" + alias + "'");
        }
        return orderId;
    }
}
