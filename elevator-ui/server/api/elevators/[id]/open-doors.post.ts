// Explicit door open: proxies the service API's POST
// /elevators/{id}/open-doors -- but pre-checks the same two conflicts
// ElevatorService.openDoors itself enforces, before doing so. See
// server/utils/elevatorValidation.ts's own doc comment: elevator-api
// runs both checks again, against fresher state, when this proxied
// request actually arrives.

export default defineEventHandler(async (event) => {
  const id = getRouterParam(event, 'id')
  const config = useRuntimeConfig()

  const status = await fetchStatusForValidation(config.serviceApiUrl, id)
  if (status.state === 'MOVING_UP' || status.state === 'MOVING_DOWN') {
    throw createError({ statusCode: 409, statusMessage: 'Cannot open doors while moving' })
  }
  if (isOutOfServiceOrRecall(status)) {
    throw createError({ statusCode: 409, statusMessage: 'Elevator is not in service' })
  }

  return await $fetch(`${config.serviceApiUrl}/elevators/${id}/open-doors`, {
    method: 'POST'
  })
})
