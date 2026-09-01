// Landing calls: proxies the service API's POST /elevators/{id}/calls --
// but validates the floor and direction, and pre-checks whether the
// elevator is in service, before doing so. See
// server/utils/elevatorValidation.ts's own doc comment for why that
// duplicates ElevatorService.call's own rules rather than replacing
// them: elevator-api runs every one of these checks again, against
// fresher state, when this proxied request actually arrives.

export default defineEventHandler(async (event) => {
  const id = getRouterParam(event, 'id')
  const config = useRuntimeConfig()
  const body = await readBody<{ floor?: unknown; direction?: unknown }>(event)

  if (!isValidFloor(body?.floor)) {
    // Same message, same status, as ElevatorService.call's own check --
    // copied by hand, not shared code.
    throw createError({ statusCode: 400, statusMessage: 'Invalid floor' })
  }
  if (!isValidDirection(body?.direction)) {
    throw createError({ statusCode: 400, statusMessage: 'Invalid direction' })
  }

  const status = await fetchStatusForValidation(config.serviceApiUrl, id)
  if (isOutOfServiceOrRecall(status)) {
    // Mirrors ElevatorService.call's own conflict -- but only as of the
    // status this file just read; the elevator's story-owning check
    // still runs again, against current state, when the POST below
    // reaches it.
    throw createError({ statusCode: 409, statusMessage: 'Elevator is not in service' })
  }

  return await $fetch(`${config.serviceApiUrl}/elevators/${id}/calls`, {
    method: 'POST',
    body
  })
})
