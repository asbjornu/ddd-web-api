// Withdraw the technician key: clears the session cookie.

export default defineEventHandler((event) => {
  clearTechnicianCookie(event)
  return { inserted: false }
})
