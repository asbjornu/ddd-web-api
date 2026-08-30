# Call chain: `CallElevator`

A rider calls the car to floor 5, going up. Traced end to end, in both
the `crud` and `main` (REST + DDD) variants of this repository — see
`docs/architecture.md` for what `main` actually is, and `AGENTS.md` for
why `crud`'s code still exists in git history rather than in this tree.

## `crud`

**UI (Vue + Pinia)** — `app/components/CallPanel.vue`

```vue
<button type="button" :class="{ active: isPending(floor) }"
        :disabled="store.loading" @click="call(floor, 'UP')">▲</button>
```

```ts
function call(floor: number, dir: 'UP' | 'DOWN') {
  store.callElevator(floor, dir)
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

**HTTP #1** (browser to `elevator-ui:3000`, same origin):

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

**HTTP #2** (`elevator-ui` to `elevator-api:8080`, Docker DNS): identical
body, different host.

**Java** — `controller/CallController.java`

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
response is the persisted `Call` row, as JSON. Then `fetchStatus()`/
`fetchCalls()` fire two more full round trips (repeats of hops 1–2,
`GET` this time) because the `POST` response didn't say what the
elevator is doing now.

## `main`

**Step 0 — the hypermedia control this button came from.** Nothing in
`panels.client.ts` hard-codes `/elevators/1`, `"call-elevator"`, or a
`floor`/`direction` field: the button that ends up calling
`submitHiddenForm('call-elevator', ...)` only exists because an earlier
response rendered it. Captured live, this is that earlier response —
`GET /elevators/1`, the last hop of the discovery chain the entry point
(`GET /`) started, fetched by Datastar's own `data-init` (the
`Datastar-Request`/`Datastar-Selector`/`Datastar-Mode` headers are
Datastar's, not something this project invented — see
`docs/plan.html` §12):

```
$ curl -s -D- http://127.0.0.1:8000/elevators/1 \
    -H "Accept: text/html" -H "Datastar-Request: true"

HTTP/1.1 200 OK
Content-Type: text/html
Datastar-Mode: outer
Datastar-Selector: #elevator
Link: </elevators/1>; rel="self"
Link: </elevators/1/events>; rel="updates"; type="text/event-stream"

<div id="elevator">
<div id="elevator-content">
<dl>
  <dt>currentFloor</dt><dd>1</dd>
  <dt>state</dt><dd>idle</dd>
  ... <!-- direction, doorPosition, obstructed, weightKg, capacityKg,
           destinationFloor, travelSecondsPerFloor, doorOpenTimeoutSeconds -->
</dl>
<ul>
  <li><a rel="self" href="/elevators/1">self</a></li>
  <li><a rel="updates" href="/elevators/1/events">updates</a></li>
</ul>
<form action="/elevators/1" method="post" data-rel="call-elevator"
      data-on:submit="@post('/elevators/1', {contentType: 'form'})">
  <fieldset>
  <legend>Call elevator</legend>
  <label>type
    <input type="hidden" name="type" value="CallElevator" required>
  </label>
  <label>floor
    <select name="floor">
      <option value="1">1</option>
      <!-- ...2 through 8... -->
      <option value="9">9</option>
    </select>
  </label>
  <label>direction
    <select name="direction">
      <option value="up">up</option>
      <option value="down">down</option>
    </select>
  </label>
  <button type="submit">Call elevator</button>
  </fieldset>
</form>
<!-- ...insert-key, open-doors, select-floor: the other affordances
     idle offers, each its own <form>, elided here... -->
</div>
<div id="elevator-events" data-init="@get('/elevators/1/events')"></div>
</div>
```

This is what `panels.client.ts` actually reads when it builds
`CallPanel` and wires up its buttons: the `href` (`/elevators/1`, from
the form's own `action`), the field names (`floor`, `direction`), and
the floor range (`1`–`9`, from the `<option>` values it already
rendered) all come from here — see
`no.javazone.elevator.feature.callelevator.CallElevatorAffordanceContributor`,
the class on the Java side that decided this form exists at all, and
`no.javazone.elevator.shared.hypermedia.FloorOptions`, which supplied
the `1`–`9` range from `ElevatorProperties.floors()` rather than a
constant baked into either side.

**UI (vanilla TypeScript, no framework state)** —
`app/plugins/panels.client.ts`

```ts
up.addEventListener('click', () =>
  submitHiddenForm('call-elevator', { floor: String(floor), direction: 'up' }))

function submitHiddenForm(rel: string, fields: Record<string, string>) {
  const form = document.querySelector<HTMLFormElement>(
    `#elevator-content form[data-rel="${rel}"]`
  )
  for (const [name, value] of Object.entries(fields)) {
    const field = form.elements.namedItem(name)
    if (field instanceof HTMLSelectElement) field.value = value
  }
  form.requestSubmit() // Datastar's data-on:submit picks this up
}
```

The form itself was rendered by the server (`data-rel="call-elevator"`,
`data-on:submit="@post('/elevators/1', {contentType: 'form'})"`).

**HTTP #1 (only)** (browser to Caddy `:8000`, same origin):

```
POST /elevators/1
type=CallElevator&floor=5&direction=up
```

**Java** — `shared/web/CommandsController.java` (the *one*
`POST /elevators/{id}` for every command)

```java
@PostMapping("/elevators/{segment}")
public ResponseEntity<String> dispatch(
        @PathVariable String segment, HttpServletRequest request, ...) {
    JsonNode body = RequestBodies.read(request, objectMapper);
    CommandEndpoint endpoint = endpointsByType.get(commandType(body)); // "CallElevator" -> bean
    return endpoint.handle(id.get(), segment, body, accept, principalResolver.resolve());
}
```

`feature/callelevator/CallElevatorController.java`

```java
handler.handle(new CallElevatorCommand(id, floor.get(), direction.get()));
```

`CallElevatorHandler.java`

```java
public List<DomainEvent> handle(CallElevatorCommand command) {
    Elevator elevator = store.find(command.elevatorId()).orElseThrow(...); // pure domain object, no JPA
    List<DomainEvent> events = elevator.call(command.floor(), command.direction()); // can throw CommandRefused
    store.save(elevator);
    effects.apply(elevator, events); // syncs elevator_view, publishes events (scheduler listens)
    return events;
}
```

`store.save` writes `elevator_aggregate`/`landing_call` (H2);
`effects.apply` writes `elevator_view` (H2, a separate table) and — if
the car just dispatched — schedules a future `FloorReached` via
`MovementScheduler`, independent of this request. The response is the
new representation, rendered by content negotiation. **No second
request**: an already-open `EventSource`
(`GET /elevators/1/events`) gets pushed the same state via SSE.

## The difference, reasoned

`crud` needs 3 requests (`POST` plus 2×`GET`) because the entity save
doesn't say what happens next and nothing pushes; `main` needs 1,
because the aggregate's own commit computes the arrival instant up
front and a scheduler — not a poll — narrates it later. `crud`'s
validation (`floor` range, `OUT_OF_SERVICE` check) is `if`/`throw`
*after* loading state; `main`'s is inside `elevator.call()`, and a
refused command never reaches persistence at all.
