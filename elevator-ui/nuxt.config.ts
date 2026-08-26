// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  compatibilityDate: '2025-07-15',
  // Disabled: DevTools injects an extra element into its SSR output that
  // the client doesn't expect, which Vue reports as a hydration mismatch
  // -- harmless directly against Nuxt's own dev server, but fatal to the
  // renderer once a request also passes through Caddy's reverse proxy
  // (the actual `docker compose up` path everyone else uses).
  devtools: { enabled: false },
  modules: ['@nuxt/eslint'],
  css: ['~/assets/css/main.css'],
  app: {
    head: {
      // Datastar drives every interactive part of this app -- see
      // docs/architecture.md's "elevator-ui: front-end only, no BFF"
      // section. There is no Pinia store, no typed API model, and no
      // hard-coded domain constant left in this project: the server
      // renders forms, this script morphs them in and keeps them
      // live over SSE, and that is the entire client.
      script: [
        {
          type: 'module',
          src: 'https://cdn.jsdelivr.net/gh/starfederation/datastar@v1.0.2/bundles/datastar.js'
        }
      ]
    }
  }
})
