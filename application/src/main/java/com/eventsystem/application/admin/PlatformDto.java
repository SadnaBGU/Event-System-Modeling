package com.eventsystem.application.admin;

import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.platform.PlatformStatus;
import com.eventsystem.domain.shared.ProviderId;

import java.time.Duration;
import java.util.Set;

/** Read-only projection of the {@link com.eventsystem.domain.platform.Platform} aggregate. */
public record PlatformDto(
        PlatformStatus status,
        Set<MemberId> systemAdmins,
        Set<ProviderId> paymentProviders,
        Set<ProviderId> issuanceProviders,
        Duration defaultReservationTimeout,
        int queueLoadThreshold) {
}
