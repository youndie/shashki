-- What a ride grows after it is over (B-44).
--
-- **The rating is a row and the tip is a second payout.** A rating outlives every process — it is
-- what the candidate sort reads, and a number that vanished on a restart would be a sort key that
-- lies — so unlike the geo-index it is written down.
CREATE TABLE ratings (
    ride_id    VARCHAR(255) PRIMARY KEY,
    driver_id  VARCHAR(255) NOT NULL,
    stars      INT          NOT NULL,
    created_at BIGINT       NOT NULL
);

-- One rider rates one ride once; the driver's number is the average of theirs.
CREATE INDEX ratings_driver ON ratings (driver_id);

-- **A tip is a second payout for the same ride, so the key grows a column.** It was `ride_id`
-- alone, which is what made "a settlement that ran twice collides rather than pays twice" true —
-- and a tip is not that settlement running twice, it is a different one. `FARE` for everything
-- written before this migration, which is what those rows were.
ALTER TABLE payouts ADD COLUMN kind VARCHAR(20) NOT NULL DEFAULT 'FARE';
-- **And the default goes again**, because the Exposed table declares none and `SchemaTest` compares
-- the two: a default that lives only in the database is a rule the application cannot see. It was
-- here to fill the rows that already existed, which it has now done.
ALTER TABLE payouts ALTER COLUMN kind DROP DEFAULT;
ALTER TABLE payouts DROP CONSTRAINT payouts_pkey;
ALTER TABLE payouts ADD PRIMARY KEY (ride_id, kind);
