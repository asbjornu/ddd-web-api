// Technician key-switch actions: proxies POST /elevators/{id}/maintenance.
// Requires X-Technician-Key header from the client.

export default defineEventHandler(async (event) => {
  const id = getRouterParam(event, 'id')
  const config = useRuntimeConfig()
  const body = await readBody(event)
  const key = getHeader(event, 'x-technician-key')

  return await $fetch(`${config.serviceApiUrl}/elevators/${id}/maintenance`, {
    method: 'POST',
    headers: key ? { 'X-Technician-Key': key } : undefined,
    body
  })
})
