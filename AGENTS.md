# AGENTS.md

Instructions for AI coding agents working in this repository. See
`readme.md` for the talk abstract and `docs/architecture.md` for the full
architecture and domain description — read both before making changes.

## What this repository is

A demo application for the talk "Domain-driven web APIs". It intentionally
contains code smells (God Object, Feature Envy, Data Clumps, Primitive
Obsession, Model reuse, etc. — see `docs/architecture.md` for the full
list) that arise from a REST-ish CRUD API mismatching the underlying domain
model. **The code smells are the deliverable, not a bug.** Do not "fix",
refactor away, or avoid introducing them unless a task explicitly asks for
a refactoring pass.

The domain is an elevator (lift) control system for a single building
(calls, car calls, doors, maintenance, emergency recall), with two personas
sharing one UI: Rider (normal operation) and Technician (key-switch
actions: maintenance, emergency recall, gated by a hard-coded shared
secret, not a login). The API is shaped to support multiple elevators
(`/elevators/{id}`), but only one elevator is seeded and used for now —
don't build out multi-elevator dispatch logic unless a task explicitly asks
for it.

## Repository layout (monorepo)

```
/service-api      Java 21 + Spring Boot 3 (Gradle, Kotlin DSL)
/bff               Nuxt.js 3 (Nitro server routes), TypeScript
/frontend          Vue 3 + TypeScript SPA (Vite, Pinia, Vue Router)
/docs              architecture.md and other design docs
```

> Note: this structure doesn't exist yet at the time of writing. Create it
> exactly as above when scaffolding the project; keep these three top-level
> app directories separate from `docs/`.

Within `service-api`, organize by **type**, not by feature: `controller/`,
`service/`, `repository/`, `model/` (entities doubling as DTOs — this
reuse is intentional). Do not reorganize into feature/domain-based packages
(e.g. `calls/`, `doors/`) — that would remove one of the demonstrated
smells (Model reuse across layers is easier to keep messy in a type-based
structure).

## Coding conventions

- **Markdown**: wrap prose at 75 characters per line (hard-wrapped, not
  just soft-wrapped by an editor). Preserve fenced code blocks, mermaid
  diagrams, and blockquotes as their own wrapping units — don't let
  wrapping merge blockquote `>` markers into the text. Treat em dashes
  (`—`) as double-width when checking line length, since some
  editors/fonts render them wider than one column. Write links as
  reference-style (`[text][id]` with `[id]: url` definitions collected in a
  footer at the end of the file), not inline (`[text](url)`).
- **service-api**: Java 21, Spring Boot 3, Gradle Kotlin DSL. Tests with
  JUnit 5, Mockito, AssertJ. Follow standard Spring naming
  (`XxxController`, `XxxService`, `XxxRepository`), but don't be surprised
  by (and don't silently clean up) inconsistent naming elsewhere — that's
  one of the intentional smells.
- **bff**: Nuxt 3, TypeScript, server routes under `server/api/`.
- **frontend**: Vue 3 Composition API, `<script setup>`, Pinia for state,
  TypeScript throughout. ESLint + Prettier config is enforced in CI — keep
  code passing lint even where it's intentionally smelly in other ways
  (naming, structure); lint failures are not part of the demo.

## How to run things

(Fill in once each layer is scaffolded — keep this section and the
readme's setup instructions in sync.)

- Service API: `./gradlew bootRun` (from `service-api/`)
- Service API tests: `./gradlew test`
- BFF dev server: `npm run dev` (from `bff/`)
- Frontend dev server: `npm run dev` (from `frontend/`)
- Frontend unit tests: `npm run test:unit` (Vitest)
- Frontend e2e tests: `npm run test:e2e` (Playwright)
- Full stack locally: `docker compose up`
- Markdown lint: `npm run lint:md` (from the repo root; see `.remarkrc.mjs`
  for the remark-lint config and its documented deviations from the plugin
  defaults)

## Things not to touch / be careful with

- Do not refactor away the code smells listed in `docs/architecture.md`. If
  you notice one while working on something else, leave it — or note it in
  the commit message/PR description instead of fixing it.
- Do not unify the service API's and BFF's REST representations — the
  mismatch between them is intentional and central to the demo.
- Do not introduce a shared model/types package between `service-api` and
  `bff`/`frontend` purely to remove duplication — the duplication is the
  point.
- `docs/architecture.md` describes the *current, smelly* architecture. A
  future revision of that file will describe the target refactored
  architecture; don't build ahead of it.

## Process

- After making a coherent set of changes, commit them yourself with a
  suggested commit message describing the change **and** naming the code
  smell being introduced (e.g. "Add emergency recall endpoint (God Object:
  ElevatorService)"), then stop and pause work until further notice —
  don't start the next change until told to continue.
- Build features vertically, one slice through all three layers, in the
  order given in `docs/architecture.md` under "Incremental development",
  service API first, then BFF, then front-end.
- Pause after each suggested commit for human review before continuing —
  don't chain multiple commits' worth of work without a checkpoint.
- Keep `readme.md` up to date with setup instructions and stack overview as
  things change. Keep this file (`AGENTS.md`) up to date with the "How to
  run things" section as soon as real scripts/commands exist.
