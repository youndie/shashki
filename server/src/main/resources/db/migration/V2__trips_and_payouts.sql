-- The two things a ride grows after the order saga is done with it (B-37).
--
-- **The trip is not a saga and has no table of saga shape.** Research §1.4c: `ARRIVING → ARRIVED →
-- IN_PROGRESS → COMPLETED` is driven by the driver's own transitions and by location, and there is
-- nothing in it to compensate. So it is a row with a status on it, and the row appears when the
-- driver first says the trip has moved — not when the order saga finishes, which would put a side
-- effect with no compensation inside a saga step.
CREATE TABLE trips (
    ride_id    VARCHAR(255) PRIMARY KEY,
    driver_id  VARCHAR(255) NOT NULL,
    status     VARCHAR(50)  NOT NULL,
    updated_at BIGINT       NOT NULL
);

-- What the driver is owed, written by the settlement saga's EXECUTION step and removed by its
-- compensation. One row per ride, so a settlement that ran twice would collide rather than pay
-- twice — the primary key is the idempotence.
CREATE TABLE payouts (
    ride_id      VARCHAR(255) PRIMARY KEY,
    driver_id    VARCHAR(255) NOT NULL,
    amount_cents BIGINT       NOT NULL,
    currency     VARCHAR(3)   NOT NULL,
    created_at   BIGINT       NOT NULL
);
