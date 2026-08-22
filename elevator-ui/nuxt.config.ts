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

    // Where elevator-auth lives, and which client this BFF presents itself
    // as. Overridable via NUXT_OAUTH_ISSUER and NUXT_OAUTH_CLIENT_ID.
    //
    // Note that the issuer is configuration rather than something this
    // application discovers, which is the CRUD-shaped part of an otherwise
    // standards-based arrangement: a client told where to look cannot be
    // moved without being reconfigured.
    oauthIssuer: 'http://localhost:9000',
    oauthClientId: 'elevator-technician'
  }
})
