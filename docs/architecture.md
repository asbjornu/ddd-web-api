# Domain-driven web APIs — target architecture

This is a demo application for the talk "Domain-driven web APIs",
building on the ideas presented in the talk [What's In A Model][1]. This
file describes the architecture the application is being refactored
towards: a command-based, hypermedia-driven, CQRS-separated REST API,
replacing an earlier REST-ish CRUD API that intentionally mismatched its
underlying domain model. That earlier architecture is not described here
— it is preserved in this file's git history, and its code remains in the
tree until each slice below migrates it away.

The full argument, worked examples, media-type samples, and open
questions live in [`docs/plan.html`][2]; this file is the condensed,
actionable version for agents. Read the plan first when a decision here
seems under-justified — it almost certainly is justified there in more
depth than fits here.

## The thesis

A CRUD API cannot tell the client what it may legally do next, so the
client re-derives the rules itself: the domain leaks into the front-end's
store, its BFF routes, and its components, as three independently
drifting copies of one rule set. The client also ends up owning a copy of
the API's URL space — a hard-coded resource id, hand-built paths — which
is a *fact*, not a judgement call, and is falsifiable by running the test
suite against randomised URIs.

This architecture removes both copies by making the server say, in every
response, exactly what may be done next — as hypermedia — and by naming
every domain behaviour as a distinct command instead of a field
assignment. Neither idea alone is sufficient: named commands without
hypermedia are just RPC with extra paths; hypermedia over a CRUD surface
degenerates to "you may `PATCH` this," which the client already knew. The
two are load-bearing for each other.

## Domain

The business domain is a **single elevator (lift) control system**. It's
deliberately small and physical — everyone has an intuitive mental model
of how an elevator behaves — but rich enough in state, invariants, and
scheduling logic to have real workflows that don't map cleanly onto CRUD.

There are two personas, both interacting through the same UI: the
**Rider**, who wants to get from A to B (calls the elevator, selects a
floor, opens/closes doors), and the **Technician**, who wants to service
or safeguard the elevator (enters/exits maintenance mode, triggers
emergency recall) via a physical key-switch, gated by a scoped OAuth 2.0
token rather than a login.

### Building and elevators

The system serves a single building (there is no `Building` entity — the
service is deployed per building, so "which building" is a deployment
concern, not a domain concept). The building has a configurable number of
floors, seeded to 9. The building has one or more **elevators**, each
independently an aggregate root with its own state, request queue, and
doors. The API addresses elevators as resources from the start, but for
now exactly one elevator is seeded and used — dispatch/assignment logic
for multiple elevators is out of scope until a task explicitly asks for
it.

### Elevator state

Each elevator (aggregate root) is always in exactly one of these states,
represented as a sealed `ElevatorState` rather than a status string:

- `idle`: stationary at a floor, doors closed, no pending requests.
- `doorsOpen`: stationary at a floor, doors open (boarding/alighting).
- `doorsClosing`: doors in the process of closing; obstruction re-opens.
- `movingUp` / `movingDown`: travelling between floors to serve a
  request.
- `outOfService`: maintenance mode; ignores calls and floor selections.
  Reached either directly (`EnterMaintenance`) or automatically after an
  emergency recall completes; left only via `ExitMaintenance`.
- `emergencyRecall`: pre-empts everything else, travels directly to the
  recall floor (typically the ground floor), opens doors, and then
  automatically transitions to `outOfService`.

### Core workflows, as commands

CRUD is four operations over rows. The domain is ten behaviours over an
aggregate, and there is no total function from one to the other — each
behaviour below is a distinct command (see "Commands" below), not a
generic update:

- **`CallElevator`**: a rider at a floor requests the car, specifying a
  direction if not on the top/bottom floor. Adds a landing call to the
  request queue.
- **`SelectFloor`**: a rider inside the car selects a destination floor,
  adding a car call. Refused if the car is overloaded or in
  `outOfService`/`emergencyRecall` — refusal is the affordance's absence,
  not a runtime error.
- **Serve requests / travel**: the elevator services pending calls in
  direction-committed order (a simplified SCAN/LOOK algorithm) via
  `RequestQueue`. Movement is scheduled: when the aggregate commits to
  moving, it computes the arrival instant, and a scheduler emits
  `FloorReached` at that instant, rather than deriving state from
  elapsed wall-clock time on every read.
- **`OpenDoors` / `CloseDoors` / `ObstructDoors` / `ClearObstruction`**:
  doors auto-open on arrival and auto-close after a timeout (itself a
  scheduled event), interrupted and reversed by `ObstructDoors`. There's
  no real obstruction sensor; it's simulated via a rider UI button,
  usable while doors are closing.
- **`ReportLoad`**: sensor telemetry (simulated via a weight
  slider/input, range 0–1000kg). A `Load` value object owns
  `isOverloaded()` against the elevator's 800kg capacity; overload makes
  `select-floor` absent rather than returning a 409.
- **`EnterMaintenance`** (Technician): transitions to `outOfService` from
  any non-`emergencyRecall` state, cancelling all pending calls.
- **`ExitMaintenance`** (Technician): transitions `outOfService` back to
  `idle` — the single action that clears `outOfService`, whether reached
  via `EnterMaintenance` or automatically via a completed emergency
  recall.
- **`TriggerEmergencyRecall`** (Technician): pre-empts *any* state,
  clears the request queue, forces travel to the recall floor, opens
  doors, then automatically transitions to `outOfService`.

A command is a message ("do this"), not an assignment ("become this"): it
can be refused with a domain-specific reason, and it is the unit of
authorization, audit, and multiplicity. `InsertKey`/`WithdrawKey` are
deliberately *not* commands — a key switch changes no elevator state, so
they exist only as affordances that start an authorization exchange (see
"Key-switch and authorization" below), never as aggregate behaviour.

### Timing

Real wall-clock durations, kept short for a live demo: roughly 2 seconds
of travel per floor, 4 seconds of door-open timeout before auto-close.
Configurable via Spring `application.yml` properties, not hard-coded.
State transitions are scheduled forward (an arrival instant computed when
the aggregate commits to moving) rather than derived backward from
elapsed time on every read — this is what makes SSE possible and is why
no ticking background job re-derives state on a timer.

## Affordances: hypermedia over the aggregate

Every representation carries the operations legally available *right
now*, given the elevator's current state and the caller's authority.
Omission — not a disabled button — is how "not available" is expressed:
in `outOfService` there is no `call-elevator` affordance at all, so a
rider client shows no call button without ever being told what
maintenance is.

`AffordanceCatalog` is a Spring-injected `List<AffordanceContributor>`
that knows nothing about elevators as a concept beyond the interface it
implements. Adding a directory (a vertical slice, see below) adds an
affordance, which adds a form in HTML and an operation in all three JSON
formats, with no registry to edit and no switch on `rel`.

A refusal is a representation too: `Problem` is one more renderer
alongside the success formats, carrying an `operations` extension member
(RFC 9457 §3.2) with the same rel vocabulary, so a client that understood
a success already understands a failure.

## Four media types, one affordance model

The same resource, the same state, four content-negotiated
serialisations on the same URL:

- `text/html` — server-rendered forms (a hand-built `HtmlRenderer`, not
  a templating engine — see plan §12 for why JTE was dropped).
- `application/vnd.elevator.state+json` — a minimal bespoke format, kept
  only as a teaching device (see plan §18), not a recommendation.
- `application/vnd.siren+json` — the complete standard format.
- `application/ld+json` with Hydra — adds a machine-shared vocabulary.
- `application/problem+json` (RFC 9457) — a fifth renderer, for
  refusals.

One affordance model, N renderers: adding a format must never touch the
domain. Rels are dereferenceable URIs under a documented namespace; no
version number appears anywhere. New capability means a new rel;
existing clients ignore what they don't recognise and keep working.

## Identifiers and URIs

Three kinds of identifier exist, and only one may reach the wire as a
constructible thing:

- **Domain identifiers** (floor 3, direction up) are part of the
  ubiquitous language, legitimately public, and legitimately field
  values.
- **Surrogate identifiers** (`Elevator.id`, a JPA primary key) are
  nobody's business outside the persistence layer, and must never reach
  the wire.
- **Resource identifiers** (`/elevators/{opaque}`) are issued by the
  server and followed by the client, never constructed by it.

`ElevatorId` remains a domain value object; only the web layer's
`UriResolver` maps it to a resource identifier, in both a readable and an
opaque style. The test that proves the rule is being followed: the whole
suite must pass twice in CI, once against readable URIs and once against
randomised opaque ones. Representations carry links, not foreign keys —
a foreign key on the wire is an invitation to construct a URL from it.

## Command endpoints: no verbs in URLs

A URL names a resource, never a verb. Every command in this API is
invoked through exactly one endpoint per elevator, `POST
/elevators/{id}` — the same URL `GET` already reads the elevator's
representation from; a POST that changes it and a GET that reads it
are two methods on one resource, not two resources. Which behaviour a
POST invokes is the request body's job, not the request line's. The
body carries a `"type"` member naming the command (its command
record's own simple name, e.g. `"OpenDoors"`, `"SelectFloor"`) — the
same string every `AffordanceContributor` that offers the command
already places in a *hidden* `Field`
(`Field.hidden("type", "OpenDoors")`) among the affordance's own
fields, so a client never has to know or construct it: it simply
echoes every field's value back, overriding only the ones a form
actually collects.

This replaces what a CRUD-shaped API tends to reach for instead — a URL
per command, one for each verb (`/calls`, `/car-calls`, `/open-doors`,
`/close-doors`, `/obstruct-doors`, `/clear-obstruction`, `/weight`). A
`CommandsController` (shared kernel, `shared.web`) resolves the elevator
and dispatches by the body's `"type"` to whichever `CommandEndpoint` bean
declares that name; each slice still owns its own command, handler,
endpoint and affordance contributor exactly as before (see "Vertical
slices" below) — only the `@PostMapping` itself lives in one shared
place, since every slice needs the identical one. A `"type"` naming no
known command, or absent altogether, is a plain 400, not a 404: the
resource (the elevator) was found; the request just didn't name a
command.

## CQRS and domain events

Commands go through `command → handler → aggregate → events`,
synchronously, inside one slice; the aggregate is the only thing that
may refuse, and events are its only output. Queries never touch the
aggregate: each query slice owns its own read model and Flyway tables,
updated by synchronous projections. One H2 database, no eventual
consistency, no message broker.

The domain event hierarchy is the specification, documented with Adam
Dymitruk's Event Modeling (events past-tense facts, commands imperative
intentions, views present-tense projections; commands specified
Given/When/Then, views Given/Then). `InsertKey`/`WithdrawKey` and
`ElevatorViewed` are deliberately not events, for the same reason they
are not commands — no aggregate state changes.

SSE replaces polling: because transitions are scheduled rather than
derived from elapsed time, the server always knows the instant something
will happen, and can push at that instant instead of waiting to be
asked.

## Key-switch and authorization

The key-switch is a domain concept — a car panel has one, and the
ubiquitous language has a word for turning it — but it is not an
aggregate command: turning it changes no elevator state. It is an
affordance (`insert-key`) whose response is an authorization challenge,
not a mutation. Following `insert-key` gets a 401 challenge carrying RFC
9728 (Protected Resource Metadata), so the client discovers the issuer
rather than being configured with it.

Authorization here is modelled the way Bergh Johnsson & Deogun's
*Domain-Driven Security* argues it always should be: as a domain rule
expressed in the same model and the same ubiquitous language as
everything else, not as a cross-cutting technical concern bolted on in
front of it. Two of their patterns apply directly:

- **Validate once at the border; let the type carry the proof.** Just as
  `ElevatorId` is constructed only from a URI the server itself issued,
  and `Floor`/`Load` reject an out-of-range primitive at their
  constructor rather than downstream, the token completing the
  `insert-key` challenge is exchanged, once, at the security boundary,
  for a validated `Principal` — the only thing any handler or
  `AffordanceContributor` ever sees. No handler re-parses a scope string
  or re-checks for a missing principal; the type already guarantees it.
- **Authorization is a domain rule, not infrastructure.** A scope
  requirement on `TriggerEmergencyRecall` is not middleware standing in
  front of the command; it is part of what the command *means*, exactly
  as much as "not while already recalling" is. `AffordanceCatalog`
  already collapses authority and resource state into one predicate per
  contributor — the command is the right unit for this because it is
  already the unit of one domain behaviour, so it is also the natural,
  non-divisible unit to attach one authority requirement to. This is
  also why field-level authorization is hard to express honestly (a
  field is not a domain concept) while command-level authorization is
  not.

`elevator-auth` (a Spring Authorization Server) issues scoped tokens;
`elevator-api` validates them as a resource server. Two scopes exist and
are not interchangeable:

- `elevator:maintenance` permits entering and leaving maintenance.
- `elevator:recall` permits triggering an emergency recall.

Browser and machine clients converge on one validated `Principal`. The
browser gets a same-origin, stateless, `HttpOnly` cookie carrying the
token itself (not a session key — this is the sanctioned exception to
"cookies are session state," per Tilkov's REST anti-patterns, because
the cookie is self-contained and verified by signature rather than
indexing server-side state). Machine clients present `Authorization:
Bearer` directly. Either way, the client never learns which operations
are privileged or that scopes exist: a privileged affordance is simply
present or absent, computed by the same predicate — never a security
filter it cannot see — that decides every other affordance. The payoff:
a reviewer can read the aggregate and its contributors and see the
entire authorization policy without also reading a separate security
layer, because there is only one place it is written down.

## Vertical slices

One directory per domain behaviour, each holding everything that
behaviour needs: command, handler, endpoint, affordance descriptor,
tests. Not layers — high cohesion inside a slice, near-zero coupling
between them. A slice does not register itself anywhere; it implements
one interface, and the API starts describing it.

**Sliced** (independent, near-zero coupling):

- Commands, queries and their handlers
- Each command's own endpoint logic (a `CommandEndpoint` bean, dispatched
  to by the shared `POST /elevators/{id}` URL — see "Command endpoints:
  no verbs in URLs" above; the URL is shared, but no slice reaches into
  another's parsing or handling to use it)
- Affordance descriptors and availability rules
- Read models and the projections that fill them
- Tests — each slice is independently testable

**Deliberately shared** (the thing no single slice may own):

- The `Elevator` aggregate and its value objects
- The domain event hierarchy
- The four renderers — cross-cutting by media type, not by feature
- Persistence adapters

**Most of the API is unauthenticated, including endpoints with side
effects.** Only `maintenance` and `emergency-recall` require a scope.
Landing calls, car calls, door operations and both simulated sensors are
open, which follows from the Rider persona having no login. The sensors
are the sharp edge: `PUT /elevators/{id}/obstruction` has no
precondition and sets a flag that persists until explicitly cleared, so
one unauthenticated request stops the doors closing and therefore stops
the lift, indefinitely. That is acceptable here because obstruction is a
simulated sensor with a button in the rider UI -- but it is an artefact
of the simulation, not a design position. A real light curtain does not
accept HTTP from arbitrary parties; in a production shape these would be
device-authenticated inputs translated into domain events rather than
open endpoints.

Isolation earns its keep the further out you go and costs you the
further in. Emergency recall pre-empts *all* states; maintenance rejects
calls that `CallElevator` would otherwise accept; overload invalidates a
car call belonging to another slice. Give each slice its own elevator
state and those rules get restated per slice — the exact failure this
architecture exists to remove, rebuilt on the server instead of the
client.

The client (Vue component, Playwright test, or a hand-written script)
may not hard-code a URL path or a domain constant (elevator id, floor
count, travel timing). It follows links and reads representations. New
behaviour is always a new slice, never a new flag or an `if`/`switch`
case in an existing one.

## No versioning

New capability is a new `rel`; existing clients ignore what they don't
recognise. Hypermedia additionally allows introducing a *required* field
with a server-supplied default (a form field carrying `value`), which a
pure JSON API cannot do without breaking every unmodified client that
constructs its own request body.

## elevator-ui: front-end only, no BFF

`elevator-api` serves HTML directly (a hand-built `HtmlRenderer`, not a
templating engine) alongside the JSON/JSON-LD formats, from one origin —
fronted by a Caddy reverse proxy in `docker-compose` (added in slice 0),
so the browser never crosses origins and the technician cookie needs no
CORS/`SameSite` negotiation.

`elevator-ui`'s only remaining job is the page shell (nav chrome, CSS)
and the Playwright suite, plus one purely decorative exception: a CSS
transition standing in for a car/shaft animation, positioned and timed
entirely from the DOM `elevator-api` already rendered (the status
fields' own `currentFloor`/`travelSecondsPerFloor`, and the floor count
already present in any rendered `select[name=floor]`) rather than a
constant of its own — see the "Timing" section above and `docs/plan.html`
§12's own account of exposing that timing on the representation.
Datastar handles every server-driven DOM update over SSE, including the
affordances/forms themselves; Vue never owns the Datastar-morphed
subtree, and the shaft's own reading of it is one-way (DOM → CSS custom
property), never state of its own to keep in sync. `server/api/`,
`app/stores/elevator.ts`, every Vue component that rendered elevator
state, every typed API model, and every hard-coded domain constant are
deleted, not migrated. Discovery is a self-triggering chain of three
`GET`s (entry point → elevators collection → the one elevator → its SSE
stream), not a single request, because the entry point cannot itself
know which elevator resource to auto-fetch next — each hop's response
carries the next hop's link, and each renders its own auto-fetching
`data-init` div rather than the client constructing the next URL.

## Repository and file structure

Monorepo, three applications:

```
/elevator-api      Java 21 + Spring Boot 4 (Gradle, Kotlin DSL)
/elevator-auth     Spring Authorization Server; issues the technician's
                   scoped tokens and nothing else
/elevator-ui       Nuxt.js 4 front-end shell + Datastar, TypeScript
/docs              architecture.md and plan.html
```

Within `elevator-api`, organize **by feature, one directory per domain
behaviour** (`callelevator/`, `selectfloor/`, `opendoors/`, ...), each
holding its command, handler, endpoint, affordance descriptor, and
tests together. The `Elevator` aggregate, its value objects, the domain
event hierarchy, the four renderers, and persistence adapters live
outside any single slice, in a shared kernel.

Use Flyway for schema migrations against the in-memory H2 database, with
separate tables for the write model (mapped from the aggregate at the
persistence boundary only) and each query slice's read model — JPA
entities are confined to infrastructure and never leak into a
representation.

## Roadmap (slices, in build order)

One commit per slice (elevator-api first, then elevator-ui), naming the
smell removed, followed by lint and tests for both projects, then a
pause for review.

0. **Hypermedia kernel** (largest slice, sets up removal of everything
   that follows, no behaviour moves yet): domain skeleton (`Elevator`
   aggregate, value objects, sealed `ElevatorState`) with no Spring/JPA/
   Lombok; domain event infrastructure; the Representation/Affordance/
   Field model and `AffordanceCatalog`; all four renderers plus content
   negotiation; entry point `GET /`; rel vocabulary documentation pages;
   `UriResolver` (`ElevatorId` ↔ resource identifier, readable and
   opaque styles, both tested in CI); `Problem` as a fifth renderer for
   RFC 9457 `problem+json`, with `operations` as an extension member and
   `Link` headers for everyone else; the Caddy reverse proxy (one
   origin, allowlisted paths, service ports unpublished); the
   technician cookie narrowed to `Path=/api` as a prerequisite for the
   shared origin.
1. **Status + SSE**: read model + projection, `GET /elevators/{id}`,
   `text/event-stream`.
2. **Call elevator**: `CallElevator` command + handler,
   `ElevatorCalled` event, `RequestQueue`, first affordance rendered as
   a form.
3. **Select floor**: `SelectFloor` command, SCAN/LOOK fully on
   `RequestQueue`, scheduled `FloorReached` events.
4. **Doors**: `OpenDoors`, `CloseDoors`, `ObstructDoors`,
   `ClearObstruction`; a `Doors` value object with its own state;
   auto-close as a scheduled event.
5. **Overload**: a `Load` value object owning `isOverloaded()`;
   `ReportLoad` as sensor telemetry; `select-floor` simply absent when
   overloaded.
6. **Maintenance & authorization**: `EnterMaintenance`/
   `ExitMaintenance` split; `insert-key` becomes an affordance whose
   response is a 401 challenge carrying RFC 9728 `resource_metadata`; a
   `Principal` value object built once at the boundary (see "Key-switch
   and authorization" above).
7. **Emergency recall**: `TriggerEmergencyRecall` pre-empting every
   state, automatic settle into `outOfService`.
8. **Delete the evidence** (removes only): all of `server/api/`,
   `app/stores/elevator.ts`, every typed API model, every hard-coded
   path/id/constant in `elevator-ui`. The Playwright suite must pass
   unchanged against a client that knows exactly one URL.
9. **Delete the CRUD scaffolding** (removes only, elevator-api's own
   side of slice 8): the original `controller`/`model`/`service`/
   `repository` packages, kept piecemeal only as long as some
   not-yet-migrated behaviour still read through `ElevatorService`
   (the God Object this whole refactor exists to dissolve). Slices 1–7
   had already moved every behaviour that mattered onto the new
   aggregate; what was left behind was dead code its own comments no
   longer justified, plus `/health`, replaced with an equivalent in
   the shared kernel so docker-compose's healthcheck keeps working.
   The three now-orphaned CRUD tables (`elevators`, `calls`,
   `car_calls`) are dropped in a follow-up Flyway migration, not left
   behind either.
## Devops

Containerized with Docker Compose: `elevator-api`, `elevator-ui`,
`elevator-auth`, the Caddy reverse proxy, and (if not embedded) the
database. GitHub Actions runs tests and lint on each commit, one
workflow/job per application so failures are attributable to a specific
one.

## Definition of done

This architecture can be considered implemented when all slices above
have landed, `elevator-ui` no longer contains a BFF or any hard-coded
URL/domain constant, the CI suite passes against both readable and
randomised opaque URIs, and all four representations (plus
`problem+json`) expose the same affordance set for the same aggregate
state and caller authority.

`docs/refactor-metrics.md` (regenerated by `npm run measure`)
quantifies the before/after claims made throughout this document and
`plan.html` against the actual codebase — lines of code, cyclomatic
complexity, duplication, framework-import coupling, and an estimate
of what point-to-point API versioning would have cost this same
codebase. Re-run it whenever `main` moves to refresh the numbers.

[1]: https://github.com/asbjornu/whats-in-a-model
[2]: plan.html
