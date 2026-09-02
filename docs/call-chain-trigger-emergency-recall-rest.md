# Call chain: `TriggerEmergencyRecall` — REST + DDD (`main`)

A technician presses the panic button, in this repository's current
(`main`) architecture. See `docs/architecture.md`'s "Key-switch and
authorization" section for the domain reasoning behind this variant's
approach to authorization.

## Step 0 — the hypermedia control this button came from

This one takes two requests to get to, both captured live. First,
submitting the technician key-switch form (its own `insert-key`
affordance, always present) sets an `HttpOnly` cookie:

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

`panels.ts` never inspects the cookie, checks a scope, or knows
"technician" is a concept — it only ever asks `formFor('trigger-
emergency-recall')`, the same DOM lookup `ObstructDoors` used for a
state gate, now answering an authorization gate instead. The class that
actually decided this form should exist for *this* cookie's holder —
`TriggerEmergencyRecallAffordanceContributor`, checking
`context.principal().hasScope("elevator:recall")` — is quoted in full
below.

## UI

`elevator-ui/src/panels.ts`

```ts
emergencyButton.addEventListener('click', () =>
  submitHiddenForm('trigger-emergency-recall', {}))
emergencyButton.disabled = !formFor('trigger-emergency-recall')
```

## HTTP

`POST /elevators/1` with `type=TriggerEmergencyRecall` — the cookie
(set once at key-insert, same mechanism as `crud`) rides along
automatically, same origin. Same `data-on:submit="@post(...)"` /
`Datastar-Mode: outer` round trip as `CallElevator`'s own file
describes in full, including the real SSE wire format for anyone else
already watching when the recall settles into `outOfService`.

## Java

The check lives inside the command endpoint itself, not a filter in
front of it:

```java
// TriggerEmergencyRecallController.java
if (!principal.hasScope("elevator:recall")) {
    return responses.problem(HttpStatus.FORBIDDEN, accept,
        ElevatorRepresentations.forbidden(
            "This operation requires the emergency recall key."));
}
handler.handle(new TriggerEmergencyRecallCommand(id));
```

And the same rule, restated for what the client is allowed to even see:

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

## Tests

Same Java suite as `json-hypermedia`, byte-for-byte (see that file's
own "Tests" section — `TriggerEmergencyRecallControllerTest.java`,
`TriggerEmergencyRecallAffordanceContributorTest.java`, and the shared
domain-level `ElevatorEmergencyRecallTest.java`, none of which changed
when the BFF's OAuth exchange was replaced by this branch's
`KeySwitchSessionController`):

```java
@Test
void triggerEmergencyRecallTransitionsToEmergencyRecallWhenElsewhere() {
    Elevator elevator = Elevator.seed(new ElevatorId(1), new Floor(3), 800);

    List<DomainEvent> events = elevator.triggerEmergencyRecall(new Floor(1, true));

    assertThat(elevator.state())
            .isEqualTo(new ElevatorState.EmergencyRecall(new Floor(1, true)));
    assertThat(events).hasAtLeastOneElementOfType(EmergencyRecallTriggered.class);
    assertThat(events).noneMatch(EmergencyRecallCompleted.class::isInstance);
}
```

There is no client-side test for `KeySwitchSessionController`'s own
replacement of the BFF flow, nor for `panels.ts`'s technician
section at all — this variant, like `crud`, has no unit test layer on
the client, and the e2e smoke test never inserts a key. The one thing
this branch *can* claim over `json-hypermedia` is that there is simply
less untested surface to worry about: no `technicianKey.ts`, no RFC
9728 discovery step, no `client_credentials` grant — the whole BFF-side
flow that file's own "Tests" section flags as uncovered does not exist
here to be uncovered.

## Client-side result

The response (and, once the recall settles into `outOfService`, the
follow-up SSE push) drives `shaft.ts`'s colour toggles, quoted
in full in the `CallElevator` trace:

```ts
car.classList.toggle('oos', state === 'outOfService') // knows the exact "outOfService" state name
car.classList.toggle('emergency', state === 'emergencyRecall') // knows the exact "emergencyRecall" state name
```

and `panels.ts`'s technician-section rebuild, which reacts to
the *disappearance* of `enter-maintenance`/`exit-maintenance` rather
than to any state name at all:

```ts
maintenanceButton.disabled = !enterMaintenance && !exitMaintenance
```

— true only while `emergencyRecall` is active, per the affordance
contributor's own comment above ("pre-empts everything else"), but
`panels.ts` doesn't need to know *why* both are absent, only
that they are.

## What this client needed to know about the state machine

Almost nothing, and what little there is, is presentation: which two
state strings mean "colour the car gold/red." The authorization
decision that gates the button — the scope check — is never
duplicated here at all; the client's only signal is whether the form
was rendered, decided once, server-side, by the same class that will
also refuse the `POST` if a caller bypasses the button entirely.

## The difference, reasoned

`crud` enforces authority as a URL pattern matched by a Spring Security
filter chain — a config file, disconnected from the controller and the
service, that a reviewer has to cross-reference to know this action is
privileged at all. `json-hypermedia` moves the scope check into the
command endpoint itself, same as `main` below, but still needs a Nuxt
BFF to hold the technician's Bearer token at all (see this series'
`json-hypermedia` file for that flow) — a browser cannot attach a
bearer token to a same-origin request on its own, and this branch's
key-switch endpoint answers only with an RFC 9728 challenge, never a
cookie of elevator-api's own. `main` removes the BFF from this picture
entirely: `POST /elevators/{id}/key-switch/session` sets the cookie
itself, so the same scope check that already existed in
`json-hypermedia`'s command endpoint is now reachable same-origin, no
proxy required. Across all three, the exact same rule — written once
in the `AffordanceContributor` (so the button never renders for a
caller who cannot use it) and checked again, identically, in the
command endpoint (so a direct `POST` from someone who guessed the
shape still gets refused) — the same string, the same rule, two honest
places instead of one hidden one.

## Across all three operations in this series

`crud` always needs *N+1* requests for one user action (the mutation,
plus however many `fetch*()` calls figure out what actually happened),
because the entity that got saved is not the same shape as "what should
the UI show now." `json-hypermedia` already needs only one for the
command itself (the response *is* the new representation), but still
needs a second, BFF-proxied re-read after a technician's key-switch
action, because its unauthenticated SSE stream never carries
`operations` and the store has no other way to learn what a
*technician* may now do. `main` needs exactly one, full stop: no BFF
re-read, because there is no BFF — the same-origin `key-switch/session`
response is itself the moment the client would need to notice
anything, and everything after that is pushed. And every legality
check in `crud` is discovered by trying (`if`/`throw`, translated to a
status code after the fact); every one in `json-hypermedia` and `main`
is discovered by looking (the operation/affordance is there, or it is
not), with the aggregate's own refusal as the only thing that can
actually stop a command that lied about that.
