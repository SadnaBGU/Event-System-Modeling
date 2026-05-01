/**
 * Security ports (interfaces only — adapters live in infrastructure).
 *
 * <p>Owns: {@code PasswordHasher} + {@code TokenService} interfaces.
 * Adapters: {@code BCryptPasswordHasher}, {@code JwtTokenService} in infrastructure.
 */
package com.eventsystem.application.security;
