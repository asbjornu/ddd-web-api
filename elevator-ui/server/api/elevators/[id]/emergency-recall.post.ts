// Emergency recall: proxies POST /elevators/{id}/emergency-recall.
//
// Authorisation comes from the access token in the HttpOnly cookie. The
// browser never sees it, and the BFF holds no credential of its own --
// the token was obtained by exchanging what the technician typed.
//
// Unlike every other write route in this directory, there is no
// state-dependent conflict to duplicate here: ElevatorService
// .triggerEmergencyRecall (see elevator-api's own service) never
// refuses -- it is deliberately unconditional, mid-recall or in
// maintenance included -- so there is nothing this file could
// pre-check that Java's own method would ever reject anyway.

export default defineEventHandler(async (event) => {
  const id = getRouterParam(event, 'id')
  const config = useRuntimeConfig()

  const token = requireToken(event)

  return await $fetch(`${config.serviceApiUrl}/elevators/${id}/emergency-recall`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` }
  })
})
