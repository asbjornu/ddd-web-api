import { defineConfig, devices } from '@playwright/test'

// End-to-end smoke tests against the Nuxt UI shell. These don't require
// elevator-api to be running -- they only assert on static page
// structure, since API connectivity is covered by elevator-api's own
// tests and by manual `docker compose up` verification.
export default defineConfig({
  testDir: './test/e2e',
  fullyParallel: true,
  reporter: 'list',
  use: {
    baseURL: 'http://localhost:3000'
  },
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:3000',
    reuseExistingServer: !process.env.CI,
    timeout: 60_000
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] }
    }
  ]
})
