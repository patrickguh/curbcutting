ALTER TABLE scan_jobs ADD COLUMN IF NOT EXISTS is_example BOOLEAN NOT NULL DEFAULT false;

-- Scans now belong to accounts. Scans submitted before accounts existed have
-- no owner and are no longer reachable by anyone under the new privacy model,
-- so remove them; the curated is_example scan takes their place as the one
-- publicly-viewable demo.
DELETE FROM scan_jobs WHERE owner_id IS NULL;
