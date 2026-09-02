// Obstruction sensor toggle: proxies the service API's PUT
// /elevators/{id}/obstruction -- but validates the payload's shape
// first. ElevatorService.setObstruction never validates this itself
// either (Boolean.getOrDefault-style coercion happens at Jackson's own
// deserialization layer, same gap as callElevator's direction field) --
// this file invents the check on its own, then hands the exact same
// body on unchanged.

export default defineEventHandler(async (event) => {
  const id = getRouterParam(event, 'id')
  const config = useRuntimeConfig()
  const body = await readBody<{ obstructed?: unknown }>(event)

  if (typeof body?.obstructed !== 'boolean') {
    throw createError({ statusCode: 400, statusMessage: 'Invalid obstructed value' })
  }

  return await $fetch(`${config.serviceApiUrl}/elevators/${id}/obstruction`, {
    method: 'PUT',
    body
  })
})
