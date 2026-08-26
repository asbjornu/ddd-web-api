// A visible "something is happening" indicator for the bespoke
// CallPanel/StatusDisplay/CarPanel buttons -- clicking one submits a
// hidden form (see panels.client.ts/shaft.client.ts's own comments),
// so there is no native :active state on the button actually doing the
// work for the browser to show on its own. The pre-refactoring
// original disabled every control while its store's own loading flag
// was set, for the same reason: a rider clicking a button with no
// feedback at all has no way to tell a slow response from a broken
// one. Datastar's own datastar-fetch event (fired on every @get/@post
// action, started/finished/error/retrying/retries-failed -- see
// https://data-star.dev/reference/actions#events) is the same signal
// without needing to know which specific form is involved.
export default defineNuxtPlugin(() => {
  if (typeof window === 'undefined') {
    return
  }

  let inFlight = 0

  document.addEventListener('datastar-fetch', (event) => {
    const type = (event as CustomEvent).detail?.type
    if (type === 'started') {
      inFlight += 1
    } else if (type === 'finished' || type === 'error' || type === 'retries-failed') {
      inFlight = Math.max(0, inFlight - 1)
    } else {
      return
    }
    document.body.classList.toggle('busy', inFlight > 0)
  })
})
