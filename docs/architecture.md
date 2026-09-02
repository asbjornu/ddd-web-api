# Spring Boot Application with code smells

Write a web application with code smells building on the ideas presented in
the talk [What's In A Model][1]. The goal of the application is to
demonstrate in particular how providing a REST-ish CRUD API that mismatches
the underlying domain model can lead to code being repeated between the
front-end/BFF layer and the API layer, and how this can lead to code smells
in the application.

In the future, this `architecture.md` file will be rewritten to describe a
more optimal architecture that the application should then be slowly
refactored towards. The goal is to demonstrate how to identify the
real-world problems that arise from the mismatch and code smells first
introduced, and how the proposed RESTful, hypermedia-driven, Domain-driven
designed architecture can help address and alleviate them.

## Code smells

Make sure to include the following code smells:

- **God Object**: A class that knows too much or does too much.
- **Feature Envy**: A method that seems more interested in a class other
  than the one it actually is in.
- **Data Clumps**: A group of variables that are always passed around
  together.
- **Primitive Obsession**: Using primitive data types to represent domain
  ideas instead of creating small objects for them.
- **Long Method**: A method that is too long and tries to do too much.
- **Switch Statements**: Using switch statements instead of polymorphism.
- **Speculative Generality**: Code that is more general than it needs to
  be, often because the developer anticipates future requirements that may
  never come.
- **Temporary Field**: A field that is only set in certain circumstances,
  leading to confusion about its purpose.
- **Refused Bequest**: A subclass that inherits methods and properties from
  a parent class but does not use them, leading to confusion about the
  relationship between the classes.
- **Model reuse**: Reusing the same model class for several different
  purposes such as ORM, DTO, API representation, validation and domain
  business logic.
- **Inappropriate Intimacy**: Two classes that are too closely related and
  know too much about each other, leading to tight coupling and difficulty
  in maintaining the code.
- **Inconsistent Naming**: Using inconsistent naming conventions for
  classes, methods, and variables, leading to confusion and difficulty in
  understanding the code.
- **Inappropriate Use of Static**: Using static methods or variables in
  appropriately, leading to tight coupling and difficulty in testing and
  maintaining the code.
- **Inappropriate Use of Inheritance**: Using inheritance inappropriately,
  leading to confusion about the relationship between classes and
  difficulty in maintaining the code.

## Architecture

The application consists of three applications: `elevator-api` (the
Java/Spring Boot domain service), `elevator-ui` (a single Nuxt.js
application that serves both the front-end and the backend-for-frontend
layer -- see "elevator-ui (front-end + BFF)" below for why these two
traditionally separate layers live in one app here), and `elevator-auth`
(a Spring Authorization Server that issues the technician's scoped
tokens and nothing else).

### Domain

The business domain is a **single elevator (lift) control system**. It's
deliberately small and physical — everyone has an intuitive mental model
of how an elevator behaves — but rich enough in state, invariants, and
scheduling logic to demonstrate the code smells above and to have real
workflows that don't map cleanly onto CRUD. It's a deliberate step up in
complexity from a single-appliance state machine (like a toaster), while
staying away from multi-actor authorization concerns.

There are two personas, both interacting through the same UI: the
**Rider**, who wants to get from A to B (calls the elevator, selects a
floor, opens/closes doors), and the **Technician**, who wants to service or
safeguard the elevator (enters/exits maintenance mode, triggers emergency
recall) via a physical key-switch. The Technician isn't a separate
authenticated role with its own dashboard — it's the same rider console,
with a handful of extra actions gated behind a simple key/token rather than
a login, because those actions represent physical key-switch access rather
than an account.

#### Building and elevators

The system serves a single building (there is no `Building` entity — the
service is deployed per building, so "which building" is a deployment
concern, not a domain concept). The building has a **configurable number of
floors**, seeded to 9 for the initial implementation.

The building has one or more **elevators**, each independently an aggregate
root with its own state, request queue, and doors. The API is shaped around
a collection of elevators (`/elevators/{id}`) from the start, but for the
initial implementation, **exactly one elevator is seeded and used** —
dispatch/assignment logic for deciding which elevator answers a given
landing call when there's more than one is out of scope for now, deferred
to a later iteration.

Floors are numbered 1-9 (ground floor = 1, also the recall floor). IDs
(elevator, calls, car calls) are simple sequential numbers, not UUIDs —
readable in URLs, logs, and tests.

#### Elevator state

Each elevator (aggregate root) is always in exactly one of these states:

- `idle`: stationary at a floor, doors closed, no pending requests.
- `doorsOpen`: stationary at a floor, doors open (boarding/alighting).
- `doorsClosing`: doors in the process of closing; obstruction re-opens.
- `movingUp` / `movingDown`: travelling between floors to serve a request.
- `outOfService`: maintenance mode; ignores calls and floor selections.
  Reached either directly ("enter maintenance") or automatically after an
  `emergencyRecall` completes; left only via "exit maintenance".
- `emergencyRecall`: pre-empts everything else, travels directly to the
  recall floor (typically the ground floor), opens doors, and then
  automatically transitions to `outOfService` until "exit maintenance" is
  used to resume normal operation.

#### Core workflows (not just data operations)

- **Call elevator**: a rider at a floor requests the car, specifying a
  direction (up/down) if not on the top/bottom floor. This adds a *landing
  call* to the elevator's request queue; it does not directly set a status
  field.
- **Select floor**: a rider inside the car selects a destination floor,
  adding a *car call*. Rejected if the car is overloaded or in
  `outOfService`/`emergencyRecall`.
- **Serve requests / travel**: the elevator services its pending calls in
  direction-committed order (a simplified SCAN/LOOK algorithm): once moving
  in a direction, it keeps serving calls in that direction before
  reversing, rather than serving calls in arrival order. This is where
  "Feature Envy" and "Long Method" naturally live if request-queue logic
  leaks into the wrong class.
- **Open/close doors**: explicit rider actions, but also automatic: doors
  auto-open on arrival at a requested floor and auto-close after a timeout
  — auto-close is interrupted and reversed by the obstruction sensor. This
  is the doors' own little state machine nested inside the elevator's.
  There's no real obstruction sensor, so obstruction is triggered manually
  via a "simulate obstruction" button in the rider UI, usable while doors
  are closing.
- **Detect overload**: if weight capacity (800kg) is exceeded while
  boarding, the elevator refuses to move, re-opens/holds the doors, and
  clears any car call that would have been added by the over-capacity
  rider. Since there's no real weight sensor, weight is simulated: the
  rider UI exposes a manual weight slider/input (range 0-1000kg)
  representing current car load, checked against the elevator's 800kg
  capacity on floor selection.
- **Enter maintenance (Technician, key-switch)**: transitions the elevator
  to `outOfService` from any non-`emergencyRecall` state, cancelling all
  pending calls. Not a generic status update — it's a distinct action with
  side effects (queue is cleared, calls are rejected while active).
- **Exit maintenance (Technician)**: transitions `outOfService` back to
  `idle`. This is the single action that clears `outOfService`, whether the
  elevator got there via "enter maintenance" or automatically via a
  completed emergency recall — there is no separate "clear emergency
  recall" action, since emergency recall always settles into `outOfService`
  on arrival (see below).
- **Trigger emergency recall (Technician, key-switch)**: pre-empts *any*
  state (including mid-travel and `outOfService`), clears the request
  queue, and forces travel to the recall floor. This is the sharpest
  contrast to CRUD: a single action whose effect depends entirely on the
  state it pre-empts, and which cannot be expressed as "set status to X".
  On arrival, the elevator opens its doors and then automatically
  transitions to `outOfService`, requiring "exit maintenance" to resume
  normal operation.

These workflows are the intentional contrast to CRUD: e.g. "call elevator"
and "trigger emergency recall" are behaviors with pre/post-conditions and
side effects — some of which pre-empt or clear other state — not `PATCH
/elevator` with a status field.

#### Minimal domain model (elevator-api / domain layer)

- `Elevator` (aggregate root): id, currentFloor, state (see above),
  direction (up/down/none), doorState (open/closing/closed), weight
  capacity, pending request queue.
- `Floor`: level number, whether it's the recall/ground floor. The
  building's floor count is configurable (seeded to 9 floors initially).
- `Call` (landing call): id, floor, direction (up/down), createdAt.
- `CarCall`: id, destinationFloor, createdAt.

There is no `Building` entity: the service is deployed per building (see
"Building and elevators" above), so there is exactly one implicit building
per deployment, with one or more elevators.

#### Timing

Use real wall-clock durations, kept short for a live demo: roughly 2
seconds of travel per floor, and a 4 second door-open timeout before
auto-close (interruptible by the obstruction sensor). These defaults should
be configurable (e.g. via Spring `application.yml` properties) rather than
hard-coded magic numbers, but don't need a UI to change them.

State is computed on read from elapsed wall-clock time rather than advanced
by a background scheduler: each state transition (e.g. "started moving up
from floor 3 at T") is recorded with a timestamp, and the elevator's
current floor/state/door state at any given moment is derived from that
timestamp plus the configured per-floor/door durations whenever the state
is queried or acted upon. No ticking background job is needed; this
derivation logic is itself a deliberate candidate for the Long
Method/Feature Envy smells if it ends up living outside the `Elevator`
domain class.

This is a starting point, not a final schema — flesh it out as needed
while implementing, but keep the aggregate boundaries above so the
domain/API mismatch (see below) has something real to mismatch against.

### elevator-ui (front-end + BFF)

`elevator-ui` is a single Nuxt.js (v4) application that plays both the
front-end and backend-for-frontend roles: its pages (Vue 3, TypeScript,
Composition API, Pinia for state) are the SPA/SSR front-end, and its Nitro
server routes (under `server/api/`) are the BFF, proxying and reshaping
requests to `elevator-api`. These are kept as one Nuxt app rather than two
separate projects because that's how Nuxt is normally used -- splitting
them into separate front-end and BFF projects would be artificial for this
stack. Use Vitest for unit tests and Playwright for end-to-end tests.
Create an ESLint + Prettier configuration that suits the application and
enforce it with GitHub Actions. `elevator-ui` should include unit tests and
end-to-end tests that demonstrate the code smells in action.

The Nitro server routes should provide a REST-ish CRUD interface that
almost, but not quite, matches that of the underlying `elevator-api`. The
mismatch should be intentional and should be designed to demonstrate the
code smells in action -- e.g. the server routes may collapse "select
floor", "call elevator", and "trigger emergency recall" into the same
generic `PUT /api/elevators/{id}` they expose to the front-end pages,
forwarding to whichever mismatched `elevator-api` endpoint seems closest,
and re-deriving business rules (such as which state transitions are legal,
or whether a call pre-empts the queue) that already exist, differently, in
`elevator-api`.

The front-end pages should provide a single rider view (for the one seeded
elevator, even though the underlying API is shaped for several): a call
panel (per floor, showing up/down call buttons where applicable), an in-car
panel (floor selection buttons, a weight slider simulating car load, door
open/close, a "simulate obstruction" button usable while doors are closing,
key-switch actions for maintenance/emergency recall gated behind a mock
"insert key" toggle), and a status display (current floor, direction, door
state) — plus a public, unauthenticated status page that just shows
current floor, direction, and door state, so anyone can check whether the
elevator is working without needing the key. The front-end should poll the
status endpoint (e.g. every 1-2 seconds) rather than use a push mechanism.

### elevator-api

`elevator-api` should be written in modern Java (21+) and Spring Boot 4,
built with Gradle (Kotlin DSL). It should provide a REST-ish API with CRUD
operations that mismatch the underlying domain model, and it should include
unit tests (JUnit 5, Mockito, AssertJ) that demonstrate the code smells in
action. The application should be structured in a way that makes it easy to
identify the code smells.

Example of the intended mismatch: the domain workflow "call elevator" (a
request that gets queued and scheduled relative to other pending calls, not
a direct state assignment) should be exposed as a generic `PUT
/elevators/{id}` that accepts a full elevator representation including a
`state` field the client can set directly to `"movingUp"`. Similarly,
"trigger emergency recall" (which pre-empts and clears the pending queue,
and behaves differently depending on the state it interrupts) should be
exposed as `PATCH /elevators/{id}` with a `state` field, losing the
distinction between a routine floor selection and a safety override — both
go through the same endpoint and the same generic `ElevatorDto`, requiring
ad hoc `if`/`switch` statements in the controller/service to figure out
"what kind of update is this really".

Suggested (intentionally CRUD-ish, not final) endpoints, shaped for
multiple elevators even though only one is seeded for v1:

- `GET /elevators`, `GET /elevators/{id}`, `PUT /elevators/{id}` (generic
  state update, the mismatch)
- `GET/POST /elevators/{id}/calls` (landing calls from floors)
- `GET/POST /elevators/{id}/car-calls` (destination floor selections)
- `POST /elevators/{id}/doors` (open/close, also CRUD-ish rather than a
  door-specific action)
- `GET /elevators/{id}/status` (public, unauthenticated read model)

### Database

Use Flyway for schema migrations against the in-memory H2 database. Model
tables directly after the JPA entities (one entity per table, no separate
read models) so that the same classes double as ORM entities, DTOs, and
sometimes API representations — this is the intentional "Model reuse" code
smell, not an oversight.

### Authentication and authorization

No login is required for the Rider persona's normal use: calling the
elevator, selecting floors and operating the doors. A rider has no
account, and a lift has no login.

The Technician's key-switch actions -- enter/exit maintenance and
trigger emergency recall -- require an OAuth 2.0 access token carrying
the right scope. `elevator-auth` is a Spring Authorization Server that
issues those tokens; `elevator-api` is a resource server that validates
them by signature against the keys that issuer publishes. Neither the
API nor the BFF holds a credential of its own.

There are two scopes, and they are not interchangeable:

- `elevator:maintenance` permits entering and leaving maintenance.
- `elevator:recall` permits triggering an emergency recall.

The split is domain-driven rather than administrative. In a real
installation fire recall is initiated by the building's alarm system or
by the fire brigade's own key, while the maintenance key belongs to the
lift service company, and a holder of the former must never be able to
strand the lift out of service. Only one client is registered today; a
fire alarm panel holding `elevator:recall` alone is the intended second
holder and is not built.

A technician types the key-switch credential into the car panel, and
`elevator-ui`'s BFF exchanges it at the token endpoint for a scoped
token, which it keeps in an `HttpOnly` cookie and forwards to
`elevator-api` as a `Bearer` token. The credential never reaches browser
JavaScript, and the browser never sees the token either.

This deliberately reverses an earlier decision. Authentication used to be
a hard-coded shared secret compared with `equals()` inside
`MaintenanceController`, on the grounds that anything more would distract
from the domain modelling. It distracted anyway, in a worse way: a
single string could not express that recall and maintenance are separate
capabilities, and "who may do what" was not representable at all.
Standard, boring OAuth turns out to cost less attention than the bespoke
alternative, and it makes the authorisation rule something the system can
state rather than something the reader has to infer.

Two simplifications are worth naming, because both are deliberate and
neither is defensible in production:

- The technician's typed credential *is* the OAuth client secret. Client
  secrets are not meant to be typed by humans. It replaces the previous
  shared string without inventing a user account for something that
  represents physical key possession, and the plan replaces it properly.
- The BFF is told where the authorization server lives. A client that
  discovered the issuer from the resource server's own
  `WWW-Authenticate` challenge would need no such configuration, which
  is the subject of the refactoring rather than an oversight in it.

Two properties of this arrangement are worth stating explicitly, because
they follow from the layering rather than from anyone forgetting
something.

**The BFF is not a security boundary.** `docker-compose` publishes
`elevator-api` on port 8080, so the service is independently reachable
and every check in `elevator-ui`'s server routes can be bypassed by
addressing it directly. Nothing is exposed by this: the privileged
endpoints require a scoped token that the BFF cannot mint and a direct
caller does not have. But it does mean the BFF's own check is a UX
affordance rather than a control, and that the enforcement which counts
is the scope requirement declared in `SecurityConfig`. Any rule added to
the BFF alone would be silently unenforced.

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

### Repository and file structure

Use a monorepo for the application, with separate directories for
`elevator-api`, `elevator-ui`, and `elevator-auth`.

The file structure should be based around types (models, controllers,
services, repositories) rather than features (e.g. "users", "orders",
etc.).

Once the initial architecture is in place, create an `AGENTS.md` file that
describes the application architecture to AI agents. Provide
project-specific instructions: coding conventions, folder structure, how to
run tests, how to run the dev server, things not to touch. This is the
highest-leverage file for consistency across sessions.

Note the tension between `AGENTS.md`'s usual purpose and this project's
goal: `AGENTS.md` should not tell agents to "fix" or avoid the code smells
listed above — they are intentional and part of the deliverable. Instead,
it should point out where they live and instruct agents to preserve them
unless a change explicitly says otherwise (e.g. during the later,
not-yet-written refactoring phase).

## Devops

The application should be containerized using Docker Compose, with separate
containers for `elevator-api`, `elevator-ui`, `elevator-auth`, and (if not
embedded) the database. The application does not need to be deployed to a
real cloud provider for this talk — running locally via Docker Compose is
sufficient, but the CI/CD pipeline should still build and test all three
applications, and produce container images as build artifacts.

GitHub Actions should be used to run tests and enforce linting rules on
each commit, with one workflow/job per application (`elevator-api`,
`elevator-ui`, `elevator-auth`) so that failures are attributable to a
specific application.

## Incremental development

The application should be developed incrementally, with each layer being
developed with accompanying tests before moving on to the next layer.

Features should be added to `elevator-api` first, followed by
`elevator-ui`. Each layer should be developed in a way that allows for the
inclusion of the code smells listed above.

Suggested build order (one thin vertical slice at a time, through both
applications before moving to the next slice). All slices operate against a
single seeded elevator (id fixed/known ahead of time), in a 9-floor
building, even though the API is shaped to address elevators by id:

1. Rider views elevator status (current floor, direction, door state);
   public read-only status endpoint, polled by the front-end.
2. Rider calls the elevator from a floor (landing call queued) and it
   travels to serve it, using real (short) per-floor travel timing.
3. Rider selects a destination floor from inside the car (car call queued),
   with direction-committed scheduling across multiple pending calls.
4. Doors: auto-open on arrival, auto-close after timeout, obstruction
   re-opens; explicit open/close rider actions.
5. Overload detection: rider sets a simulated weight via a UI slider;
   over-capacity refuses to move, holds doors, clears the offending car
   call.
6. Key-switch: enter/exit maintenance (`outOfService`), clearing pending
   calls, gated by a scoped OAuth 2.0 access token (`elevator:maintenance`)
   issued by `elevator-auth`.
7. Key-switch: trigger emergency recall, pre-empting any state and
   travelling to the recall floor, settling into `outOfService` on arrival;
   "exit maintenance" resumes normal operation. Gated by a separate scope
   (`elevator:recall`), since the two capabilities must not be
   interchangeable.

Each slice above is a natural place to introduce one or two of the code
smells (e.g. slice 3's request-queue scheduling is the natural home for the
God Object/Feature Envy contrast described in the elevator-api section).

Commit code after each coherent change, and make sure to include a commit
message that describes the change and the code smell that was introduced.
This will allow for easy identification of the code smells in the commit
history.

After each commit, pause and wait for human confirmation that the change is
complete before moving on to the next change. While pausing, take time to
reflect on the code smell that was introduced, and consider how it could be
refactored to improve the design of the application. This will help to
reinforce the concepts presented in the talk and provide a deeper
understanding of how to identify and address code smells in real-world
applications.

As development progresses, keep readme.md up to date with setup
instructions, stack overview, and how to run locally.

## Definition of done

The application can be considered done when all features are implemented in
`elevator-api`, `elevator-ui`, and `elevator-auth`, it includes the code
smells listed above, and it is structured in a way that makes it easy to
identify them. All parts of the application should be runnable and should
include instructions for how to run.

[1]: https://github.com/asbjornu/whats-in-a-model
