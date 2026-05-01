/**
 * Infrastructure — in-memory repository adapters.
 *
 * <p>One file per repository port. All use {@code ConcurrentHashMap} +
 * {@code putIfAbsent} for uniqueness. No JPA, no DB in V1.
 *
 * <p>Naming convention: {@code InMemory<AggregateName>Repository}.
 */
package com.eventsystem.infrastructure.persistence;
