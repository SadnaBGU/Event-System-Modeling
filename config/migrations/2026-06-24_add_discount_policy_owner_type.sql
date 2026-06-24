-- Migration: add discount_policies.owner_type (COMPANY|EVENT), 2026-06-24.
-- ddl-auto=update can't add a NOT NULL column to a populated table, so the live
-- cloud DB never got this column. Idempotent / safe to re-run.

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
