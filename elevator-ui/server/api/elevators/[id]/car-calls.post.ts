// Car calls (destination floor selection): proxies the service API's
// POST /elevators/{id}/car-calls.

export default defineEventHandler(async (event) => {
  const id = getRouterParam(event, 'id')
  const config = useRuntimeConfig()
  const body = await readBody(event)

  return await $fetch(`${config.serviceApiUrl}/elevators/${id}/car-calls`, {
    method: 'POST',
    body
  })
})
