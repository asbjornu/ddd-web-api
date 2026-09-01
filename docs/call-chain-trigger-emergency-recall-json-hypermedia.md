# Call chain: `TriggerEmergencyRecall` — JSON hypermedia (`json-hypermedia`)

A technician presses the panic button, in the `json-hypermedia`
branch. The domain layer and its authorization rule are unchanged from
`main`; what differs is *how a Bearer token ever reaches the browser at
all*, since this branch's `insert-key` endpoint answers only with an
RFC 9728 challenge, never a cookie of its own — that gap is why this is
the one operation in this series that still needs a Nuxt BFF.

## Step 0 — the hypermedia control this button came from

Submitting the technician key-switch form goes to the BFF, not
directly to `elevator-api`:

```
$ curl -sD- -X POST http://127.0.0.1:8000/api/key \
    -H "Content-Type: application/json" -d '{"secret":"dev-secret-key"}' \
    -c cookies.txt

HTTP/1.1 200 OK
Content-Type: application/json
Set-Cookie: technician_token=eyJraWQ...; Max-Age=899; Path=/api;
            HttpOnly; SameSite=Strict

{"inserted":true,"scope":"elevator:recall elevator:maintenance","expiresIn":899}
```

Behind that one response, the BFF itself made two requests of its own
(see `technicianKey.ts`, quoted in full below): a `GET
/.well-known/oauth-protected-resource` against `elevator-api` to
*discover* the token endpoint, then a `client_credentials` grant
against that endpoint, `Authorization: Basic` with the typed secret as
the password. Neither elevator-api's own cookie mechanism (`main`) nor
crud's is at play here — the token comes from `elevator-auth`
directly, and the BFF's cookie is a wrapper around it, not a
session of its own.

The cookie's `Path=/api` means it is never sent to `/elevators/1`
directly; the client instead re-reads the resource through the BFF,
which attaches the token as a real `Authorization: Bearer` header:

```
$ curl -s -b cookies.txt http://127.0.0.1:8000/api/elevators/1/status

{
  "currentFloor" : 3,
  "state" : "idle",
  ...
  "operations" : [
    { "rel" : "call-elevator", ... },
    { "rel" : "enter-maintenance", ... },
    { "rel" : "open-doors", ... },
    { "rel" : "select-floor", ... },
    {
      "rel" : "trigger-emergency-recall",
      "title" : "Trigger emergency recall",
      "method" : "POST",
      "href" : "/elevators/1",
      "fields" : [
        { "name" : "type", "type" : "hidden", "value" : "TriggerEmergencyRecall", "required" : true }
      ]
    }
  ]
}
```

`insert-key`'s own absence and `enter-maintenance`/
`trigger-emergency-recall`'s appearance are decided by the exact same
`TriggerEmergencyRecallAffordanceContributor` (and its siblings) `main`
uses — the difference is entirely in how the browser came to hold a
credential this class's `context.principal()` recognizes at all.

## UI

`app/stores/elevator.ts`

```ts
async triggerEmergencyRecall() {
  const operation = this.triggerEmergencyRecallOperation
  if (!operation) {
    this.error = 'Emergency recall is not available right now.'
    return
  }
  try {
    await $fetch(`/api/elevators/${ELEVATOR_ID}/commands`, {
      method: 'POST',
      body: commandBody(operation)
    })
    this.error = null
    await this.refreshAuthenticatedStatus()
  } catch {
    this.error = 'Unable to trigger emergency recall.'
  }
}
```

`server/api/elevators/[id]/commands.post.ts`

```ts
const token = requireToken(event)
const body = await readBody(event)
return await $fetch(`${config.serviceApiUrl}/elevators/${id}`, {
  method: 'POST',
  headers: { Authorization: `Bearer ${token}`, Accept: 'application/vnd.elevator.state+json' },
  body
})
```

## HTTP

**#1**: `POST /api/elevators/1/commands` `{"type":"TriggerEmergencyRecall"}`
(browser, no credential of its own — the cookie rides along). **#2**:
`POST http://elevator-api:8080/elevators/1`, `Authorization: Bearer
<token>`, same body.

## Java

Unchanged from `main` — same scope check, same two places:

```java
// TriggerEmergencyRecallController.java
if (!principal.hasScope("elevator:recall")) {
    return responses.problem(HttpStatus.FORBIDDEN, accept,
        ElevatorRepresentations.forbidden(
            "This operation requires the emergency recall key."));
}
handler.handle(new TriggerEmergencyRecallCommand(id));
```

```java
// TriggerEmergencyRecallAffordanceContributor.java
if (!context.principal().hasScope("elevator:recall")) return List.of();
if ("emergencyRecall".equals(context.state().orElse(""))) return List.of();
```

## Tests

`TriggerEmergencyRecallControllerTest.java` and
`TriggerEmergencyRecallAffordanceContributorTest.java` are identical
to `main`'s (confirmed by diffing the two branches directly — the
scope check and its test coverage did not change when the BFF's OAuth
dance was replaced by `main`'s own `key-switch/session` endpoint):

```java
@Test
void triggeringRecallWithoutTheScopeIsForbidden() throws Exception {
```

```java
@Test
void absentWithoutTheScope() {
```

This branch is the only one of the three whose *client* also has a
real test for this command, `elevator-ui/test/unit/elevatorStore.test.ts`:

```ts
describe('useElevatorStore triggerEmergencyRecall', () => {
  it('does nothing when no trigger-emergency-recall operation is present', async () => {
    // ...
    await store.triggerEmergencyRecall()
    expect(store.error).toBe('Emergency recall is not available right now.')
    expect(lastTechnicianCommandBody).toBeUndefined()
  })

  it("posts to the BFF's commands proxy, echoing the operation's hidden type", async () => {
    // ...
    await store.triggerEmergencyRecall()
    expect(lastTechnicianCommandBody).toEqual({ type: 'TriggerEmergencyRecall' })
  })
})
```

What it does not cover is the entire point of this file's "Client-side
result" section: `refreshAuthenticatedStatus`'s re-read is never
asserted to actually pick up `hasAnyTechnicianOperation`'s change, and
neither `technicianKey.ts`'s RFC 9728 discovery step nor its
`client_credentials` exchange has a test double anywhere in this
suite — the whole BFF-side flow this file's Step 0 section captures
live is untested code, run for the first time, for real, against a
real `elevator-auth`, only when someone does exactly that.

## Client-side result

The command's own response never reaches `store.status` here — note
`commands.post.ts` above discards it (`return await $fetch(...)` inside
a proxy route the store's own `triggerEmergencyRecall` action does not
capture; it calls `refreshAuthenticatedStatus()` instead, quoted below,
purely because the ordinary anonymous SSE stream this store also
subscribes to has no Bearer token to attach and therefore never
carries a technician's own operations:

```ts
async refreshAuthenticatedStatus() {
  try {
    const data = await $fetch<ElevatorView>(`/api/elevators/${ELEVATOR_ID}/status`)
    this.status = data
  } catch {
    // The SSE stream will catch up on its own next push.
  }
}
```

Once that resolves, `ElevatorShaft.vue`'s colour computeds (identical
to the `CallElevator` trace's) do the same work `main`'s
`shaft.client.ts` does:

```ts
const isOutOfService = computed(() => store.status?.state === 'outOfService') // knows the exact "outOfService" state name
const isEmergency = computed(() => store.status?.state === 'emergencyRecall') // knows the exact "emergencyRecall" state name
```

and `StatusDisplay.vue`'s `hasAnyTechnicianOperation` reacts to the
*disappearance* of every technician operation at once, the same shape
as `main`'s `enter-maintenance`/`exit-maintenance` disappearing
together:

```ts
const hasAnyTechnicianOperation = computed(
  () =>
    store.enterMaintenanceOperation != null ||
    store.exitMaintenanceOperation != null ||
    store.triggerEmergencyRecallOperation != null
)
```

## What this client needed to know about the state machine

The same two state names as `main`'s equivalent trace, for the same
presentation-only reason. The authorization decision is, again, never
duplicated client-side — but unlike `main`, this branch's client *does*
have to know something `main`'s does not: that an authenticated re-read
exists at all, and that ordinary SSE pushes cannot be trusted to carry
a technician's own operations. That is not state-machine knowledge, but
it is exactly the kind of gap a BFF exists to paper over, and it goes
away entirely once `main`'s same-origin, cookie-issuing
`key-switch/session` endpoint replaces this flow.
