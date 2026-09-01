// Simulated weight sensor: proxies the service API's POST
// /elevators/{id}/weight -- but validates the payload and pre-checks
// the same door-state conflict ElevatorService.setWeight itself
// enforces, before doing so. See
// server/utils/elevatorValidation.ts's own doc comment: elevator-api
// runs its own check again, against fresher state, when this proxied
// request actually arrives.

export default defineEventHandler(async (event) => {
  const id = getRouterParam(event, 'id')
  const config = useRuntimeConfig()
  const body = await readBody<{ weightKg?: unknown }>(event)

  if (typeof body?.weightKg !== 'number' || !Number.isInteger(body.weightKg) || body.weightKg < 0) {
    throw createError({ statusCode: 400, statusMessage: 'Invalid weight' })
  }

  const status = await fetchStatusForValidation(config.serviceApiUrl, id)
  if (status.state !== 'DOORS_OPEN') {
    throw createError({ statusCode: 409, statusMessage: 'Doors must be open to change weight' })
  }

  return await $fetch(`${config.serviceApiUrl}/elevators/${id}/weight`, {
    method: 'POST',
    body
  })
})
