-- Who a driver is, which this product had never written down (B-63).
--
-- **Four documents said the same absence in their own words** — the class and the rating on a
-- position frame are "self-reported", the earnings day rolls at UTC because there is nowhere to keep
-- a timezone, the assigned-ride card has a registration slot that is deliberately blank, and R8 asks
-- a rider to rate an e-mail address. One row answers all four.
--
-- **The class moves here from the wire.** A driver telling the server which class they drive is a
-- driver choosing which offers they are eligible for; the record ends that without a single new
-- check, which is the security half of B-52's remainder.
CREATE TABLE drivers (
    id         VARCHAR(255) PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    car        VARCHAR(255) NOT NULL,
    plate      VARCHAR(32)  NOT NULL,
    ride_class VARCHAR(20)  NOT NULL
);

-- **Seeded, because this product has no registration and says so.** A driver signs in and drives;
-- nothing here creates a driver, so the demo's two are rows in a migration rather than a fiction the
-- server invents on first sight. The item that adds a "become a driver" flow replaces this seed —
-- and until it exists, a driver the server has never heard of is not a candidate, which is a rule
-- with a visible failure rather than a silent one.
INSERT INTO drivers (id, name, car, plate, ride_class) VALUES
    ('driver-1', 'Ivan Sokolov', 'Skoda Octavia · white', 'A 123 BC', 'ECONOMY'),
    ('rider@example.com', 'Ivan Sokolov', 'Skoda Octavia · white', 'A 123 BC', 'ECONOMY');
