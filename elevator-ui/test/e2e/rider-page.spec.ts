import { test, expect } from '@playwright/test'

// Smoke test: the rider page shell renders regardless of whether
// elevator-api is reachable -- there is nothing else static left to
// assert on here, since every other piece of content (forms, status)
// is fetched and rendered by elevator-api itself and morphed in by
// Datastar; API connectivity is covered separately by elevator-api's
// own test suite and by manual `docker compose up` verification.
test.describe('rider page', () => {
  test('renders the page shell', async ({ page }) => {
    await page.goto('/')

    await expect(page.getByRole('heading', { name: 'Elevator', exact: true })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Public status page' })).toBeVisible()
  })

  test('navigates to the public status page', async ({ page }) => {
    await page.goto('/')
    await page.getByRole('link', { name: 'Public status page' }).click()

    await expect(page).toHaveURL(/\/status$/)
    await expect(page.getByRole('heading', { name: 'Elevator status', level: 1 })).toBeVisible()
  })
})
