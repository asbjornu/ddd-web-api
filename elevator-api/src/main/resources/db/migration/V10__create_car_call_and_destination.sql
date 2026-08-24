CREATE TABLE car_call (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    elevator_id BIGINT NOT NULL,
    floor INT NOT NULL,
    CONSTRAINT fk_car_call_elevator FOREIGN KEY (elevator_id)
        REFERENCES elevator_aggregate (id)
);

-- Only movingUp/movingDown carry a destination; every other state
-- leaves this null. See shared.domain.ElevatorStateNames.
ALTER TABLE elevator_aggregate ADD COLUMN destination_floor INT;
