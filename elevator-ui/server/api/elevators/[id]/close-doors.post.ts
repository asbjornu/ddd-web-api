// Explicit door close: proxies the service API's POST /elevators/{id}/close-doors.

export default defineEventHandler(async (event) => {
  const id = getRouterParam(event, 'id')
  const config = useRuntimeConfig()

  return await $fetch(`${config.serviceApiUrl}/elevators/${id}/close-doors`, {
    method: 'POST'
  })
})
