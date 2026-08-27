-- Drops the original CRUD schema, now that no.javazone.elevator.model/
-- .repository/.service/.controller (the code that read and wrote these
-- three tables) is deleted. Nothing else ever referenced them: the new
-- architecture's write side lives in elevator_aggregate/landing_call/
-- car_call (V9/V10), its read side in elevator_view (V8/V11). Dropped,
-- not left behind, per the same "delete outright" the old
-- CallController/CarCallController javadoc already promised once their
-- last reader migrated.
DROP TABLE calls;
DROP TABLE car_calls;
DROP TABLE elevators;
