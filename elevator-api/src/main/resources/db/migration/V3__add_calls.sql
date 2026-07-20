ALTER TABLE elevators ADD COLUMN target_floor INT;

CREATE TABLE calls (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    elevator_id BIGINT NOT NULL,
    floor INT NOT NULL,
    direction VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    served_at TIMESTAMP,
    CONSTRAINT fk_calls_elevator FOREIGN KEY (elevator_id)
        REFERENCES elevators (id)
);
