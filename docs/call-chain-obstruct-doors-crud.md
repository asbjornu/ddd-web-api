# Call chain: `ObstructDoors` — `crud`

A rider simulates blocking the doors while they are closing, in the
`crud` variant of this repository.

## UI (Vue + Pinia)

`app/components/StatusDisplay.vue`

```vue
<input type="checkbox" :checked="store.status?.obstructed ?? false"
       @change="store.toggleObstruction()" />
<p v-if="obstructionWarning" class="obstruction-warning">{{ obstructionWarning }}</p>
```

```ts
const canCloseDoors = computed(() => {
  return store.status?.state === 'DOORS_OPEN' && !store.status?.obstructed // knows the exact "DOORS_OPEN" state name and that obstruction blocks closing
})
const obstructionWarning = computed(() => {
  return store.status?.obstructed ? 'Doors blocked — cannot close' : ''
})
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

## HTTP

`POST /api/elevators/1/obstruction` `{"obstructed":true}` to the BFF (no
auth header needed — a rider action), then
`POST http://elevator-api:8080/elevators/1/obstruction`, same body —
but only once the BFF's own shape check passes:

```ts
// server/api/elevators/[id]/obstruction.post.ts
const body = await readBody<{ obstructed?: unknown }>(event)

if (typeof body?.obstructed !== 'boolean') {
  throw createError({ statusCode: 400, statusMessage: 'Invalid obstructed value' })
}

return await $fetch(`${config.serviceApiUrl}/elevators/${id}/obstruction`, {
  method: 'POST',
  body
})
```

`ElevatorService.setObstruction`, quoted below, never validates this
itself — an unrecognised JSON shape for `obstructed` would already fail
at Jackson's own deserialization layer before the method is entered.
This check exists purely to answer that same rejection with this
file's own message instead of Java's, and duplicates an outcome that
was already going to happen regardless.

## Java

`controller/DoorController.java`

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

## Tests

`DoorControllerTest.java` covers the reopening behaviour this endpoint
triggers, but through `close-doors`'s own conflict responses rather
than any assertion on `setObstruction` in isolation — there is no unit
test for `ElevatorService.setObstruction` itself, only its observed
effects via `MockMvc`:

```java
@Test
void obstructionReopensClosingDoors() throws Exception {
    mockMvc.perform(post("/elevators/1/open-doors"));
    mockMvc.perform(post("/elevators/1/close-doors"));

    mockMvc.perform(post("/elevators/1/obstruction")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"obstructed\": true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.obstructed", is(true)));

    // On next read, obstruction should have triggered re-open
    mockMvc.perform(get("/elevators/1/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.doorState", is("OPEN")))
            .andExpect(jsonPath("$.state", is("DOORS_OPEN")));
}

@Test
void closeDoorsWhenObstructedReturnsConflict() throws Exception {
    mockMvc.perform(post("/elevators/1/obstruction")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"obstructed\": true}"));
    mockMvc.perform(post("/elevators/1/open-doors"));

    mockMvc.perform(post("/elevators/1/close-doors"))
            .andExpect(status().isConflict());
}
```

Nothing tests `canCloseDoors`'s client-side re-derivation of
`DOORS_OPEN && !obstructed`, or the checkbox's own always-enabled
behaviour this section describes — `elevator-ui/test/unit/elevatorStore.test.ts`
never calls `toggleObstruction`, and the e2e smoke test never checks
the checkbox at all. Nor is there any test for `obstruction.post.ts`'s
own new shape check — the BFF's copy of the rule is, like `CallElevator`'s,
verified by nothing but whatever request happens to reach it first.

## Client-side result

`fetchStatus()` resolves, `store.status.obstructed` flips, and two
*separate* pieces of client code now have to independently reason
about what that means:

`StatusDisplay.vue`'s `canCloseDoors`, quoted above, has to know that
obstruction only matters relative to `DOORS_OPEN` — obstructing an idle
car changes the flag but has no visible consequence here, a business
rule this component re-derives from two raw fields every time it
renders.

`ElevatorShaft.vue`'s door-state watcher (the same one that delays the
door-open reveal after an arrival, see the `CallElevator` trace for the
rest of it) treats `obstructed` as just another input to
`delayedDoorState`, by way of `store.status.doorState` — the door's
*visual* state (`open`/`closing`/`closed`) is a value the server
already computed by folding obstruction into it server-side (see
`ElevatorService.recomputeState`'s own `isOverloaded`/obstruction
handling), so this part, at least, the client does not have to
re-derive itself. What it *does* have to know is which of those three
door-state strings counts as "car looks open"
(`delayedDoorState.value === 'open' || delayedDoorState.value === 'closing'`)
— the same enum-value-by-value knowledge as everywhere else in this
component.

## What this client needed to know about the state machine

`canCloseDoors` is the sharper example: `DOORS_OPEN && !obstructed` is
a business rule (can the doors actually close right now), not a display
concern, and it lives entirely in the Vue component, duplicating
whatever `ElevatorService`'s own close-doors logic checks server-side.
Nothing connects the two copies; if either side's rule changes
without the other, the button either lies about being disabled or
about being enabled.
