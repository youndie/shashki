-- One receipt per settlement, whatever petich does to the pass that sends it (B-92).
--
-- `PublishSettled` sends the mail and then emits, and petich advances a saga's position only after a
-- member's body returns — so anything that makes that pass run again makes the mail go out again.
-- That is not the crash window it looks like: `processWithRetry` re-runs the whole pass on an
-- optimistic-lock conflict, five times by default, so two requests touching one settlement are
-- enough. petich measured six sends for one saga under exactly that.
--
-- **Why a table and not the payload.** `Settled.RECEIPT` is written by the pass that did not commit,
-- which is the case this exists for; and SMTP deduplicates nothing, so naming the send buys nothing
-- either. What is left is a claim written BEFORE the mail, outside the saga's transaction, so that a
-- re-run finds it already taken.
--
-- **The trade this accepts, and the product already accepted it.** A process dying between the claim
-- and the send loses that receipt: at most once. `SendReceiptUseCase` already swallows a send failure
-- — "a settlement that rolled back because a mail server was down would be the tail wagging the dog"
-- — and `Settled.RECEIPT` exists so a ride whose receipt never went can be found. A missing receipt
-- is something this system notices and lives with. A duplicate is not.
CREATE TABLE receipt_claims (
    claim_key  VARCHAR(255) PRIMARY KEY,
    ride_id    VARCHAR(255) NOT NULL,
    claimed_at BIGINT       NOT NULL
);

-- **What removes them**, because a row per settlement kept for ever is a table that only grows. The
-- claim is only consulted while a settlement saga can still be re-driven, which is bounded by how
-- long a saga lives; anything older than that answers a question nobody will ask. There is no job
-- here to do it — this is a demo database — so the index is what a prune would read, and the note is
-- the instruction: `DELETE FROM receipt_claims WHERE claimed_at < <now - retention>`.
CREATE INDEX receipt_claims_claimed_at ON receipt_claims (claimed_at);
