package com.eventsystem.application.auth;

import com.eventsystem.domain.member.MemberId;

import java.time.Instant;

/** Output DTO returned on successful login: bearer token + metadata. */
public record LoginResponse(String token, MemberId memberId, Instant expiresAt) {
}
