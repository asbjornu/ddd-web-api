// Withdraw the technician key: clears the cookie holding the token.
//
// There is no server-side session to invalidate, so the token remains
// technically valid until it expires. Its lifetime is short for exactly
// this reason.

export default defineEventHandler((event) => {
  clearToken(event)
  return { inserted: false }
})
