// Car calls (destination floor selection): proxies the service API's
// GET /elevators/{id}/car-calls.

export default defineEventHandler(async (event) => {
  const id = getRouterParam(event, 'id')
  const config = useRuntimeConfig()

  return await $fetch(`${config.serviceApiUrl}/elevators/${id}/car-calls`)
})
