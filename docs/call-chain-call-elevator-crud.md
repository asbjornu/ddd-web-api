# Call chain: `CallElevator` — `crud`

A rider calls the car to floor 5, going up, in the `crud` variant of
this repository.

## UI (Vue + Pinia)

`app/components/CallPanel.vue`

```vue
<button type="button" :class="{ active: isPending(floor) }"
        :disabled="store.loading" @click="call(floor, 'UP')">▲</button>
```

```ts
function call(floor: number, dir: 'UP' | 'DOWN') {
  store.callElevator(floor, dir)
}
function isPending(floor: number) {
  return store.floorsWithPendingCalls.has(floor) // knows "pending" means the call row's servedAt is still null
}
```

`app/stores/elevator.ts`

```ts
async callElevator(floor: number, direction: 'UP' | 'DOWN') {
  this.loading = true
  try {
    await $fetch(`/api/elevators/${ELEVATOR_ID}/calls`, {
      method: 'POST',
      body: { floor, direction }
    })
    await Promise.all([this.fetchStatus(), this.fetchCalls()])
  } finally {
    this.loading = false
  }
}
```

## HTTP

**#1** (browser to `elevator-ui:3000`, same origin):

```
POST /api/elevators/1/calls
{"floor":5,"direction":"UP"}
```

**BFF** — `server/api/elevators/[id]/calls.post.ts`

```ts
export default defineEventHandler(async (event) => {
  const body = await readBody(event)
  return await $fetch(
    `${config.serviceApiUrl}/elevators/${getRouterParam(event, 'id')}/calls`,
    { method: 'POST', body }
  )
})
```

**#2** (`elevator-ui` to `elevator-api:8080`, Docker DNS): identical
body, different host.

## Java

`controller/CallController.java`

```java
@PostMapping("/elevators/{id}/calls")
@ResponseStatus(HttpStatus.CREATED)
public Call call(@PathVariable Long id, @RequestBody Call request) {
    return elevatorService.call(id, request);
}
```

`service/ElevatorService.java`

```java
public Call call(Long elevatorId, Call request) {
    Elevator elevator = findElevator(elevatorId);
    recomputeState(elevator); // derive state from elapsed time, first
    if (request.getFloor() < 1 || request.getFloor() > properties.floors())
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid floor");
    if (elevator.getState() == ElevatorState.OUT_OF_SERVICE
            || elevator.getState() == ElevatorState.EMERGENCY_RECALL)
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Elevator is not in service");

    Call call = new Call(); // JPA entity IS the wire DTO
    call.setElevatorId(elevatorId);
    call.setFloor(request.getFloor());
    call.setDirection(request.getDirection());
    if (elevator.getState() == ElevatorState.IDLE && !isOverloaded(elevator))
        dispatchToFloor(elevator, call.getFloor()); // mutates & saves the SAME entity
    return callRepository.save(call);
}
```

`Call`/`Elevator` persist to **H2**, tables `calls`/`elevators`. The
response is the persisted `Call` row, as JSON. `fetchStatus()`/
`fetchCalls()` then fire two more full round trips (repeats of hops
1–2, `GET` this time) because the `POST` response didn't say what the
elevator is doing now.

## Client-side result

Once `fetchStatus()` resolves, Pinia's `store.status` changes, and
`ElevatorShaft.vue`'s own `watch()` fires — this is where the car
actually starts moving on screen, and it is the densest piece of
state-machine-aware code in the whole client:

```ts
const TRAVEL_SECONDS_PER_FLOOR = 2 // knows the server's own timing, guessed and hand-copied, never read

watch(
  () => store.status,
  (status) => {
    if (!status) return
    const isMoving = status.state === 'MOVING_UP' || status.state === 'MOVING_DOWN' // knows the exact two "moving" state names

    if (isMoving && status.targetFloor != null) {
      const from = status.currentFloor
      const to = status.targetFloor
      if (to !== animTarget) startCarAnimation(from, to)
    } else if (!isMoving) { // knows "not moving" means snap to currentFloor, no interpolation
      if (animFrameId) cancelAnimationFrame(animFrameId)
      animTarget = -1
      animatedFloor.value = status.currentFloor
    }
  }
)
```

`startCarAnimation` then runs its own `requestAnimationFrame` loop,
interpolating `animatedFloor` over `distance * TRAVEL_SECONDS_PER_FLOOR
* 1000` milliseconds — a duration the client computes itself, from a
constant that exists nowhere on the wire and has no relationship to
`application.yml`'s `travel-seconds-per-floor` other than "someone
typed the same number twice." If an operator ever tunes the server's
timing without remembering this line, the car's animation and the
server's actual arrival silently drift apart; nothing would fail, the
UI would just be lying about how long the trip takes.

`CallPanel.vue`'s pending-call highlighting is the same shape of
problem at smaller scale: `isPending(floor)` calls
`store.floorsWithPendingCalls`, a getter that filters `state.calls` for
`servedAt === null` — the client re-derives "is this call still
outstanding" from a raw list of rows, rather than being told.

## What this client needed to know about the state machine

To animate one call correctly, this code has to know: the exact string
names of the two "moving" states (`'MOVING_UP'`/`'MOVING_DOWN'`, typed
twice, once per `watch()` callback that needs them); that
`travelSecondsPerFloor` is `2`, as a constant of its own; that a call
becomes "not pending" when its `servedAt` column is non-null; and,
separately (see the door-timing logic this same component also owns),
how long the door-open timeout is and that arrivals need a delayed
door-state reveal to look right. None of this is discoverable from the
response to *this* request — it is knowledge about the server's
internals that has to already live in the client before the request is
even sent, and has no mechanism to notice if the server's own copy of
it ever changes.
