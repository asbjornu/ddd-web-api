// Insert the technician key: exchanges the shared secret for an HttpOnly
// session cookie. The secret is compared server-side and never sent back.

export default defineEventHandler(async (event) => {
  const config = useRuntimeConfig()
  const body = await readBody<{ secret?: string }>(event)
  const supplied = (body?.secret ?? '').trim()

  if (!supplied || supplied !== config.technicianKey) {
    clearTechnicianCookie(event)
    throw createError({
      statusCode: 401,
      statusMessage: 'Invalid technician key'
    })
  }

  issueTechnicianCookie(event, config.technicianKey)
  return { inserted: true }
})
