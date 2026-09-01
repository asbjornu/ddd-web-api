# Call chain: `TriggerEmergencyRecall` — `crud`

A technician presses the panic button, in the `crud` variant of this
repository.

## UI (Vue + Pinia)

`app/components/StatusDisplay.vue`

```vue
<button v-if="!inMaintenance" @click="store.enterMaintenance()">Enter maintenance</button>
<button v-if="inMaintenance" @click="store.exitMaintenance()">Exit maintenance</button>
<button class="emergency-btn" @click="store.triggerEmergencyRecall()">
  Emergency recall
</button>
```

```ts
const inMaintenance = computed(() => store.status?.state === 'OUT_OF_SERVICE') // knows the exact "OUT_OF_SERVICE" state name
```

`app/stores/elevator.ts`

```ts
async triggerEmergencyRecall() {
  await $fetch(`/api/elevators/${ELEVATOR_ID}/emergency-recall`, {
    method: 'POST'
  })
  await this.fetchStatus()
}
```

## HTTP

`server/api/elevators/[id]/emergency-recall.post.ts`

```ts
const token = requireToken(event) // pulled from the HttpOnly cookie set when the key was inserted
return await $fetch(`${config.serviceApiUrl}/elevators/${id}/emergency-recall`, {
  method: 'POST',
  headers: { Authorization: `Bearer ${token}` }
})
```

**#1**: `POST /api/elevators/1/emergency-recall` (browser, no credential
of its own — the cookie rides along). **#2**:
`POST http://elevator-api:8080/elevators/1/emergency-recall`,
`Authorization: Bearer <token>`.

Still a pure proxy, unchanged: `ElevatorService.triggerEmergencyRecall`
never refuses (see its own "Java" section below), so there is nothing
this route could pre-check that the service would ever reject anyway.
Its sibling, `maintenance.post.ts` — the route this same panel's Enter/
Exit maintenance buttons call — is not so lucky:

```ts
// server/api/elevators/[id]/maintenance.post.ts
const status = await fetchStatusForValidation(config.serviceApiUrl, id)
if (body.maintenance) {
  if (status.state === 'OUT_OF_SERVICE') {
    throw createError({ statusCode: 409, statusMessage: 'Already in maintenance' })
  }
  // ...duplicates ElevatorService.enterMaintenance/exitMaintenance's own conflicts
}
```

## Java

The gate is a filter, not the controller:

```java
// SecurityConfig.java
.requestMatchers("/elevators/*/emergency-recall")
    .hasAuthority("SCOPE_elevator:recall")
```

```java
// MaintenanceController.java
@PostMapping("/elevators/{id}/emergency-recall")
public Elevator emergencyRecall(@PathVariable Long id) {
    return elevatorService.triggerEmergencyRecall(id); // controller has no idea auth even happened
}
```

`ElevatorService.triggerEmergencyRecall` clears pending calls and car
calls, resets obstruction and weight, and sets state — a roughly
25-line method mutating the same `Elevator` entity, saved once at the
end.

## Tests

`MaintenanceControllerTest.java` covers the scope check
(`SecurityConfig.java`'s filter, not this method) via three separate
negative cases, and the recall itself via `MockMvc`:

```java
@Test
void emergencyRecallSetsDirectionToRecallFloor() throws Exception {
    mockMvc.perform(post("/elevators/1/calls")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"floor\": 3, \"direction\": \"UP\"}"));

    Thread.sleep(5000);

    mockMvc.perform(post("/elevators/1/emergency-recall")
                    .with(recallToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state", is("EMERGENCY_RECALL")))
            .andExpect(jsonPath("$.direction", is("DOWN")))
            .andExpect(jsonPath("$.targetFloor", is(1)));
}
```

`ElevatorServiceTest.java`'s own recall cases go further, and one of
them documents a real gap directly in its own body — worth quoting in
full, since it is exactly the kind of client-observability problem
this file's "Client-side result" section is about:

```java
@Test
@DisplayName("recall at the recall floor skips EMERGENCY_RECALL and leaves the doors shut")
void emergencyRecallAtTheRecallFloorGoesStraightOutOfService() {
    // The seeded car sits at floor 1, which is the recall floor.
    Elevator recalled = service.triggerEmergencyRecall(ELEVATOR_ID);

    assertThat(recalled.getState()).isEqualTo(ElevatorState.OUT_OF_SERVICE);
    assertThat(recalled.getTargetFloor()).isNull();

    // Recorded rather than endorsed. docs/architecture.md says recall
    // "opens its doors and then automatically transitions to
    // outOfService", but on this path the doors are set CLOSED and the
    // EMERGENCY_RECALL state is never entered at all -- so a client
    // watching for it would miss the transition entirely. Worth
    // resolving when recall moves onto the aggregate.
}
```

No client-side test exercises `store.triggerEmergencyRecall`,
`inMaintenance`, or `ElevatorShaft.vue`'s colour computeds — the
technician flow is entirely outside what
`elevator-ui/test/unit/elevatorStore.test.ts` or the e2e smoke test
cover. Nor is there any test for `maintenance.post.ts`'s own new
conflict pre-checks (quoted in this file's "HTTP" section above) — the
BFF's duplicated copy of `enterMaintenance`/`exitMaintenance`'s rules
is, like every other route this branch's BFF now validates, unverified
by anything but a request actually reaching it.

## Client-side result

`fetchStatus()` resolves, and — eventually, once the recall settles —
`store.status.state` becomes `'OUT_OF_SERVICE'`. Three separate pieces
of client code now change their mind about what is on screen, all from
that one raw string:

`StatusDisplay.vue`'s `inMaintenance` (quoted above) flips the
Enter/Exit maintenance button — meaning the client infers "the recall
has finished and settled into maintenance" purely from seeing the same
state string `EnterMaintenance` would have produced on its own; nothing
distinguishes "a technician explicitly entered maintenance" from "an
emergency recall just automatically got there" at this component,
because the wire shape does not either.

`ElevatorShaft.vue`'s `isEmergency`/`isOutOfService` computeds (already
quoted in the `CallElevator` trace) drive the car's colour —
gold during the recall, then red once `isOutOfService` takes over —
so the same transition this button just triggered has to be recognised
twice more, by two more string comparisons, in a completely different
file.

## What this client needed to know about the state machine

That `EMERGENCY_RECALL` is a real, distinct state that automatically
settles into `OUT_OF_SERVICE` on its own — this component was never
told that happens, it just has to notice by polling and comparing
strings it already hardcodes elsewhere for an unrelated reason
(whether "Enter maintenance" should be its label). The authorization
rule that gated the button in the first place lives in a *different*
file again (`SecurityConfig.java`), which this component also has no
way to see: it renders the button unconditionally and finds out it was
forbidden only if the request comes back with a 403.
