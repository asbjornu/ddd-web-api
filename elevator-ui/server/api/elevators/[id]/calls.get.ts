// Landing calls: proxies the service API's GET /elevators/{id}/calls.

export default defineEventHandler(async (event) => {
  const id = getRouterParam(event, 'id')
  const config = useRuntimeConfig()

  return await $fetch(`${config.serviceApiUrl}/elevators/${id}/calls`)
})
