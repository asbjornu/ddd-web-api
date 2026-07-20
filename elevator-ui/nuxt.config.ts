// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  compatibilityDate: '2025-07-15',
  devtools: { enabled: true },
  runtimeConfig: {
    // Overridable at runtime via the NUXT_SERVICE_API_URL env var (Nuxt's
    // standard runtimeConfig env override convention) -- setting a plain
    // SERVICE_API_URL would only apply at build time, not in the built
    // output running in a container.
    serviceApiUrl: 'http://localhost:8080'
  }
})
