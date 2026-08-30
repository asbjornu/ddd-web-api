# Call chain: `ObstructDoors`

A rider simulates blocking the doors while they are closing. Traced end
to end, in both the `crud` and `main` (REST + DDD) variants of this
repository.

## `crud`

**UI (Vue + Pinia)** — `app/components/StatusDisplay.vue`

```vue
<input type="checkbox" :checked="store.status?.obstructed ?? false"
       @change="store.toggleObstruction()" />
```

`app/stores/elevator.ts`

```ts
async toggleObstruction() {
  const obstructed = !this.status?.obstructed
  await $fetch(`/api/elevators/${ELEVATOR_ID}/obstruction`, {
    method: 'POST',
    body: { obstructed }
  })
  await this.fetchStatus()
}
```

**HTTP**: `POST /api/elevators/1/obstruction` `{"obstructed":true}` to
the BFF (no auth header needed — a rider action), then
`POST http://elevator-api:8080/elevators/1/obstruction`, same body.

**Java** — `controller/DoorController.java`

```java
@PostMapping("/elevators/{id}/obstruction")
public Elevator setObstruction(@PathVariable Long id, @RequestBody ObstructionRequest body) {
    return elevatorService.setObstruction(id, body.obstructed());
}
```

`service/ElevatorService.java`

```java
public Elevator setObstruction(Long id, boolean obstructed) {
    Elevator elevator = findElevator(id);
    elevator.setObstructed(obstructed); // no check at all: legal in *any* state
    if (obstructed) recomputeState(elevator);
    return elevatorRepository.save(elevator);
}
```

Notice: there is no state check here — the checkbox is always
clickable, in every state; whatever happens, happens.

## `main`

**Step 0 — the hypermedia control this checkbox came from.** The
`obstruct-doors` form only exists in the DOM for the few hundred
milliseconds the car is actually in `doorsClosing` — captured live,
mid-window, by opening the doors, closing them, and polling
`GET /elevators/1` fast enough to catch it (no `Datastar-Request`
header this time, so this is the standalone document a machine client
reading the API directly would see, not a Datastar fragment):

```
$ curl -s http://127.0.0.1:8000/elevators/1 -H "Accept: text/html"

<!doctype html>
<html lang="en"><head><meta charset="utf-8"><title>Elevator</title></head><body>
<h1>Elevator</h1>
<div id="elevator">
<div id="elevator-content">
<dl>
  <dt>currentFloor</dt><dd>1</dd>
  <dt>state</dt><dd>doorsClosing</dd>
  <dt>direction</dt><dd>none</dd>
  <dt>doorPosition</dt><dd>closing</dd>
  ... <!-- obstructed, weightKg, capacityKg, destinationFloor,
           travelSecondsPerFloor, doorOpenTimeoutSeconds -->
</dl>
<!-- ...self/updates links, call-elevator, insert-key... -->
<form action="/elevators/1" method="post" data-rel="obstruct-doors"
      data-on:submit="@post('/elevators/1', {contentType: 'form'})">
  <fieldset>
  <legend>Simulate obstruction</legend>
  <label>type
    <input type="hidden" name="type" value="ObstructDoors" required>
  </label>
  <button type="submit">Simulate obstruction</button>
  </fieldset>
</form>
<!-- ...open-doors, select-floor... -->
</div>
<div id="elevator-events" data-init="@get('/elevators/1/events')"></div>
</div>
</body></html>
```

The same `GET`, issued a second earlier (`state: idle`) or a second
later (`state: idle` again, doors now closed), simply would not contain
this `<form>` at all — see
`no.javazone.elevator.feature.obstructdoors.ObstructDoorsAffordanceContributor`,
quoted below, which is the one place that decides so. `panels.client.ts`
never asks "is the door closing"; it only ever asks "does a form with
`data-rel="obstruct-doors"` currently exist" (`formFor('obstruct-doors')`
in the code that follows), which is the same question restated as a DOM
lookup instead of a state comparison.

**UI** — `app/plugins/panels.client.ts`

```ts
toggleInput.addEventListener('change', () => {
  if (toggleInput.checked) submitHiddenForm('obstruct-doors', {})
  else submitHiddenForm('clear-obstruction', {})
})
// disabled state mirrors availability, read straight off the DOM:
toggle.disabled = obstructed
  ? !formFor('clear-obstruction')
  : !formFor('obstruct-doors')
```

**HTTP**: `POST /elevators/1` with `type=ObstructDoors` — but only if
the checkbox was enabled at all.

**Java** — the affordance decides whether that's even legal, before any
request reaches the network:

```java
// ObstructDoorsAffordanceContributor.java
if (!"doorsClosing".equals(context.state().orElse(""))) return List.of(); // no form rendered otherwise
```

`ObstructDoorsHandler.java`

```java
public List<DomainEvent> handle(ObstructDoorsCommand command) {
    Elevator elevator = store.find(command.elevatorId()).orElseThrow(...);
    List<DomainEvent> events = elevator.obstructDoors(); // refuses if not doorsClosing, redundantly-but-safely
    store.save(elevator);
    effects.apply(elevator, events);
    return events;
}
```

## The difference, reasoned

This is the starkest pair of the three operations in this series.
`crud`'s checkbox is a raw boolean toggle with no legality concept at
all — you can "obstruct" an idle, stationary car with closed doors,
because nothing stops you; the service just sets a field. `main` makes
"there is nothing to obstruct right now" a first-class fact the client
is *told*: the checkbox itself is disabled or absent because the server
never rendered the form, computed from the exact same state the
aggregate's own refusal would enforce if you somehow tried anyway
(`elevator.obstructDoors()` re-checks, belt-and-suspenders). One rule,
checked twice for safety, in one place — not "whatever the checkbox
happens to let you click."
