-- Restores the destination the old status endpoint exposed as
-- "targetFloor" -- gone since slice 1 (nothing could move yet), back
-- now that select-floor and call-elevator can actually dispatch the
-- car. Null except while moving.
ALTER TABLE elevator_view ADD COLUMN destination_floor INT;
