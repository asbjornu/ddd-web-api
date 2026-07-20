// Public, unauthenticated read model -- proxies the service API's
// GET /elevators/{id}/status. See docs/architecture.md's "Authentication
// and authorization" section: no key is required for this endpoint.

export default defineEventHandler(async (event) => {
  const id = getRouterParam(event, 'id')
  const config = useRuntimeConfig()

  return await $fetch(`${config.serviceApiUrl}/elevators/${id}/status`)
})
