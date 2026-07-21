// Technician key-switch actions: proxies POST /elevators/{id}/maintenance.
// Requires Authorization: Bearer token from the client.

export default defineEventHandler(async (event) => {
  const id = getRouterParam(event, 'id')
  const config = useRuntimeConfig()
  const body = await readBody(event)
  const auth = getHeader(event, 'authorization')

  return await $fetch(`${config.serviceApiUrl}/elevators/${id}/maintenance`, {
    method: 'POST',
    headers: auth ? { Authorization: auth } : undefined,
    body
  })
})
