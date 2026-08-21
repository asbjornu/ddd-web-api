// Technician key-switch actions: proxies POST /elevators/{id}/maintenance.
//
// Authorisation comes from the HttpOnly technician cookie, not from a
// client-supplied Authorization header -- the browser never holds the
// shared secret. The Bearer token is attached here, server-side.

export default defineEventHandler(async (event) => {
  const id = getRouterParam(event, 'id')
  const config = useRuntimeConfig()

  requireTechnicianKey(event, config.technicianKey)

  const body = await readBody(event)

  return await $fetch(`${config.serviceApiUrl}/elevators/${id}/maintenance`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${config.technicianKey}` },
    body
  })
})
