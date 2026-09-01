-- petich's two tables, hand-written because petich-postgres ships no DDL on purpose: the tables
-- describe themselves in Kotlin, and SchemaTest asks Exposed whether this file agrees with them.
-- A column type wrong here surfaces as a saga that cannot be written, in whichever request runs
-- one first — which is why the test exists rather than a note.

CREATE TABLE petiches (
    id                        VARCHAR(255) PRIMARY KEY,
    type                      VARCHAR(100) NOT NULL,
    current_phase             VARCHAR(50)  NOT NULL,
    current_interceptor_index INT          NOT NULL,
    status                    VARCHAR(50)  NOT NULL,
    payload                   JSON         NOT NULL,
    enriched_payload          JSON         NOT NULL,
    version                   BIGINT       NOT NULL,
    suspended_until           BIGINT       NULL
);
CREATE INDEX idx_petiches_status_suspended_until ON petiches (status, suspended_until);

CREATE TABLE outbox_events (
    id          VARCHAR(255) PRIMARY KEY,
    type        VARCHAR(100) NOT NULL,
    payload     TEXT         NOT NULL,
    status      VARCHAR(20)  DEFAULT 'PENDING' NOT NULL,
    retry_count INT          DEFAULT 0 NOT NULL,
    created_at  BIGINT       NOT NULL
);
CREATE INDEX idx_outbox_events_status_created_at ON outbox_events (status, created_at);
