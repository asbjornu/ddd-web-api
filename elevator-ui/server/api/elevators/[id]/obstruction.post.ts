// Obstruction sensor toggle: proxies the service API's POST /elevators/{id}/obstruction.

export default defineEventHandler(async (event) => {
  const id = getRouterParam(event, 'id')
  const config = useRuntimeConfig()
  const body = await readBody(event)

  return await $fetch(`${config.serviceApiUrl}/elevators/${id}/obstruction`, {
    method: 'POST',
    body
  })
})
