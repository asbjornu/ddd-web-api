// Car calls (destination floor selection): proxies the service API's
// POST /elevators/{id}/car-calls -- but validates the floor and
// pre-checks the same three conflicts ElevatorService.carCall itself
// enforces, before doing so. See
// server/utils/elevatorValidation.ts's own doc comment: elevator-api
// runs every one of these checks again, against fresher state, when
// this proxied request actually arrives.

export default defineEventHandler(async (event) => {
  const id = getRouterParam(event, 'id')
  const config = useRuntimeConfig()
  const body = await readBody<{ floor?: unknown }>(event)

  if (!isValidFloor(body?.floor)) {
    throw createError({ statusCode: 400, statusMessage: 'Invalid floor' })
  }

  const status = await fetchStatusForValidation(config.serviceApiUrl, id)
  if (isOutOfServiceOrRecall(status)) {
    throw createError({ statusCode: 409, statusMessage: 'Elevator is not in service' })
  }
  if (body.floor === status.currentFloor && status.state === 'DOORS_OPEN') {
    throw createError({ statusCode: 400, statusMessage: 'Already at this floor' })
  }
  if (isOverloaded(status)) {
    throw createError({ statusCode: 409, statusMessage: 'Overload detected' })
  }

  return await $fetch(`${config.serviceApiUrl}/elevators/${id}/car-calls`, {
    method: 'POST',
    body
  })
})
