// Explicit door close: proxies the service API's POST
// /elevators/{id}/close-doors -- but pre-checks the same three
// conflicts ElevatorService.closeDoors itself enforces, before doing
// so. See server/utils/elevatorValidation.ts's own doc comment:
// elevator-api runs every one of these checks again, against fresher
// state, when this proxied request actually arrives -- including the
// obstruction check, which is exactly the kind of state that can
// change in the gap between this file's own read and its write.

export default defineEventHandler(async (event) => {
  const id = getRouterParam(event, 'id')
  const config = useRuntimeConfig()

  const status = await fetchStatusForValidation(config.serviceApiUrl, id)
  if (status.state !== 'DOORS_OPEN') {
    throw createError({ statusCode: 409, statusMessage: 'Doors are not open' })
  }
  if (status.obstructed) {
    throw createError({ statusCode: 409, statusMessage: 'Obstruction detected' })
  }
  if (isOverloaded(status)) {
    throw createError({ statusCode: 409, statusMessage: 'Overload detected' })
  }

  return await $fetch(`${config.serviceApiUrl}/elevators/${id}/close-doors`, {
    method: 'POST'
  })
})
