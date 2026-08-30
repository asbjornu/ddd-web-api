# Call chain: `TriggerEmergencyRecall`

A technician presses the panic button. Traced end to end, in both the
`crud` and `main` (REST + DDD) variants of this repository — see
`docs/architecture.md`'s "Key-switch and authorization" section for the
domain reasoning behind `main`'s side of this.

## `crud`

**UI (Vue + Pinia)** — `app/components/StatusDisplay.vue`

```vue
<button class="emergency-btn" @click="store.triggerEmergencyRecall()">
  Emergency recall
</button>
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

**BFF** — `server/api/elevators/[id]/emergency-recall.post.ts`

```ts
const token = requireToken(event) // pulled from the HttpOnly cookie set when the key was inserted
return await $fetch(`${config.serviceApiUrl}/elevators/${id}/emergency-recall`, {
  method: 'POST',
  headers: { Authorization: `Bearer ${token}` }
})
```

**HTTP #1**: `POST /api/elevators/1/emergency-recall` (browser, no
credential of its own — the cookie rides along). **HTTP #2**:
`POST http://elevator-api:8080/elevators/1/emergency-recall`,
`Authorization: Bearer <token>`.

**Java** — the gate is a filter, not the controller:

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

## `main`

**Step 0 — the hypermedia control this button came from.** This one
takes two requests to get to, both captured live. First, submitting the
technician key-switch form (see `docs/architecture.md`'s "Key-switch
and authorization" section) sets an `HttpOnly` cookie:

```
$ curl -sD- -X POST http://127.0.0.1:8000/elevators/1/key-switch/session \
    -H "Content-Type: application/json" -d '{"secret":"dev-secret-key"}' \
    -c cookies.txt

HTTP/1.1 200 OK
Content-Type: application/vnd.elevator.state+json
Set-Cookie: technician_token=eyJraWQ...; Path=/elevators; Max-Age=899;
            HttpOnly; SameSite=Strict
```

Then the *next* `GET /elevators/1`, cookie attached, renders a form that
was absent a moment ago:

```
$ curl -s http://127.0.0.1:8000/elevators/1 \
    -H "Accept: text/html" -H "Datastar-Request: true" -b cookies.txt

<div id="elevator">
<div id="elevator-content">
<dl>
  <dt>currentFloor</dt><dd>1</dd>
  <dt>state</dt><dd>idle</dd>
  ... <!-- direction, doorPosition, obstructed, weightKg, capacityKg,
           destinationFloor, travelSecondsPerFloor, doorOpenTimeoutSeconds -->
</dl>
<!-- ...self/updates links, call-elevator, enter-maintenance,
     withdraw-key, open-doors, select-floor... -->
<form action="/elevators/1" method="post" data-rel="trigger-emergency-recall"
      data-on:submit="@post('/elevators/1', {contentType: 'form'})">
  <fieldset>
  <legend>Trigger emergency recall</legend>
  <label>type
    <input type="hidden" name="type" value="TriggerEmergencyRecall" required>
  </label>
  <button type="submit">Trigger emergency recall</button>
  </fieldset>
</form>
</div>
<div id="elevator-events" data-init="@get('/elevators/1/events')"></div>
</div>
```

`panels.client.ts` never inspects the cookie, checks a scope, or knows
"technician" is a concept — it only ever asks `formFor('trigger-
emergency-recall')`, the same DOM lookup `ObstructDoors` used for a
state gate, now answering an authorization gate instead. The class
that actually decided this form should exist for *this* cookie's
holder — `TriggerEmergencyRecallAffordanceContributor`, checking
`context.principal().hasScope("elevator:recall")` — is quoted in full
below.

**UI** — `app/plugins/panels.client.ts`

```ts
emergencyButton.addEventListener('click', () =>
  submitHiddenForm('trigger-emergency-recall', {}))
emergencyButton.disabled = !formFor('trigger-emergency-recall')
```

**HTTP**: `POST /elevators/1` with `type=TriggerEmergencyRecall` — the
cookie (set once at key-insert, same mechanism as `crud`) rides along
automatically, same origin.

**Java** — the check lives inside the command endpoint itself, not a
filter in front of it:

```java
// TriggerEmergencyRecallController.java
if (!principal.hasScope("elevator:recall")) {
    return responses.problem(HttpStatus.FORBIDDEN, accept,
        ElevatorRepresentations.forbidden(
            "This operation requires the emergency recall key."));
}
handler.handle(new TriggerEmergencyRecallCommand(id));
```

And the same rule, restated for what the client is allowed to even
see:

```java
// TriggerEmergencyRecallAffordanceContributor.java
if (!context.principal().hasScope("elevator:recall")) return List.of();
if ("emergencyRecall".equals(context.state().orElse(""))) return List.of(); // pre-empts everything else...
```

```java
// TriggerEmergencyRecallHandler.java
Floor recallFloor = new Floor(properties.recallFloor(), true);
List<DomainEvent> events = elevator.triggerEmergencyRecall(recallFloor); // the aggregate itself decides
```

## The difference, reasoned

`crud` enforces authority as a URL pattern matched by a Spring Security
filter chain — a config file, disconnected from the controller and the
service, that a reviewer has to cross-reference to know this action is
privileged at all. `main` makes the exact same scope check part of what
the command *means*: written once in the `AffordanceContributor` (so
the button never renders for a caller who cannot use it) and checked
again, identically, in the `CommandEndpoint` (so a direct `POST` from
someone who guessed the shape still gets refused) — the same string,
the same rule, two honest places instead of one hidden one.

## Across all three operations in this series

`crud` always needs *N+1* requests for one user action (the mutation,
plus however many `fetch*()` calls figure out what actually happened),
because the entity that got saved is not the same shape as "what should
the UI show now." `main` always needs exactly one, because the response
*is* the new representation, and anything that happens later (a
scheduled arrival, an SSE-eligible event) is pushed, not polled. And
every legality check in `crud` is discovered by trying (`if`/`throw`,
translated to a status code after the fact); every one in `main` is
discovered by looking (the affordance is there, or it is not), with the
aggregate's own refusal as the only thing that can actually stop a
command that lied about that.
