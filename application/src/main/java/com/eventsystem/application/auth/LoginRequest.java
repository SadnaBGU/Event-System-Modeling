package com.eventsystem.application.auth;

/** Input DTO for {@link AuthService#login(LoginRequest)}. */
public record LoginRequest(String username, String plaintextPassword) {
}
