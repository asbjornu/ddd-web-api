# Call chain: `CallElevator` — REST + DDD (`main`)

A rider calls the car to floor 5, going up, in this repository's
current (`main`) architecture.

## Step 0 — how the page gets here at all

`elevator-ui` ships two static files and nothing that talks to
`elevator-api` on its own — `public/index.html` in full:

```html
<!doctype html>
<html lang="en">
  <head>
    <meta charset="utf-8" />
    <title>Elevator</title>
    <link rel="stylesheet" href="/main.css" />
    <script type="module"
      src="https://cdn.jsdelivr.net/gh/starfederation/datastar@v1.0.2/bundles/datastar.js"></script>
  </head>
  <body>
    <main>
      <h1>Elevator</h1>
      <div class="layout">
        <div id="entry-point" data-init="@get('/')"></div>
        <div id="shaft"></div>
      </div>
      <nav><a href="/status">Public status page</a></nav>
    </main>
    <script type="module" src="/shaft.js"></script>
    <script type="module" src="/panels.js"></script>
    <script type="module" src="/busy-indicator.js"></script>
  </body>
</html>
```

`data-init="@get('/')"` is the entire bootstrap: Datastar's runtime
(loaded from a CDN, never bundled into this project's own build) sees
the attribute on page load and issues that `GET` itself — nothing in
`shaft.ts`/`panels.ts` fetches anything. That request's own `Accept`
header is what lets one Caddy origin serve both this static file *and*
`elevator-api`'s hypermedia from the same path, captured live:

```
$ curl -s -D- http://127.0.0.1:8000/ -H "Accept: text/html"
                                                    # a real browser navigation

HTTP/1.1 200 OK
Content-Type: text/html; charset=utf-8
Server: Caddy
                                                    # the static index.html above, byte for byte

$ curl -s -D- http://127.0.0.1:8000/ \
    -H "Accept: text/event-stream, text/html, application/json" \
                                                    # Datastar's own @get('/') -- this is the literal
                                                    # Accept header its runtime sends, event-stream first
    -H "Datastar-Request: true"

HTTP/1.1 200 OK
Content-Type: text/html
Datastar-Mode: outer
Datastar-Selector: #entry-point
Link: </>; rel="self"
Link: </rels/help>; rel="help"
Link: </elevators>; rel="elevators"

<div id="entry-point">
<ul>
  <li><a rel="self" href="/">self</a></li>
  <li><a rel="help" href="/rels/help">help</a></li>
  <li><a rel="elevators" href="/elevators">elevators</a></li>
</ul>
<div id="elevators-collection" data-init="@get('/elevators')"></div>
</div>
```

The two requests are identical in every way except `Accept`; the
Caddyfile's own `@entryPoint` matcher (`path /` and `Accept` *not*
starting with `text/html`) is what routes the second one to
`elevator-api` instead of re-serving the static file — a genuine
top-level navigation always sends `text/html` first and gets the page;
Datastar's own re-fetch never does, and gets the hypermedia entry
point instead. `Datastar-Mode: outer` and `Datastar-Selector:
#entry-point` tell the already-loaded runtime exactly what to do with
the response body it just received: replace `#entry-point`'s own
outer element with this `<div>` — no JavaScript in this codebase reads
that response at all. The nested `data-init="@get('/elevators')"` on
the fragment `elevator-api` just rendered is what continues the
chain — elevators collection, then the one elevator, each hop's own
`data-init` naming the next, down to Step 1's `GET /elevators/1`
below.

## Step 1 — the hypermedia control this button came from

Nothing in `panels.ts` hard-codes `/elevators/1`,
`"call-elevator"`, or a `floor`/`direction` field: the button that ends
up calling `submitHiddenForm('call-elevator', ...)` only exists because
an earlier response rendered it. Captured live, this is that earlier
response — `GET /elevators/1`, the last hop of the discovery chain the
entry point (`GET /`) started, fetched by Datastar's own `data-init`
(the `Datastar-Request`/`Datastar-Selector`/`Datastar-Mode` headers are
Datastar's, not something this project invented — see `docs/plan.html`
§12):

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

This is what `panels.ts` actually reads when it builds
`CallPanel` and wires up its buttons: the `href` (`/elevators/1`, from
the form's own `action`), the field names (`floor`, `direction`), and
the floor range (`1`–`9`, from the `<option>` values it already
rendered) all come from here — see
`no.javazone.elevator.feature.callelevator.CallElevatorAffordanceContributor`,
the class on the Java side that decided this form exists at all, and
`no.javazone.elevator.shared.hypermedia.FloorOptions`, which supplied
the `1`–`9` range from `ElevatorProperties.floors()` rather than a
constant baked into either side.

## UI (vanilla TypeScript, no framework state)

`elevator-ui/src/panels.ts`

```ts
up.addEventListener('click', () =>
  submitHiddenForm('call-elevator', { floor: String(floor), direction: 'up' }))

function submitHiddenForm(rel: string, fields: Record<string, string>) {
  const form = formFor(rel) // document.querySelector against #elevator-content
  if (!form) return
  for (const [name, value] of Object.entries(fields)) {
    const field = form.elements.namedItem(name)
    if (field instanceof HTMLInputElement || field instanceof HTMLSelectElement) {
      field.value = value
    }
  }
  form.requestSubmit() // Datastar's data-on:submit picks this up
}
```

`form.requestSubmit()` is the entire hand-off to Datastar: this file
never calls `fetch` itself. The `<form>`'s own `data-on:submit="@post(
'/elevators/1', {contentType: 'form'})"` attribute — rendered by
`CallElevatorAffordanceContributor`, quoted in Step 1 — is what
Datastar's runtime reads to decide the request actually happens, and
where to.

## HTTP (one request, full stop)

```
POST /elevators/1
type=CallElevator&floor=5&direction=up
```

## Java

`shared/web/CommandsController.java` (the *one* `POST /elevators/{id}`
for every command)

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
`MovementScheduler`, independent of this request. The command's own
`POST` response is the new representation, rendered by content
negotiation, with the same `Datastar-Mode: outer`/`Datastar-Selector:
#elevator-content` headers Step 1's `GET` carried — Datastar morphs
`#elevator-content` in place, no page reload, no client-side parsing
of the body.

**No second request** for anyone else already watching, either: an
already-open `EventSource` (`GET /elevators/1/events`, opened by the
`data-init` at the very end of Step 1's fragment) gets pushed the same
state, but over SSE instead of a plain response — captured live,
mid-move, right after this same command:

```
$ curl -s http://127.0.0.1:8000/elevators/1/events

event: datastar-patch-elements
id: 0
data: selector #elevator-content
data: elements <div id="elevator-content">
data: elements <dl>
data: elements   <dt>currentFloor</dt><dd>1</dd>
data: elements   <dt>state</dt><dd>movingUp</dd>
data: elements   <dt>direction</dt><dd>up</dd>
...
data: elements </dl>
...

```

This is `ElevatorViewUpdates.send`
(`feature/streamevents/ElevatorViewUpdates.java`), using the Datastar
Java SDK's own `PatchElements` builder rather than Spring's
`SseEmitter`:

```java
subscriber.events().send(PatchElements.builder()
        .selector("#" + ElevatorRepresentations.CONTENT_WRAPPER_ID)
        .mode(ElementPatchMode.Outer)
        .data(fragment)
        .build());
```

Same selector, same "outer" mode, same fragment-rendering code as the
command's own HTTP response — one `htmlRenderer.contentFragment(...)`
call serves both a direct `POST` response and every open SSE
subscriber, so there is exactly one place that decides what "the
current state, as HTML" looks like, used identically by both delivery
mechanisms.

## Tests

`CallElevatorControllerTest.java` and `CallElevatorAffordanceContributorTest.java`
are unchanged in spirit from `json-hypermedia`'s own, but the
affordance test gained a case the other two branches do not have,
covering the `FloorOptions` fix this file's Step 1 section describes:

```java
@Test
void offersEveryFloorAsAnOption() {
```

There is, once again, no client-side test at all — not even the store
unit tests `json-hypermedia` had, because there is no store: `npm run
test:unit` was removed from this branch's CI entirely (see this
repository's commit history — the last thing it ever ran was a suite
covering a client-side state layer that no longer exists). The only
remaining client-side test is the same Playwright smoke test as the
other two variants, `elevator-ui/test/e2e/rider-page.spec.ts`, which
still never clicks a floor button. Concretely: the car animation,
`travelSecondsPerFloor` being read off the DOM rather than
hard-coded, and the `car.classList.toggle` calls this file's
"Client-side result" section quotes are exercised by nothing but a
human loading the page and watching the shaft.

## Client-side result

`shaft.ts`'s `update()` runs on every mutation of
`#elevator-content` — the command's own response, and later the SSE
push when the car arrives. This is where the car actually starts
moving on screen:

```ts
const state = fieldValue('state')
const destinationFloor = /* parsed from the "destinationFloor" dt/dd, or null */
const isMoving = state === 'movingUp' || state === 'movingDown' // knows the exact two "moving" state names

if (isMoving && destinationFloor !== null) {
  if (destinationFloor !== animTarget && !Number.isNaN(travelSeconds)) {
    startCarAnimation(currentFloor, destinationFloor, travelSeconds)
  }
} else if (animFrameId !== null || animTarget !== -1) { // knows "not moving" means snap to currentFloor, no interpolation
  cancelAnimationFrame(animFrameId ?? 0)
  animTarget = -1
  animatedFloor = currentFloor
}
positionCar()

car.classList.toggle('emergency', state === 'emergencyRecall') // knows the exact "emergencyRecall" state name
car.classList.toggle('doors-open', state === 'doorsOpen' || state === 'doorsClosing') // knows which two door states count as "car looks open"
```

`startCarAnimation` runs the identical `requestAnimationFrame`
interpolation loop `crud`'s `ElevatorShaft.vue` does — but the duration
comes from `travelSeconds`, read moments earlier via
`fieldValue('travelSecondsPerFloor')`, i.e. straight off the `<dl>` this
same response rendered, not a number written into this file. If the
server's `application.yml` changes `travel-seconds-per-floor` tomorrow,
this animation changes with it, automatically, on the next load.

## What this client needed to know about the state machine

Two hardcoded string comparisons remain, and are worth being honest
about: `state === 'movingUp' || state === 'movingDown'` (deciding
whether to animate at all) and `state === 'emergencyRecall'` (deciding
the car's colour). Both are pure *presentation* — how something already
permitted should look — never a legality decision; nothing here asks
"is this allowed," only "is the car currently between floors, and if
so, what colour is it." Contrast `crud`'s equivalent code, which needed
the same two state names *plus* an independent, hand-maintained timing
constant with no path back to the server's own value. This file's
duration always agrees with the server's, because it was never
memorised in the first place — it was read.

