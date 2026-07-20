// Landing calls: proxies the service API's POST /elevators/{id}/calls.

export default defineEventHandler(async (event) => {
  const id = getRouterParam(event, 'id')
  const config = useRuntimeConfig()
  const body = await readBody(event)

  return await $fetch(`${config.serviceApiUrl}/elevators/${id}/calls`, {
    method: 'POST',
    body
  })
})
