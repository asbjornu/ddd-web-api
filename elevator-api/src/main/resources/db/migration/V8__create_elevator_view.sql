CREATE TABLE elevator_view (
    id BIGINT PRIMARY KEY,
    current_floor INT NOT NULL,
    state VARCHAR(32) NOT NULL,
    direction VARCHAR(16) NOT NULL,
    door_position VARCHAR(16) NOT NULL,
    obstructed BOOLEAN NOT NULL,
    weight_kg INT NOT NULL,
    capacity_kg INT NOT NULL
);

-- The read side's own table, separate from the write-side "elevators"
-- table above -- see docs/architecture.md's "CQRS and domain events"
-- section. Seeded with the same initial values as V2, since no command
-- has moved onto the new aggregate yet to produce an event of its own;
-- from slice 2 onward, a projection updates this row instead of it
-- being seeded once and left alone.
INSERT INTO elevator_view (
    id, current_floor, state, direction, door_position,
    obstructed, weight_kg, capacity_kg
) VALUES (
    1, 1, 'idle', 'none', 'closed', FALSE, 0, 800
);
