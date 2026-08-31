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
const inMaintenance = computed(() => store.status?.state === 'OUT_OF_SERVICE')
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
