/**
 * VirtualQueue aggregate (sliding-window admission control).
 *
 * <p>Owns: {@code VirtualQueue} aggregate root + {@code AdmissionToken} +
 * {@code VirtualQueueRepository} port. Methods: {@code admitNext()}, {@code revokeAdmission()}.
 *
 * <p>See: {@code docs/7_VirtualQueue.mmd}.
 */
package com.eventsystem.domain.queue;
