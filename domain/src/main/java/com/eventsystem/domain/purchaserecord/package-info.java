/**
 * PurchaseRecord aggregate (immutable audit trail).
 *
 * <p>Owns: {@code PurchaseRecord} aggregate root + snapshot value objects +
 * {@code PurchaseRecordRepository} port. Append-only.
 *
 * <p>See: {@code docs/5_PurchaseRecord.mmd}.
 */
package com.eventsystem.domain.purchaserecord;
