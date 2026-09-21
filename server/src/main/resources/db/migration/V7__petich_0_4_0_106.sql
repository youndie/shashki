-- petich 0.4.0.106 keeps two more things on the saga row, and both are about a rollback that is
-- already running: where to carry on undoing from, and what the saga is to be called once it is
-- undone (petich B-53 and B-54).
--
-- Both are empty for a saga that is not rolling back, so a schema without them loses nothing until a
-- process dies mid-rollback — at which point the pass that picks the saga up starts from the wrong
-- member and finishes a refusal as a system failure. A saga interrupted under the old schema and
-- resumed under this one still carries nothing in these columns and is finished the old way; that is
-- petich's documented behaviour, not a gap in this script.
--
-- Same lock timeout as V5 and for the same reason: `petiches` is the busiest table here, and a
-- statement waiting on ACCESS EXCLUSIVE queues every later reader behind it. Both statements below
-- are catalogue changes rather than table rewrites — nullable, no default — so the lock is held for
-- the time it takes to write two catalogue rows.
SET lock_timeout = '3s';

-- One past the next member to compensate. Written when the rollback starts and by every step of it,
-- so a resumed rollback does not undo the same member twice.
ALTER TABLE petiches ADD COLUMN IF NOT EXISTS compensating_from_index INT;

-- What the rollback will end as: FAILED for a fault, REJECTED when a member refused the saga on
-- business grounds. Without it a refusal interrupted mid-rollback was finished as FAILED, and the
-- client asking again was told the server had broken rather than that it was refused.
--
-- INT AND VARCHAR RATHER THAN THE JSON V5 NEEDED. The spelling difference V5 documents is about
-- petich's JSON-shaped columns, where `PetichTable` declares `json()` and the native store's schema
-- prints TEXT. These two are `integer()` and `varchar(…, 32)` in `PetichTable` and `INT` and
-- `VARCHAR(32)` in the native schema — the same in both, so petich's upgrade notes can be copied
-- here as they stand.
ALTER TABLE petiches ADD COLUMN IF NOT EXISTS compensating_towards VARCHAR(32);
