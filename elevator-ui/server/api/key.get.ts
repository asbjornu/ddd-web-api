// Whether the technician key is currently inserted.
//
// The front-end needs this because the cookie is HttpOnly and therefore
// invisible to JavaScript -- the client cannot tell on its own whether it
// still holds the key, so it has to ask.

export default defineEventHandler((event) => {
  const config = useRuntimeConfig()
  return { inserted: hasTechnicianKey(event, config.technicianKey) }
})
