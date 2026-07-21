// Simulated weight sensor: proxies the service API's POST /elevators/{id}/weight.

export default defineEventHandler(async (event) => {
  const id = getRouterParam(event, 'id')
  const config = useRuntimeConfig()
  const body = await readBody(event)

  return await $fetch(`${config.serviceApiUrl}/elevators/${id}/weight`, {
    method: 'POST',
    body
  })
})
