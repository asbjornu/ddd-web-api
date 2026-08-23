<!--
  Domain-driven web APIs -- Reveal.js deck.

  Styled to match the presenter's actual decks (Hypermedia: The First
  2000 Years / The REST And Then Some / Compositional UIs): a title
  slide framed as an HTTP exchange, a spare bio slide, pseudo-HTML
  `<tag>` / `</tag>` section dividers on their own slide, and content
  slides that are almost entirely real HTTP requests/responses and
  JSON -- one exchange per slide, minimal prose, no bullet-heavy
  "slideument" pages. A single running worked example (the toaster's
  Off -> On -> Heating -> Idle -> Shutting down state machine) is
  mirrored here with the elevator's own state machine, request by
  request.

  Horizontal separator: `---` (topic change).
  Vertical separator:   `----` (a beat within one topic).
  Speaker notes: a line reading exactly `Note:` starts the note for
  the slide above it.

  Present with: `npx reveal-md docs/talk.md`

  Timing budget (50 min total):
    00 Title + who I am ................................... 2 min
    01 Meet the elevator .................................. 4 min
    02 The CRUD problem .................................... 7 min
    03 DDD principles for web APIs ......................... 9 min
    04 Building it: CRUD vs. REST+DDD ..................... 18 min
    05 Honest limits ........................................ 4 min
    06 Conclusion + Q&A ..................................... 6 min
-->

```
GET /presentation HTTP/1.1

HTTP/1.1 200 OK
Content-Type: application/json

{
  "what": "Domain-driven web APIs",
  "where": "Your Conference Here",
  "when": "2026-08-24T09:00+02:00"
}
```

---

# Asbjørn Ulsberg

### Software Architect, Programmer, Geek, Demoscener

- 25+ years professional programming experience
- Computer hobbyist since 1989
- Believes Fielding might be on to something

Note:
Title + bio, ~2 min total. State the title, then this slide, then move
straight on -- the bio is texture, not content. Don't linger.

---

# `<domain-driven-web-apis>`

Note:
Section divider (~5 sec). Everything from here until the matching
closing tag is this talk.

---

A box that goes up and down.

----

You already understand it perfectly.

----

*(shaft animation, live if the app is up)*

Note:
If `elevator-ui` is running, switch to the browser and show the
shaft/car animation live here, no mention of REST yet. If not, skip
straight past this slide. (~20 sec if live)

----

A rider calls it, waits, gets on, picks a floor, gets off.

----

A technician turns a key, does maintenance, turns it back.

----

We'll call the first one **the Rider**.

----

And the second, **the Technician**.

----

Small enough to hold in your head.

Rich enough to matter.

Note:
This whole beat (~4 min) is the "toaster" move from The REST And Then
Some -- an approachable physical device, introduced in plain language
before any domain vocabulary lands on top of it.

---

# `<crud>`

---

```java
// elevator-api/.../controller/ElevatorStatusController.java
@GetMapping("/elevators/{id}/status")
public Elevator status(@PathVariable Long id) {
    return elevatorService.getStatus(id);
}
```

----

```json
{ "id": 1, "state": "IDLE", "currentFloor": 3, "doorState": "CLOSED" }
```

----

Given only this response --

what may this client legally do next?

Note:
Pause. Let the room actually try to answer. (~20 sec)

----

Nobody can answer.

Because the representation doesn't say.

Note:
This is the whole talk in one slide. Don't over-explain it -- the rest
of the section shows why it doesn't say. (~1 min for this subsection)

---

```java
// elevator-api/.../service/ElevatorService.java
public Call call(Long elevatorId, Call request) {
    Elevator elevator = findElevator(elevatorId);
    recomputeState(elevator);

    if (request.getFloor() < 1 || request.getFloor() > properties.floors()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid floor");
    }
    if (elevator.getState() == ElevatorState.OUT_OF_SERVICE
            || elevator.getState() == ElevatorState.EMERGENCY_RECALL) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Elevator is not in service");
    }
    // ...validation, dispatch, persistence, all in one 501-line class
}
```

----

501 lines.

Validation, state machine, dispatch, door timing, overload detection,
maintenance, recall, persistence.

----

# God Object.

Note:
Name the smell out loud. ~1.5 min for this subsection.

---

```java
// elevator-api/.../controller/MaintenanceController.java
@PostMapping("/elevators/{id}/maintenance")
public Elevator maintenance(@PathVariable Long id,
        @RequestBody Map<String, Boolean> body) {
    boolean enable = body.getOrDefault("maintenance", false);
    if (enable) {
        return elevatorService.enterMaintenance(id);
    } else {
        return elevatorService.exitMaintenance(id);
    }
}
```

----

Nobody says "set maintenance to false."

They say: "I'm done -- take it back into service."

Note:
~1 min. The assignment-vs-message argument, made concrete.

---

```java
// elevator-api/.../model/Elevator.java
/**
 * ...used as the JPA entity, the domain model, and (for now) the
 * JSON representation -- see the "Model reuse" code smell in
 * docs/architecture.md.
 */
@Entity
public class Elevator { /* ... */ }
```

----

One class. Three masters.

The API has no vocabulary for *behavior*.

Only for *state*.

Note:
~1.5 min. This is the pivot into the next section -- land the last
line as a genuine turn.

---

# `</crud>`

---

# `<domain-driven-design>`

---

CRUD is four operations over rows.

----

This elevator is **ten behaviors** over an aggregate.

----

`CallElevator` `SelectFloor` `OpenDoors` `CloseDoors`

`ObstructDoors` `ClearObstruction` `ReportLoad`

`EnterMaintenance` `ExitMaintenance` `TriggerEmergencyRecall`

----

A command is a message: "do this."

Not an assignment: "become this."

Note:
~2.5 min for this subsection.

---

```java
// elevator-api/.../shared/domain/ElevatorState.java
public sealed interface ElevatorState {
    record Idle() implements ElevatorState {}
    record DoorsOpen() implements ElevatorState {}
    record DoorsClosing() implements ElevatorState {}
    record MovingUp(Floor destination) implements ElevatorState {}
    record MovingDown(Floor destination) implements ElevatorState {}
    record OutOfService() implements ElevatorState {}
    record EmergencyRecall(Floor recallFloor) implements ElevatorState {}
}
```

----

`MovingUp` and `MovingDown` carry their destination.

Impossible states are unrepresentable.

Note:
~2 min. Direct before/after callback to the old `recomputeState()`
if/switch chain from the CRUD section.

---

In `outOfService`, there is no `call-elevator` affordance.

Not a disabled button.

Simply absent.

Note:
~1.5 min. This is the section's central claim.

---

```java
// elevator-api/.../shared/hypermedia/AffordanceContributor.java
public interface AffordanceContributor {
    List<Affordance> contribute();
}
```

A new behavior means a new implementation of this interface.

A new directory. Not an edit to this file.

Note:
~1.5 min.

---

> "A REST API should be entered with no prior knowledge beyond the
> initial URI... all future actions are discoverable from
> server-provided media type definitions and link relations."
>
> -- Roy Fielding, 2008

Note:
~1 min. One slide, one quote, no lecture -- this audience already got
the theory in a prior talk. Bridge straight into the demo.

---

# `</domain-driven-design>`

---

# `<building-it>`

Note:
Section divider. Core section, ~18 min. Live demo against the running
app if it's up; every slide also stands alone as backup. This section
mirrors the toaster's Off -> On -> Heating state-machine walkthrough
from a prior talk, one HTTP exchange at a time.

---

Idle

```
GET /elevators/1 HTTP/1.1
```

----

```json
HTTP/1.1 200 OK

{
  "id": "/elevators/1",
  "state": "idle",
  "currentFloor": 3,
  "operations": [{
    "rel": "call-elevator",
    "method": "POST",
    "href": "/elevators/1/calls",
    "expects": { "floor": "number", "direction": ["up", "down"] }
  }, {
    "rel": "enter-maintenance",
    "method": "POST",
    "href": "/elevators/1/maintenance"
  }]
}
```

Note:
~1 min. Compare directly to the CRUD status endpoint's bare
`{"state": "IDLE"}` from section 02 -- same state, now carrying what
may legally happen next.

---

Idle

```
POST /elevators/1/calls HTTP/1.1

{ "floor": 5, "direction": "up" }
```

----

```json
HTTP/1.1 202 Accepted

{
  "id": "/elevators/1",
  "state": "movingUp",
  "currentFloor": 3,
  "destination": 5,
  "operations": [{
    "rel": "trigger-emergency-recall",
    "method": "POST",
    "href": "/elevators/1/recall"
  }]
}
```

Note:
~1.5 min. Note what's gone: no `call-elevator`, no
`enter-maintenance` -- both absent while moving, not erroring.

---

movingUp

```
GET /elevators/1 HTTP/1.1
```

Elevator arrives. Doors open automatically.

----

```json
HTTP/1.1 200 OK

{
  "id": "/elevators/1",
  "state": "doorsOpen",
  "currentFloor": 5,
  "operations": [{
    "rel": "select-floor",
    "method": "POST",
    "href": "/elevators/1/car-calls",
    "expects": { "floor": "number" }
  }, {
    "rel": "close-doors",
    "method": "POST",
    "href": "/elevators/1/doors/close"
  }]
}
```

Note:
~1.5 min. `select-floor` appears now that the rider is aboard --
never available while the car was moving.

---

doorsOpen, overloaded

```
PUT /elevators/1/load HTTP/1.1

{ "kilograms": 850 }
```

----

```json
HTTP/1.1 200 OK

{
  "id": "/elevators/1",
  "state": "doorsOpen",
  "load": { "kilograms": 850, "capacity": 800 },
  "operations": [{
    "rel": "close-doors",
    "method": "POST",
    "href": "/elevators/1/doors/close"
  }]
}
```

Note:
~1.5 min. `select-floor` is gone. Not a 409 -- simply absent, the
moment the load value object reports overloaded.

---

Rider tries anyway:

```
POST /elevators/1/car-calls HTTP/1.1

{ "floor": 9 }
```

----

```json
HTTP/1.1 409 Conflict
Content-Type: application/problem+json

{
  "type": "https://elevator.example/problems/overloaded",
  "title": "Car is overloaded",
  "status": 409,
  "detail": "Remove weight before selecting a floor.",
  "operations": [{
    "rel": "close-doors",
    "method": "POST",
    "href": "/elevators/1/doors/close"
  }]
}
```

Note:
~2 min. RFC 9457. The refusal carries the same `operations`
vocabulary as success -- a client that understood one already
understands the other. This is `ProblemJsonRenderer`: a fifth
renderer over the same representation model, no special-cased error
format.

---

Same resource. Four representations.

```
GET /elevators/1 HTTP/1.1
Accept: application/vnd.siren+json
```

----

```json
{
  "class": ["elevator"],
  "properties": { "state": "idle", "currentFloor": 3 },
  "actions": [{
    "name": "call-elevator",
    "method": "POST",
    "href": "/elevators/1/calls",
    "fields": [
      { "name": "floor", "type": "number" },
      { "name": "direction", "type": "text" }
    ]
  }]
}
```

Note:
~2 min. Same URL, same state, same three affordances -- as Siren.
Then: `application/vnd.elevator.state+json`, JSON-LD/Hydra, and
`text/html` as an actual `<form>`, all from the same code path. One
affordance model, N renderers; adding a format never touches the
domain.

---

```
POST /elevators/1/key HTTP/1.1
```

----

```
HTTP/1.1 401 Unauthorized
WWW-Authenticate: Bearer
  resource_metadata="/.well-known/oauth-protected-resource"
```

Note:
~2 min. Turning the key doesn't change elevator state -- it starts an
authorization exchange (RFC 9728). The client discovers the issuer;
it's never configured with it.

---

> "Validate once at the border. Let the type carry the proof."
>
> -- Bergh Johnsson & Deogun, *Domain-Driven Security*

`TriggerEmergencyRecall` requires the `elevator:recall` scope --
part of what the command *means*, not middleware standing in front of
it.

Note:
~2 min. The "aha" of the section -- most audiences file auth as pure
infrastructure.

---

New capability = a new `rel`.

Existing clients ignore what they don't recognize, and keep working.

No version number, anywhere.

Note:
~2 min. Closes the core section on the thesis, now demonstrated
rather than asserted. Callback: "remember the CRUD version couldn't
tell you what you could do next?"

---

# `</building-it>`

---

# `<honest-limits>`

Note:
Section divider. ~4 min. Do not cut this section for time.

---

**Scale.**

The elevator is small -- for a live demo.

Not because the pattern doesn't scale. The cost is real.

----

**Simplicity.**

An address book doesn't need any of this.

DDD and hypermedia are a cost you pay for behavioral complexity.

----

**Our own rules.**

`vnd.elevator.state+json` is a teaching device.

Not a recommendation.

Note:
~4 min total for these three beats. Each is one sentence of claim,
one of rebuttal -- not a debate.

---

# `</honest-limits>`

---

# `</domain-driven-web-apis>`

---

The CRUD version and the REST+DDD version served the exact same
elevator.

One could tell a client what it could legally do next.

One couldn't.

Note:
~1 min close.

---

# Questions?

---

# Thank You!

Asbjørn Ulsberg

- this repo, all slices, both versions, in git history
