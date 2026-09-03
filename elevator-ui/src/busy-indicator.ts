// A visible "something is happening" indicator for the bespoke
// CallPanel/StatusDisplay/CarPanel buttons -- clicking one submits a
// hidden form (see panels.ts/shaft.ts's own comments), so there is no
// native :active state on the button actually doing the work for the
// browser to show on its own. The pre-refactoring original disabled
// every control while its store's own loading flag was set, for the
// same reason: a rider clicking a button with no feedback at all has
// no way to tell a slow response from a broken one. Datastar's own
// datastar-fetch event (fired on every @get/@post action,
// started/finished/error/retrying/retries-failed -- see
// https://data-star.dev/reference/actions#events) is the same signal
// without needing to know which specific form is involved.
//
// One @get is never meant to finish, though: the SSE subscription
// itself (data-init="@get('.../events')", rendered at the end of
// every fragment -- see ElevatorRepresentations' own autoInit call).
// It fires "started" once, when the connection opens, and by design
// never fires "finished" while it stays open to receive future
// pushes -- counting it the same as a one-shot command/GET would
// leave inFlight stuck above zero forever, the moment the page loads,
// disabling every control permanently rather than only while a real
// request is in flight.

let inFlight = 0

function isPersistentSubscription(el: Element | null | undefined): boolean {
  return el?.getAttribute?.('data-init')?.includes("/events')") ?? false
}

document.addEventListener('datastar-fetch', (event) => {
  const detail = (event as CustomEvent).detail
  if (isPersistentSubscription(detail?.el)) {
    return
  }
  const type = detail?.type
  if (type === 'started') {
    inFlight += 1
  } else if (type === 'finished' || type === 'error' || type === 'retries-failed') {
    inFlight = Math.max(0, inFlight - 1)
  } else {
    return
  }
  document.body.classList.toggle('busy', inFlight > 0)
})

// Forces module scope (no import/export otherwise), so this file's
// top-level declarations don't collide with the other two -- each is
// loaded as its own <script type="module">, genuinely isolated at
// runtime already; this only affects how tsc analyses them together.
export {}
