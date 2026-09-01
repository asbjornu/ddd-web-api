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

## Tests

`elevator-api/src/test/java/no/javazone/elevator/controller/CallControllerTest.java`
drives the one `ElevatorService.call` method end to end through
`MockMvc`, `POST`-ing the exact JSON shown above and asserting on a
second `GET /elevators/1/status` to see the dispatch's effect —
there is no separate domain-layer test for `call` in isolation:
`ElevatorServiceTest.java` (the God Object's own test class, one file
for every method the service has) covers dispatch logic as one case
among many, alongside `overloadClearsCarCallsAndPreventsDeparture` and
the maintenance/recall cases quoted elsewhere in this series.

```java
@Test
void callingFromIdleStartsTheElevatorMoving() throws Exception {
    mockMvc.perform(post("/elevators/1/calls")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"floor\": 3, \"direction\": \"UP\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.floor", is(3)))
            .andExpect(jsonPath("$.direction", is("UP")));

    mockMvc.perform(get("/elevators/1/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state", is("MOVING_UP")))
            .andExpect(jsonPath("$.direction", is("UP")))
            .andExpect(jsonPath("$.currentFloor", is(1)));
}
```

Nothing on the client is unit-tested at all: there is no test for
`store.callElevator` or for `CallPanel.vue`'s `isPending`/`call`
functions, Pinia-mocked or otherwise —
`elevator-ui/test/unit/elevatorStore.test.ts` only exercises the
store's getters and its technician key-switch actions. The one UI test
that runs, `elevator-ui/test/e2e/rider-page.spec.ts`, is a Playwright
smoke test that asserts the "Call elevator" heading renders and never
clicks a floor button:

```ts
test('renders heading and main panels', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Elevator', exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Call elevator' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Elevator status' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Public status page' })).toBeVisible()
})
```

So the car animation, the pending-call highlighting, and the
`TRAVEL_SECONDS_PER_FLOOR` duplication this file's "Client-side result"
section describes are, in this variant, entirely untested — the only
thing standing between them and silent drift is a human noticing.

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
