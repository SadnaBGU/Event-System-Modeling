-- Migration: add lotteries.registration_deadline, 2026-06-28.
-- Existing lotteries were opened without a deadline, so backfill them with the
-- same far-future default used by the legacy application path.

BEGIN;

ALTER TABLE lotteries
    ADD COLUMN IF NOT EXISTS registration_deadline timestamp with time zone;

UPDATE lotteries
    SET registration_deadline = TIMESTAMPTZ '9999-12-31 23:59:59+00'
    WHERE registration_deadline IS NULL;

ALTER TABLE lotteries
    ALTER COLUMN registration_deadline SET NOT NULL;

COMMIT;
