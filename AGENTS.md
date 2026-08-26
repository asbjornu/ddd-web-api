# AGENTS.md

Instructions for AI coding agents working in this repository. See
`readme.md` for the talk abstract and `docs/architecture.md` for the full
architecture and domain description — read both before making changes.

## What this repository is

A demo application for the talk "Domain-driven web APIs", mid-refactor
from a REST-ish CRUD API with intentional code smells (God Object,
Feature Envy, Data Clumps, Primitive Obsession, Model reuse, etc.) into
the command-based, hypermedia-driven, CQRS-separated REST API described
in `docs/architecture.md`. The refactor proceeds one vertical slice at a
time (see `docs/architecture.md`'s "Roadmap"); until a slice lands, its
code still has the old CRUD shape and the smells that came with it.
**Do not gratuitously "fix" or clean up code outside the slice a task is
actually working on** — a smell in code no slice has touched yet is
still load-bearing for the talk's before/after story, and belongs to
whichever future slice's commit will remove it and name it. The old,
fully-CRUD architecture is no longer described in `docs/architecture.md`
— it is preserved in that file's git history, and in the code itself
until its slice migrates it away.

The domain is an elevator (lift) control system for a single building
(calls, car calls, doors, maintenance, emergency recall), with two
personas sharing one UI: Rider (normal operation) and Technician
(key-switch actions: maintenance, emergency recall, gated by a scoped
OAuth 2.0 token). The API is shaped to support multiple elevators
(`/elevators/{id}`), but only one elevator is seeded and used for now —
don't build out multi-elevator dispatch logic unless a task explicitly
asks for it.

Read `docs/plan.html` before working on a slice — it is the design
rationale `docs/architecture.md` only summarizes, with the full
argument, worked examples, media-type samples, and open questions.

## Repository layout (monorepo)

```
/elevator-api      Java 21 + Spring Boot 4 (Gradle, Kotlin DSL)
/elevator-auth     Spring Authorization Server; issues the technician's
                   scoped tokens and nothing else
/elevator-ui       Nuxt.js 4 front-end shell + Datastar, TypeScript
/docs              architecture.md and plan.html
```

`elevator-ui` is a front-end shell (pages, the shaft/car animation
chrome, the Playwright suite) with no backend-for-frontend — see
`docs/architecture.md`'s "elevator-ui: front-end only, no BFF" section.
Every request, including the technician's, goes straight to
`elevator-api`; there is no `server/` directory to reintroduce one in.

Within `elevator-api`, the target layout is **one directory per domain
behaviour** (`callelevator/`, `selectfloor/`, `opendoors/`, ...), each
holding its command, handler, endpoint, affordance descriptor, and tests
together — see `docs/architecture.md`'s "Vertical slices" and
"Repository and file structure" sections. Code not yet migrated by a
slice still lives in the old `controller/`/`service/`/`repository/`/
`model/` layout; leave it there until its slice lands, rather than
moving it piecemeal.

## Coding conventions

- **Markdown**: wrap prose at 75 characters per line (hard-wrapped, not
  just soft-wrapped by an editor). Preserve fenced code blocks, mermaid
  diagrams, and blockquotes as their own wrapping units — don't let
  wrapping merge blockquote `>` markers into the text. Treat em dashes
  (`—`) as double-width when checking line length, since some
  editors/fonts render them wider than one column. Write links as
  reference-style (`[text][id]` with `[id]: url` definitions collected in a
  footer at the end of the file), not inline (`[text](url)`).
- **elevator-api**: Java 21, Spring Boot 4, Gradle Kotlin DSL. Tests with
  JUnit 5, Mockito, AssertJ. Follow standard Spring naming
  (`XxxController`, `XxxService`, `XxxRepository`) in code not yet
  migrated to a vertical slice; don't be surprised by (and don't
  silently clean up) inconsistent naming elsewhere — that's one of the
  intentional smells still awaiting its slice.
- **elevator-ui**: Nuxt 4, TypeScript, Vue 3 Composition API. No
  client-side state management: Datastar drives every interactive part
  of the page directly from elevator-api's own rendered HTML, so there
  is no store to keep in sync with it. ESLint + Prettier are configured
  and enforced in CI — keep code passing lint even where it's
  intentionally smelly in other ways (naming, structure); lint failures
  are not part of the demo. Do not add rules that would flag the
  deliberate smells: lint is here to catch mistakes, not to improve the
  design.

## How to run things

- elevator-api: `./gradlew bootRun` (from `elevator-api/`), serves on
  `http://localhost:8080`
- elevator-api tests: `./gradlew test` (from `elevator-api/`)
- elevator-auth: `./gradlew bootRun` (from `elevator-auth/`), serves on
  `http://localhost:9000`; `./gradlew test` for its tests
- elevator-ui dev server: `npm run dev` (from `elevator-ui/`), serves on
  `http://localhost:3000`
- elevator-ui lint: `npm run lint` (ESLint, from `elevator-ui/`);
  `npm run lint:fix` to autofix
- elevator-ui formatting: `npm run format:check` (Prettier, from
  `elevator-ui/`); `npm run format` to rewrite
- elevator-ui e2e tests: `npm run test:e2e` (Playwright, from
  `elevator-ui/`; requires `npx playwright install chromium` once) --
  the only test suite this project has: there is no client-side logic
  of its own left to unit test
- Full stack locally: `docker compose up` (from the repo root); starts both
  `elevator-api` (`http://localhost:8080`) and `elevator-ui`
  (`http://localhost:3000`)
- Before presenting: `docker compose up` alone never rebuilds, so a
  branch switch runs whatever image was last built under that branch's
  tag (`docker-compose.yml`'s `image: ...:${IMAGE_TAG:-<branch>}`), not
  necessarily what's on disk. Pre-build and tag all three demo branches
  ahead of time so no build happens on stage:
  `for b in crud json-hypermedia main; do git checkout $b && docker
  compose build; done` (each branch's compose file defaults `IMAGE_TAG`
  to its own branch name, so this tags each image correctly without
  passing `IMAGE_TAG` explicitly). During the talk, `git checkout
  <branch> && docker compose up` then only ever starts already-built
  images.
- Markdown lint: `npm run lint:md` (from the repo root; see `.remarkrc.mjs`
  for the remark-lint config and its documented deviations from the plugin
  defaults)
- CI: four GitHub Actions workflows in `.github/workflows` -- one per
  application plus one for docs, so a red build names what broke.
  Validate changes to them with `actionlint` from the repo root

## Toolchain

This machine uses [Homebrew][1]. Everything needed is already installed —
`node`, `openjdk@21`, `openjdk`, `openjdk@8` and `gnupg`. Install anything
missing with `brew install <formula>`.

| Tool    | Formula      | Needed for                                  |
| ------- | ------------ | ------------------------------------------- |
| Node.js | `node`       | elevator-ui dev server, Playwright          |
| JDK 21  | `openjdk@21` | elevator-api — the Gradle toolchain pins 21 |
| GnuPG   | `gnupg`      | signed commits (`commit.gpgsign` is `true`) |
| actionlint | `actionlint` | validating `.github/workflows`            |

**`PATH` first.** A non-interactive shell may start with a minimal `PATH`
of `/usr/bin:/bin:/usr/sbin:/sbin`, which excludes Homebrew's
`/usr/local/bin`. `node`, `npm`, `brew` and `gpg` then look uninstalled
when they are merely unreachable. Before concluding a tool is missing:

```sh
export PATH="/usr/local/bin:$PATH"
```

Without this, `git commit` fails with `cannot run gpg: No such file or
directory` rather than anything mentioning `PATH`.

**Java 21 specifically.** `elevator-api`'s Gradle toolchain requires
exactly Java 21, so a newer JDK on `PATH` will not do. Point `JAVA_HOME`
at the Homebrew symlink, which survives patch upgrades:

```sh
export JAVA_HOME=/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
```

Prefer `/usr/local/opt/openjdk@21/...` over a `/usr/local/Cellar/...`
path: Cellar paths embed the exact version and break on every upgrade.
The *global* `~/.gradle/gradle.properties` on this machine sets
`org.gradle.java.installations.paths` to Cellar paths that no longer
exist. Gradle only warns about those, so exporting `JAVA_HOME` is
sufficient — that file is outside the repository, so leave it alone.

Playwright's browsers are already downloaded; `npx playwright install
chromium` is only needed on a fresh machine.

## Things not to touch / be careful with

- Do not refactor away a code smell in code outside the slice a task is
  actually working on. If you notice one while working on something
  else, leave it — or note it in the commit message/PR description
  instead of fixing it.
- Removing a code smell *inside* the slice a task is working on is
  expected, even required — the commit message should name the smell
  removed (see "Roadmap" in `docs/architecture.md`).
- The client (Vue component, Playwright test, hand-written script) may
  not hard-code a URL path or a domain constant (elevator id, floor
  count, travel timing). It follows links and reads representations.
- New behaviour is always a new slice (own directory: command, handler,
  endpoint, affordance, tests), never a new flag or `if`/`switch` case in
  an existing one.
- Adding a media type or a renderer must not touch the domain
  (`Elevator` aggregate, value objects, event hierarchy) — those stay
  shared across all slices, not sliced themselves.
- Never build ahead of `docs/architecture.md` — if a decision needed for
  the current task isn't written there or in `docs/plan.html`, stop and
  ask rather than inventing target architecture.

## Process

- Commit message headers must be at most 50 characters. If the header
  needs to be cut for brevity, repeat the full header (wrapped at 72
  characters) in the commit body.
- After making a coherent set of changes, commit them yourself with a
  suggested commit message describing the change **and** naming the code
  smell removed or introduced, whichever applies (e.g. "Add emergency
  recall slice (God Object: ElevatorService deleted)"). Immediately
  after each commit, run lint and test commands for both projects; fix
  any failures and amend them into the commit before considering it
  complete. Then stop and pause work until further notice — don't start
  the next change until told to continue.
- Build features vertically, one slice through both applications, in the
  order given in `docs/architecture.md`'s "Roadmap (slices, in build
  order)", elevator-api first, then elevator-ui.
- Pause after each suggested commit for human review before continuing —
  don't chain multiple commits' worth of work without a checkpoint.
- Keep `readme.md` up to date with setup instructions and stack overview as
  things change. Keep this file (`AGENTS.md`) up to date with the "How to
  run things" section as soon as real scripts/commands exist.

[1]: https://brew.sh
