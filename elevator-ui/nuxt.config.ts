// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  compatibilityDate: '2025-07-15',
  devtools: { enabled: true },
  modules: ['@pinia/nuxt', '@nuxt/eslint'],
  runtimeConfig: {
    // Overridable at runtime via the NUXT_SERVICE_API_URL env var (Nuxt's
    // standard runtimeConfig env override convention) -- setting a plain
    // SERVICE_API_URL would only apply at build time, not in the built
    // output running in a container.
    serviceApiUrl: 'http://localhost:8080',

    // Server-only, overridable via NUXT_TECHNICIAN_KEY. This must never be
    // moved under `public`: anything there is inlined into the client
    // bundle and readable by anyone with devtools, which would let them
    // call elevator-api's key-switch endpoints directly on port 8080,
    // bypassing this BFF entirely. The browser exchanges the key for an
    // HttpOnly cookie (see server/api/key.post.ts); only the BFF ever
    // attaches the Bearer token to elevator-api.
    technicianKey: 'dev-secret-key'
  }
})
