import { test, expect } from '@playwright/test'

// Smoke test: the rider page shell renders regardless of whether
// elevator-api is reachable (API connectivity is covered separately by
// elevator-api's own test suite).
test.describe('rider page', () => {
  test('renders heading and main panels', async ({ page }) => {
    await page.goto('/')

    await expect(page.getByRole('heading', { name: 'Elevator', exact: true })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Call elevator' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Elevator status' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Public status page' })).toBeVisible()
  })

  test('navigates to the public status page', async ({ page }) => {
    await page.goto('/')
    await page.getByRole('link', { name: 'Public status page' }).click()

    await expect(page).toHaveURL(/\/status$/)
    await expect(page.getByRole('heading', { name: 'Elevator status', level: 1 })).toBeVisible()
  })
})
