package com.eventsystem.infrastructure.api.admin;

import com.eventsystem.application.admin.AdminService;
import com.eventsystem.application.admin.SuspensionDto;
import com.eventsystem.domain.member.MemberId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;

/**
 * REST endpoints for system-administrator operations.
 * UC II.6.7 — suspend member
 * UC II.6.8 — unsuspend member
 * UC II.6.9 — list suspensions
 *
 * Every request must carry a valid JWT (enforced by
 * {@link com.eventsystem.infrastructure.security.AuthenticationInterceptor}),
 * which resolves to an {@code authenticatedMemberId} request attribute.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    /**
     * POST /api/admin/members/{memberId}/suspend
     * Body (optional): { "durationDays": 7 }  — omit or 0 for permanent
     */
    @PostMapping("/members/{memberId}/suspend")
    public ResponseEntity<Void> suspendMember(
            @RequestAttribute("authenticatedMemberId") MemberId actor,
            @PathVariable String memberId,
            @RequestBody(required = false) SuspendRequest body) {

        Duration duration = (body != null && body.durationDays() != null && body.durationDays() > 0)
                ? Duration.ofDays(body.durationDays())
                : null;

        adminService.suspendMember(actor, new MemberId(memberId), duration);
        return ResponseEntity.ok().build();
    }

    /**
     * DELETE /api/admin/members/{memberId}/suspend
     */
    @DeleteMapping("/members/{memberId}/suspend")
    public ResponseEntity<Void> unsuspendMember(
            @RequestAttribute("authenticatedMemberId") MemberId actor,
            @PathVariable String memberId) {

        adminService.unsuspendMember(actor, new MemberId(memberId));
        return ResponseEntity.ok().build();
    }

    /**
     * GET /api/admin/suspensions
     * Returns all currently suspended members with suspension details.
     */
    @GetMapping("/suspensions")
    public ResponseEntity<List<SuspensionDto>> listSuspensions(
            @RequestAttribute("authenticatedMemberId") MemberId actor) {

        return ResponseEntity.ok(adminService.listSuspensions(actor));
    }

    record SuspendRequest(Integer durationDays) {}
}
