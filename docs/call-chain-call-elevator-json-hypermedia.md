# Call chain: `CallElevator` — JSON hypermedia (`json-hypermedia`)

A rider calls the car to floor 5, going up, in the `json-hypermedia`
branch: the same vertical-slice Java backend as `main`, but consumed by
a Vue + Pinia single-page app that fetches JSON representations itself
— `main`'s server-rendered HTML/Datastar layer does not exist yet on
this branch.

## Step 0 — the hypermedia control this button came from

Nothing in `CallPanel.vue`/`elevator.ts` hard-codes `/elevators/1` or
`"call-elevator"`: the button that ends up calling
`store.callElevator(...)` only exists because an earlier response
carried an operation with that `rel`. Captured live — `GET
/elevators/1`, the request `fetchInitialStatus()` makes once on mount:

```
$ curl -s http://127.0.0.1:8000/elevators/1 \
    -H "Accept: application/vnd.elevator.state+json"

{
  "currentFloor" : 1,
  "state" : "idle",
  "direction" : "none",
  "doorPosition" : "closed",
  "obstructed" : false,
  "weightKg" : 0,
  "capacityKg" : 800,
  "destinationFloor" : null,
  "travelSecondsPerFloor" : 2,
  "links" : [
    { "rel" : "self", "href" : "/elevators/1" },
    { "rel" : "updates", "href" : "/elevators/1/events", "type" : "text/event-stream" }
  ],
  "operations" : [ {
    "rel" : "call-elevator",
    "title" : "Call elevator",
    "method" : "POST",
    "href" : "/elevators/1",
    "fields" : [
      { "name" : "type", "type" : "hidden", "value" : "CallElevator", "required" : true },
      { "name" : "floor", "type" : "text", "value" : null, "required" : true },
      { "name" : "direction", "type" : "select", "value" : null, "required" : true,
        "options" : [ "up", "down" ] }
    ]
  }
  /* ...insert-key, open-doors, select-floor: the other operations
     idle offers, elided here... */
  ]
}
```

Notice `floor`'s own field: `"type": "text"`, no `options` — unlike
`direction`'s `select`, this branch's
`CallElevatorAffordanceContributor` does not yet enumerate the valid
floor range the way `main`'s does with `FloorOptions`. That gap is why
`CallPanel.vue`, quoted below, still has to hard-code
`BUILDING_FLOORS` itself: the representation never told it.

## UI (Vue + Pinia, JSON hypermedia, no HTML)

`app/stores/elevator.ts`

```ts
callElevatorOperation: (state) =>
  state.status?.operations?.find((op) => op.rel === 'call-elevator') ?? null
```

```ts
async callElevator(floor: number, direction: 'up' | 'down') {
  const operation = this.callElevatorOperation
  if (!operation) {
    this.error = 'Calling the elevator is not available right now.'
    return
  }
  this.loading = true
  try {
    const data = await $fetch<ElevatorView>(operation.href, {
      method: operation.method as 'POST',
      headers: { Accept: 'application/vnd.elevator.state+json' },
      body: commandBody(operation, { floor, direction })
    })
    this.status = data
    this.error = null
  } catch {
    this.error = 'Unable to call the elevator.'
  } finally {
    this.loading = false
  }
}
```

`app/components/CallPanel.vue`

```ts
const floors = Array.from({ length: BUILDING_FLOORS }, (_, i) => BUILDING_FLOORS - i) // knows the floor range itself; the operation's own field never says

function canCallUp(floor: number) {
  return floor < BUILDING_FLOORS // knows the floor range itself, again
}
```

## HTTP (one request, full stop)

```
POST /elevators/1
Accept: application/vnd.elevator.state+json
{"type":"CallElevator","floor":5,"direction":"up"}
```

Straight to `elevator-api`, same origin via Caddy — no BFF hop for a
rider command; the BFF on this branch exists only for the technician's
key-switch (see the `TriggerEmergencyRecall` trace in this series).

## Java

`shared/web/CommandsController.java` (the *one* `POST
/elevators/{id}` for every command — unchanged from `main`)

```java
@PostMapping("/elevators/{segment}")
public ResponseEntity<String> dispatch(
        @PathVariable String segment, HttpServletRequest request, ...) {
    JsonNode body = RequestBodies.read(request, objectMapper);
    CommandEndpoint endpoint = endpointsByType.get(commandType(body));
    return endpoint.handle(id.get(), segment, body, accept, principalResolver.resolve());
}
```

`CallElevatorHandler.java` (byte-for-byte identical to `main`'s — the
domain layer does not know or care which renderer eventually shows its
result)

```java
public List<DomainEvent> handle(CallElevatorCommand command) {
    Elevator elevator = store.find(command.elevatorId()).orElseThrow(...);
    List<DomainEvent> events = elevator.call(command.floor(), command.direction());
    store.save(elevator);
    effects.apply(elevator, events);
    return events;
}
```

The response is the same `application/vnd.elevator.state+json`
representation `fetchInitialStatus()` used — content-negotiated by the
same `RendererRegistry`/`ElevatorStateJsonRenderer` pair `main` also
has, just without an HTML renderer capable of the full resource (this
branch's `HtmlRenderer` only ever renders the entry point). An
already-open `EventSource` also gets pushed the same properties over
SSE — but never the `operations` array (see the store's own
`connectToEvents`, quoted in full below).

## Client-side result

`ElevatorShaft.vue`'s `watch()` — a close cousin of `main`'s
`shaft.client.ts`, expressed as Vue reactivity instead of a
`MutationObserver`:

```ts
watch(
  () => store.status,
  (status) => {
    if (!status) return
    const isMoving = status.state === 'movingUp' || status.state === 'movingDown' // knows the exact two "moving" state names

    if (isMoving && status.destinationFloor != null) {
      const from = status.currentFloor
      const to = status.destinationFloor
      if (to !== animTarget) {
        startCarAnimation(from, to, status.travelSecondsPerFloor)
      }
    } else if (!isMoving) { // knows "not moving" means snap to currentFloor, no interpolation
      if (animFrameId) {
        cancelAnimationFrame(animFrameId)
        animFrameId = null
      }
      animTarget = -1
      animatedFloor.value = status.currentFloor
    }
  }
)
```

and further down, the same component's colour/door computeds:

```ts
const isCarOpen = computed(
  () => doorStateClass.value === 'open' || doorStateClass.value === 'closing' // knows which two door states count as "car looks open"
)
const isOutOfService = computed(() => store.status?.state === 'outOfService') // knows the exact "outOfService" state name
const isEmergency = computed(() => store.status?.state === 'emergencyRecall') // knows the exact "emergencyRecall" state name
```

Exactly like `main`, `travelSecondsPerFloor` is read off the
representation (`status.travelSecondsPerFloor`), never a client-side
guessed constant — this branch already fixed that half of `crud`'s
problem, well before the HTML/Datastar layer existed to fix the other
half (server-rendered forms, no client-side operation lookup at all).

## What this client needed to know about the state machine

The same two state names as `main`'s equivalent trace, for the same
reason — pure presentation, never a legality decision — plus one more
`main` does not need: `doorPosition`'s two "looks open" values, because
this store's `ElevatorView` interface carries `doorPosition` as a bare
string the component must interpret itself, the same shape problem
`floor`'s missing `options` caused above. Nothing here duplicates a
*permission* rule, though: whether `call-elevator` may be invoked at
all is decided once, server-side, and this client's only question is
whether the operation showed up.
