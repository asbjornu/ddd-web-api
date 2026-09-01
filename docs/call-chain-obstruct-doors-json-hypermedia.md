# Call chain: `ObstructDoors` — JSON hypermedia (`json-hypermedia`)

A rider simulates blocking the doors while they are closing, in the
`json-hypermedia` branch.

## Step 0 — the hypermedia control this checkbox came from

The `obstruct-doors` operation only exists in the `operations` array
for the few hundred milliseconds the car is actually in
`doorsClosing` — captured live, mid-window, by opening the doors,
closing them, and polling `GET /elevators/1` fast enough to catch it:

```
$ curl -s http://127.0.0.1:8000/elevators/1 \
    -H "Accept: application/vnd.elevator.state+json"

{
  "currentFloor" : 3,
  "state" : "doorsClosing",
  "direction" : "none",
  "doorPosition" : "closing",
  "obstructed" : false,
  ...
  "operations" : [
    { "rel" : "call-elevator", ... },
    { "rel" : "insert-key", ... },
    {
      "rel" : "obstruct-doors",
      "title" : "Simulate obstruction",
      "method" : "POST",
      "href" : "/elevators/1",
      "fields" : [
        { "name" : "type", "type" : "hidden", "value" : "ObstructDoors", "required" : true }
      ]
    },
    { "rel" : "open-doors", ... },
    { "rel" : "select-floor", ... }
  ]
}
```

The same `GET`, issued a second earlier (`state: doorsOpen`) or a
second later (`state: idle`, doors now closed), simply would not
contain this operation at all — the same
`ObstructDoorsAffordanceContributor` that decides `main`'s equivalent
`<form>`'s existence decides this array entry's, unchanged between the
two branches.

## UI (Vue + Pinia)

`app/stores/elevator.ts`

```ts
obstructDoorsOperation: (state) =>
  state.status?.operations?.find((op) => op.rel === 'obstruct-doors') ?? null,
clearObstructionOperation: (state) =>
  state.status?.operations?.find((op) => op.rel === 'clear-obstruction') ?? null
```

```ts
async obstructDoors() {
  const operation = this.obstructDoorsOperation
  if (!operation) {
    this.error = 'Simulating an obstruction is not available right now.'
    return
  }
  try {
    this.status = await $fetch<ElevatorView>(operation.href, {
      method: operation.method as 'POST',
      headers: { Accept: 'application/vnd.elevator.state+json' },
      body: commandBody(operation)
    })
    this.error = null
  } catch {
    this.error = 'Unable to simulate an obstruction.'
  }
}
```

`app/components/StatusDisplay.vue`

```ts
const canObstruct = computed(() => store.obstructDoorsOperation != null)
const canClearObstruction = computed(() => store.clearObstructionOperation != null)
```

Same seam as `main`'s `formFor(rel)`, restated as a getter instead of a
DOM lookup: neither asks "is the door closing," both only ask "did the
last representation carry an operation with this `rel`."

## HTTP

```
POST /elevators/1
Accept: application/vnd.elevator.state+json
{"type":"ObstructDoors"}
```

— but only if `obstructDoorsOperation` was non-null, i.e. only if the
button was ever rendered enabled in the first place.

## Java

`ObstructDoorsAffordanceContributor.java` — unchanged from `main`:

```java
if (!"doorsClosing".equals(context.state().orElse(""))) return List.of();
```

`ObstructDoorsHandler.java` — unchanged from `main`:

```java
public List<DomainEvent> handle(ObstructDoorsCommand command) {
    Elevator elevator = store.find(command.elevatorId()).orElseThrow(...);
    List<DomainEvent> events = elevator.obstructDoors();
    store.save(elevator);
    effects.apply(elevator, events);
    return events;
}
```

## Tests

`ObstructDoorsControllerTest.java` and `ObstructDoorsAffordanceContributorTest.java`
are identical to `main`'s (confirmed by diffing the two branches'
copies directly — this is one of the few operations where the test
suite did not change at all when the HTML/Datastar layer was added):

```java
@Test
void obstructingClosingDoorsReopensThem() throws Exception {
```

```java
@Test
void presentOnlyWhileClosing() {
```

The domain layer's own `ElevatorDoorsTest.java` is likewise byte-for-
byte identical between the two branches:

```java
@Test
void givenClosing_whenObstructed_thenDoorsReopen() {
    Elevator elevator = idleElevator();
    elevator.openDoors();
    elevator.closeDoors();

    List<DomainEvent> events = elevator.obstructDoors();

    assertThat(elevator.state()).isInstanceOf(ElevatorState.DoorsOpen.class);
    assertThat(elevator.doors().obstructed()).isTrue();
    assertThat(events).hasAtLeastOneElementOfType(DoorsObstructed.class);
    assertThat(events).hasAtLeastOneElementOfType(DoorsOpened.class);
}
```

`elevator-ui/test/unit/elevatorStore.test.ts` does cover this
operation, unlike `crud`'s equivalent — `describe('useElevatorStore
doors', ...)` exercises `obstructDoors`/`clearObstruction` alongside
`openDoors`/`closeDoors`/`reportLoad` as one group, each asserted to
echo its own operation's hidden `type`:

```ts
it('follows each operation when present, echoing its hidden type', async () => {
  // ...
  await store.obstructDoors()
  expect(lastCommandBody).toEqual({ type: 'ObstructDoors' })
  // ...
})
```

It does not cover `StatusDisplay.vue`'s `obstructionWarning` computed,
or `canObstruct`/`canClearObstruction` — those are component-level,
and this branch, like the other two, has no component tests.

## Client-side result

`StatusDisplay.vue`'s obstruction warning:

```ts
const obstructionWarning = computed(() => {
  return store.status?.obstructed ? 'Doors blocked — cannot close' : ''
})
```

reads the raw `obstructed` boolean, same as `canCloseDoors` did in the
`CallElevator`/`ObstructDoors` traces for `crud` — but unlike `crud`'s
`canCloseDoors`, this branch's `closeDoors`-availability question is
never asked here at all: `StatusDisplay.vue`'s Close doors button is
gated by `store.closeDoorsOperation != null`, a presence check, not a
re-derivation of "`DOORS_OPEN` and not obstructed." The obstruction
warning text is the one remaining piece of genuine state
interpretation on this branch's obstruction path, and it interprets a
boolean, not a state name.

## What this client needed to know about the state machine

Nothing about *legality* — same as `main`. The one thing this
component still reads directly (`obstructed`, to decide whether to
show a warning sentence) is a display concern, not a permission: the
permission question was already answered by which operations arrived.
