// Technician key-switch actions: proxies POST /elevators/{id}/maintenance
// -- but validates the payload and pre-checks the same conflicts
// ElevatorService.enterMaintenance/exitMaintenance themselves enforce,
// before doing so.
//
// Authorisation comes from the access token in the HttpOnly cookie. The
// browser never sees it, and the BFF holds no credential of its own --
// the token was obtained by exchanging what the technician typed. The
// state pre-check below reads the elevator's public, unauthenticated
// status representation -- no token required for that half of this
// route -- so it runs even for a request whose own token
// `requireToken` is about to reject; elevator-api's own check is the
// one that actually matters, this is only saving a round trip for the
// common case.

export default defineEventHandler(async (event) => {
  const id = getRouterParam(event, 'id')
  const config = useRuntimeConfig()

  const token = requireToken(event)

  const body = await readBody<{ maintenance?: unknown }>(event)
  if (typeof body?.maintenance !== 'boolean') {
    throw createError({ statusCode: 400, statusMessage: 'Invalid maintenance value' })
  }

  const status = await fetchStatusForValidation(config.serviceApiUrl, id)
  if (body.maintenance) {
    if (status.state === 'OUT_OF_SERVICE') {
      throw createError({ statusCode: 409, statusMessage: 'Already in maintenance' })
    }
    if (status.state === 'EMERGENCY_RECALL') {
      throw createError({
        statusCode: 409,
        statusMessage: 'Cannot enter maintenance during emergency recall'
      })
    }
  } else if (status.state !== 'OUT_OF_SERVICE') {
    throw createError({ statusCode: 409, statusMessage: 'Not in maintenance' })
  }

  return await $fetch(`${config.serviceApiUrl}/elevators/${id}/maintenance`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body
  })
})
