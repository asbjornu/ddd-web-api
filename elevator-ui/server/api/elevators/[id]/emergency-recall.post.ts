// Emergency recall: proxies POST /elevators/{id}/emergency-recall.
//
// Authorisation comes from the HttpOnly technician cookie, not from a
// client-supplied Authorization header -- the browser never holds the
// shared secret. The Bearer token is attached here, server-side.

export default defineEventHandler(async (event) => {
  const id = getRouterParam(event, 'id')
  const config = useRuntimeConfig()

  requireTechnicianKey(event, config.technicianKey)

  return await $fetch(`${config.serviceApiUrl}/elevators/${id}/emergency-recall`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${config.technicianKey}` }
  })
})
