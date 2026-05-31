package com.eventsystem.infrastructure.api.admin;

import com.eventsystem.application.admin.AdminService;
import com.eventsystem.application.admin.PlatformDto;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.shared.ProviderId;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/admin")
public class AdminPlatformController {

    private final AdminService adminService;

    public AdminPlatformController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/platform")
    public ResponseEntity<PlatformDto> getPlatform(@RequestAttribute("authenticatedMemberId") MemberId actor) {
        return ResponseEntity.ok(adminService.getPlatform(actor));
    }

    @PostMapping("/platform/activate")
    public ResponseEntity<Void> activatePlatform(@RequestAttribute("authenticatedMemberId") MemberId actor) {
        adminService.activate(actor);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/platform/shutdown")
    public ResponseEntity<Void> shutdownPlatform(@RequestAttribute("authenticatedMemberId") MemberId actor) {
        adminService.shutdown(actor);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/admins/{memberId}")
    public ResponseEntity<Void> promoteAdmin(@RequestAttribute("authenticatedMemberId") MemberId actor,
                                             @PathVariable String memberId) {
        adminService.addAdmin(actor, new MemberId(memberId));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/admins/{memberId}")
    public ResponseEntity<Void> demoteAdmin(@RequestAttribute("authenticatedMemberId") MemberId actor,
                                            @PathVariable String memberId) {
        adminService.removeAdmin(actor, new MemberId(memberId));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/payment-providers/{providerId}")
    public ResponseEntity<Void> addPaymentProvider(@RequestAttribute("authenticatedMemberId") MemberId actor,
                                                   @PathVariable String providerId) {
        adminService.addPaymentProvider(actor, new ProviderId(providerId));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/payment-providers/{providerId}")
    public ResponseEntity<Void> removePaymentProvider(@RequestAttribute("authenticatedMemberId") MemberId actor,
                                                      @PathVariable String providerId) {
        adminService.removePaymentProvider(actor, new ProviderId(providerId));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/issuance-providers/{providerId}")
    public ResponseEntity<Void> addIssuanceProvider(@RequestAttribute("authenticatedMemberId") MemberId actor,
                                                    @PathVariable String providerId) {
        adminService.addIssuanceProvider(actor, new ProviderId(providerId));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/issuance-providers/{providerId}")
    public ResponseEntity<Void> removeIssuanceProvider(@RequestAttribute("authenticatedMemberId") MemberId actor,
                                                       @PathVariable String providerId) {
        adminService.removeIssuanceProvider(actor, new ProviderId(providerId));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/platform/reservation-timeout")
    public ResponseEntity<Void> setReservationTimeout(@RequestAttribute("authenticatedMemberId") MemberId actor,
                                                      @RequestParam String timeout) {
        adminService.setDefaultReservationTimeout(actor, Duration.parse(timeout));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/platform/queue-load-threshold")
    public ResponseEntity<Void> setQueueLoadThreshold(@RequestAttribute("authenticatedMemberId") MemberId actor,
                                                      @RequestParam int threshold) {
        adminService.setQueueLoadThreshold(actor, threshold);
        return ResponseEntity.noContent().build();
    }
}