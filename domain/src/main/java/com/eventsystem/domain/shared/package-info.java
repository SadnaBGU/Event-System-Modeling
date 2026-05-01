/**
 * Shared kernel — value objects and primitives reused across multiple bounded contexts.
 *
 * <p>Currently planned: {@code ProviderId}.
 *
 * <p>Add new shared types here ONLY if they are genuinely cross-cutting.
 * Bounded-context-specific types belong in their own packages.
 */
package com.eventsystem.domain.shared;
