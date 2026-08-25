// An authenticated re-read of the elevator's own representation --
// proxies GET /elevators/{id} with the technician's Bearer token
// attached, so the operations it carries reflect what this caller may
// do, not what an anonymous rider may. Used only right after inserting
// or withdrawing the key: the ordinary way this store learns the
// elevator's state is the unauthenticated SSE stream every rider also
// uses (see docs/architecture.md's "elevator-ui: front-end only, no
// BFF" section), which has no Bearer token to attach and therefore
// never carries a technician's operations -- a known gap this route
// papers over for the moment after a key-switch action, not a general
// fix. Tolerates a missing token (withdrawing needs the same
// anonymous-shaped refresh insertion's counterpart does).

export default defineEventHandler(async (event) => {
  const id = getRouterParam(event, 'id')
  const config = useRuntimeConfig()

  const token = readToken(event)

  return await $fetch(`${config.serviceApiUrl}/elevators/${id}`, {
    method: 'GET',
    headers: {
      Accept: 'application/vnd.elevator.state+json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    }
  })
})
