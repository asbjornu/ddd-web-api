# Call chain: `ObstructDoors` — REST + DDD (`main`)

A rider simulates blocking the doors while they are closing, in this
repository's current (`main`) architecture.

## Step 0 — the hypermedia control this checkbox came from

The `obstruct-doors` form only exists in the DOM for the few hundred
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
quoted below, which is the one place that decides so.

## UI

`app/plugins/panels.client.ts`

```ts
toggleInput.addEventListener('change', () => {
  if (toggleInput.checked) submitHiddenForm('obstruct-doors', {})
  else submitHiddenForm('clear-obstruction', {})
})
```

## HTTP

`POST /elevators/1` with `type=ObstructDoors` — but only if the
checkbox was enabled at all.

## Java

`ObstructDoorsAffordanceContributor.java` — decides whether the form
exists before any request reaches the network:

```java
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

## Client-side result

`panels.client.ts` never asks "is the door closing"; it only ever asks
"does a form with `data-rel="obstruct-doors"` currently exist" — the
same question restated as a DOM lookup instead of a state comparison:

```ts
const obstructed = fieldValue('obstructed') === 'true'
// obstruct-doors only exists while the doors are actually closing (there
// is nothing to obstruct otherwise), and clear-obstruction only once
// obstructed -- the checkbox can only ever do one or the other, never
// both at once, so its own enabled state mirrors whichever of the two
// the current state actually offers.
toggle.disabled = obstructed
  ? !formFor('clear-obstruction')
  : !formFor('obstruct-doors')
if (document.activeElement !== toggle) toggle.checked = obstructed
```

`formFor(rel)` is a plain `document.querySelector` against whatever the
most recent response rendered — it does not know, and does not need to
know, that "obstruct-doors offered only while closing" is the rule
behind the answer it gets back.

## What this client needed to know about the state machine

Nothing about *legality*. It reads one field (`obstructed`, for the
checkbox's own checked state — a display concern, not a permission) and
checks for the presence of two forms by name. The rule that actually
gates obstruction — `doorsClosing`, and only `doorsClosing` — is
written exactly once, in `ObstructDoorsAffordanceContributor`, and
never copied into this file at all.
