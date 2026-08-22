// Technician key-switch actions: proxies POST /elevators/{id}/maintenance.
//
// Authorisation comes from the access token in the HttpOnly cookie. The
// browser never sees it, and the BFF holds no credential of its own --
// the token was obtained by exchanging what the technician typed.

export default defineEventHandler(async (event) => {
  const id = getRouterParam(event, 'id')
  const config = useRuntimeConfig()

  const token = requireToken(event)

  const body = await readBody(event)

  return await $fetch(`${config.serviceApiUrl}/elevators/${id}/maintenance`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body
  })
})
