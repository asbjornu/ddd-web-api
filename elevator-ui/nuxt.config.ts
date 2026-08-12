// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  compatibilityDate: '2025-07-15',
  devtools: { enabled: true },
  modules: ['@pinia/nuxt'],
  runtimeConfig: {
    // Overridable at runtime via the NUXT_SERVICE_API_URL env var (Nuxt's
    // standard runtimeConfig env override convention) -- setting a plain
    // SERVICE_API_URL would only apply at build time, not in the built
    // output running in a container.
    serviceApiUrl: 'http://localhost:8080',
    public: {
      // Overridable via NUXT_PUBLIC_TECHNICIAN_KEY. Kept public because the
      // key-switch secret is shared between the front-end and the service
      // API (mock physical key access, not a user credential).
      technicianKey: 'dev-secret-key'
    }
  }
})
