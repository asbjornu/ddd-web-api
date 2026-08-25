// Technician key-switch commands (enter/exit-maintenance so far):
// proxies POST /elevators/{id} -- the same shared command endpoint
// every rider command already goes to directly, except this one needs
// the access token the browser cannot attach itself.
//
// Authorisation comes from the access token in the HttpOnly cookie. The
// browser never sees it, and the BFF holds no credential of its own --
// the token was obtained by exchanging what the technician typed. The
// body (a "type" naming the command, same as every other command) is
// forwarded unexamined: this route does not know or care which
// technician command it is proxying, only that one requires a Bearer
// token to reach elevator-api.

export default defineEventHandler(async (event) => {
  const id = getRouterParam(event, 'id')
  const config = useRuntimeConfig()

  const token = requireToken(event)

  const body = await readBody(event)

  return await $fetch(`${config.serviceApiUrl}/elevators/${id}`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: 'application/vnd.elevator.state+json'
    },
    body
  })
})
