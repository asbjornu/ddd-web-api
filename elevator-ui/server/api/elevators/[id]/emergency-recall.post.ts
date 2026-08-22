// Emergency recall: proxies POST /elevators/{id}/emergency-recall.
//
// Authorisation comes from the access token in the HttpOnly cookie. The
// browser never sees it, and the BFF holds no credential of its own --
// the token was obtained by exchanging what the technician typed.

export default defineEventHandler(async (event) => {
  const id = getRouterParam(event, 'id')
  const config = useRuntimeConfig()

  const token = requireToken(event)

  return await $fetch(`${config.serviceApiUrl}/elevators/${id}/emergency-recall`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` }
  })
})
