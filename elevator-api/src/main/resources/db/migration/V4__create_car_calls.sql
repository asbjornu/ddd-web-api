CREATE TABLE car_calls (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    elevator_id BIGINT NOT NULL,
    floor INT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    served_at TIMESTAMP,
    CONSTRAINT fk_car_calls_elevator FOREIGN KEY (elevator_id)
        REFERENCES elevators (id)
);
