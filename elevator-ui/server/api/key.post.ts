// Insert the technician key: exchanges the typed secret for a scoped access
// token at elevator-auth, and stores the token in an HttpOnly cookie.
// The secret itself is never stored and never returned.

export default defineEventHandler(async (event) => {
  const body = await readBody<{ secret?: string }>(event)
  const supplied = (body?.secret ?? '').trim()

  if (!supplied) {
    throw createError({ statusCode: 400, statusMessage: 'No key supplied' })
  }

  const token = await exchangeKeyForToken(supplied)
  if (!token) {
    clearToken(event)
    throw createError({ statusCode: 401, statusMessage: 'Invalid technician key' })
  }

  storeToken(event, token)
  return { inserted: true, scope: token.scope, expiresIn: token.expires_in }
})
