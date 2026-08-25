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

    // Which client this BFF presents itself as when exchanging the
    // technician's secret for a token -- overridable via
    // NUXT_OAUTH_CLIENT_ID. The issuer itself is no longer configured
    // here: exchangeKeyForToken discovers it from elevator-api's own
    // RFC 9728 challenge, per docs/architecture.md's "Key-switch and
    // authorization" section.
    oauthClientId: 'elevator-technician'
  }
})
