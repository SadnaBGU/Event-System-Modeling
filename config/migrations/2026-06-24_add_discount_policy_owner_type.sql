-- ─────────────────────────────────────────────────────────────────────────────
-- Migration: add discount_policies.owner_type  (2026-06-24)
--
-- WHY: The domain model gives every policy a PolicyOwnerType (COMPANY or EVENT).
--      Hibernate's `ddl-auto: update` adds NEW columns, but it CANNOT add a NOT NULL
--      column to a table that already holds rows. On the live Google Cloud SQL instance
--      `discount_policies` already had data, so the `owner_type` column was never created
--      there (the sibling `purchase_policies` table did get it). Any read of discount
--      policies (e.g. during checkout price calculation) then fails with:
--          ERROR: column dp1_0.owner_type does not exist
--
-- WHAT: Add the column, backfill existing rows as 'COMPANY' (all legacy discount policies
--       were company-wide), then enforce NOT NULL + the COMPANY/EVENT check constraint,
--       matching what Hibernate generates for a fresh schema.
--
-- SAFE TO RE-RUN: every statement is idempotent.
--
-- HOW TO APPLY (PowerShell, from repo root):
--   docker run --rm -i -e PGPASSWORD=$env:DB_PASSWORD postgres:15-alpine `
--     psql "host=$env:DB_IP port=$env:DB_PORT dbname=eventsystem_db user=$env:DB_USERNAME sslmode=require" `
--     -v ON_ERROR_STOP=1 -f - < config/migrations/2026-06-24_add_discount_policy_owner_type.sql
-- ─────────────────────────────────────────────────────────────────────────────

BEGIN;

-- 1. Add the column as nullable first (so it can be added to a populated table).
ALTER TABLE discount_policies
    ADD COLUMN IF NOT EXISTS owner_type varchar(255);

-- 2. Backfill legacy rows: all pre-existing discount policies are company-scoped.
UPDATE discount_policies
    SET owner_type = 'COMPANY'
    WHERE owner_type IS NULL;

-- 3. Enforce NOT NULL now that every row has a value.
ALTER TABLE discount_policies
    ALTER COLUMN owner_type SET NOT NULL;

-- 4. Add the COMPANY/EVENT check constraint (guarded so re-runs don't fail).
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'discount_policies_owner_type_check'
    ) THEN
        ALTER TABLE discount_policies
            ADD CONSTRAINT discount_policies_owner_type_check
            CHECK (owner_type IN ('COMPANY', 'EVENT'));
    END IF;
END $$;

COMMIT;
