// Emergency recall: proxies POST /elevators/{id}/emergency-recall.
// Requires Authorization: Bearer token from the client.

export default defineEventHandler(async (event) => {
  const id = getRouterParam(event, 'id')
  const config = useRuntimeConfig()
  const auth = getHeader(event, 'authorization')

  return await $fetch(`${config.serviceApiUrl}/elevators/${id}/emergency-recall`, {
    method: 'POST',
    headers: auth ? { Authorization: auth } : undefined
  })
})
