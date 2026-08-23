CREATE TABLE elevator_aggregate (
    id BIGINT PRIMARY KEY,
    current_floor INT NOT NULL,
    state VARCHAR(32) NOT NULL,
    obstructed BOOLEAN NOT NULL,
    door_position VARCHAR(16) NOT NULL,
    weight_kg INT NOT NULL,
    capacity_kg INT NOT NULL
);

-- The write side's own table -- separate from both the old CRUD
-- "elevators" table and the read side's "elevator_view" table. See
-- docs/architecture.md's "CQRS and domain events" section.
INSERT INTO elevator_aggregate (
    id, current_floor, state, obstructed, door_position, weight_kg, capacity_kg
) VALUES (
    1, 1, 'idle', FALSE, 'closed', 0, 800
);

CREATE TABLE landing_call (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    elevator_id BIGINT NOT NULL,
    floor INT NOT NULL,
    direction VARCHAR(16) NOT NULL,
    CONSTRAINT fk_landing_call_elevator FOREIGN KEY (elevator_id)
        REFERENCES elevator_aggregate (id)
);
