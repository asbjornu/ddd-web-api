CREATE TABLE elevators (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    current_floor INT NOT NULL,
    state VARCHAR(32) NOT NULL,
    direction VARCHAR(16),
    door_state VARCHAR(16) NOT NULL,
    weight_capacity_kg INT NOT NULL,
    state_since TIMESTAMP NOT NULL
);
