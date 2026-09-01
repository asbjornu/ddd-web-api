// Business rules mirrored from elevator-api's own ElevatorService and
// application.yml, so this BFF can refuse an obviously invalid or
// obviously illegal request before ever proxying it -- an "intelligent"
// BFF, not just a reverse proxy.
//
// This is the "Business logic duplicated across layers" smell in
// docs/architecture.md, made concrete: every constant and every rule
// below is a second, hand-copied version of something ElevatorService
// or application.yml already expresses once. Nothing here reads
// elevator-api's own configuration or source at startup, build time, or
// ever -- if `elevator.floors` in application.yml changes, or if
// ElevatorService's own conflict checks are edited, this file goes on
// enforcing the old rule until a human remembers it exists and updates
// it separately. The floor range and weight capacity below are simply
// today's defaults, typed a second time.

export const BUILDING_FLOORS = 9 // elevator.floors in elevator-api's application.yml
export const WEIGHT_CAPACITY_KG = 800 // elevator.weight-capacity-kg in elevator-api's application.yml

// The subset of the service API's own Elevator entity (see "Model
// reuse" in docs/architecture.md) this file's own checks need to read.
export interface ElevatorStatusForValidation {
  state: string
  currentFloor: number
  obstructed: boolean
  currentWeightKg: number
  weightCapacityKg: number
}

export function isValidFloor(floor: unknown): floor is number {
  return (
    typeof floor === 'number' && Number.isInteger(floor) && floor >= 1 && floor <= BUILDING_FLOORS
  )
}

// ElevatorService.call/carCall never validate this themselves -- an
// unrecognised direction fails earlier, as a generic 400 from Jackson's
// own enum deserialization, before the service method is ever entered.
// This duplicates that outcome with this BFF's own message instead of
// Java's, which means the two can disagree about wording for the exact
// same rejected input.
export function isValidDirection(direction: unknown): direction is 'UP' | 'DOWN' {
  return direction === 'UP' || direction === 'DOWN'
}

export function isOverloaded(
  status: Pick<ElevatorStatusForValidation, 'currentWeightKg' | 'weightCapacityKg'>
): boolean {
  return status.currentWeightKg > status.weightCapacityKg
}

export function isOutOfServiceOrRecall(
  status: Pick<ElevatorStatusForValidation, 'state'>
): boolean {
  return status.state === 'OUT_OF_SERVICE' || status.state === 'EMERGENCY_RECALL'
}

// Fetches the same public, unauthenticated read model
// status.get.ts already proxies, purely so a write route below can
// pre-check a state-dependent rule before deciding whether to bother
// the service API at all. This is an extra round trip elevator-api's
// own controllers never need (their check runs in the same request,
// against the same transaction), and it opens a real race: the
// elevator's state can change between this read and the write this
// file goes on to make, same as any check-then-act split across two
// requests would.
export async function fetchStatusForValidation(
  serviceApiUrl: string,
  id: string | undefined
): Promise<ElevatorStatusForValidation> {
  return await $fetch<ElevatorStatusForValidation>(`${serviceApiUrl}/elevators/${id}/status`)
}
