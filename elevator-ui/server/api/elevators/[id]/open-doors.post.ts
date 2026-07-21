// Explicit door open: proxies the service API's POST /elevators/{id}/open-doors.

export default defineEventHandler(async (event) => {
  const id = getRouterParam(event, 'id')
  const config = useRuntimeConfig()

  return await $fetch(`${config.serviceApiUrl}/elevators/${id}/open-doors`, {
    method: 'POST'
  })
})
