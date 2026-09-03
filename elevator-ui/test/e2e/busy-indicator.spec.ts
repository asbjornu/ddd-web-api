import { test, expect } from '@playwright/test'

// Regression test for the bug where every control was permanently
// disabled from the moment the page loaded: busy-indicator.ts counted
// every datastar-fetch "started" event, including the SSE
// subscription's own (data-init="@get('.../events')") -- which, being
// a deliberately long-lived connection, never fires a matching
// "finished". Simulates the exact event sequence captured live
// against the real stack (docs/call-chain-call-elevator-rest.md's own
// SSE capture is the same subscription) without needing elevator-api
// itself running: this is pure DOM/event behaviour, not API
// connectivity, so it belongs here rather than in elevator-api's own
// suite -- see rider-page.spec.ts's own comment for why this project
// draws that line the same way elsewhere.
test.describe('busy indicator', () => {
  test('the SSE subscription starting never leaves every control disabled', async ({ page }) => {
    await page.goto('/')

    await page.evaluate(() => {
      function dispatch(type: string, dataInit: string) {
        const el = document.createElement('div')
        el.setAttribute('data-init', dataInit)
        document.dispatchEvent(new CustomEvent('datastar-fetch', { detail: { type, el } }))
      }
      // The exact sequence captured live on page load: four @get
      // calls start, but only three ever finish -- the SSE
      // subscription's own "started" is the one with no matching
      // "finished", since the connection stays open by design.
      dispatch('started', "@get('/')")
      dispatch('finished', "@get('/')")
      dispatch('started', "@get('/elevators')")
      dispatch('finished', "@get('/elevators')")
      dispatch('started', "@get('/elevators/1')")
      dispatch('finished', "@get('/elevators/1')")
      dispatch('started', "@get('/elevators/1/events')")
      // never finishes -- this is the one that used to stick
    })

    await expect(page.locator('body')).not.toHaveClass(/busy/)
  })

  test('an ordinary fetch still toggles busy while in flight', async ({ page }) => {
    await page.goto('/')

    await page.evaluate(() => {
      const el = document.createElement('div')
      el.setAttribute('data-init', "@post('/elevators/1')")
      document.dispatchEvent(new CustomEvent('datastar-fetch', { detail: { type: 'started', el } }))
    })
    await expect(page.locator('body')).toHaveClass(/busy/)

    await page.evaluate(() => {
      const el = document.createElement('div')
      el.setAttribute('data-init', "@post('/elevators/1')")
      document.dispatchEvent(
        new CustomEvent('datastar-fetch', { detail: { type: 'finished', el } })
      )
    })
    await expect(page.locator('body')).not.toHaveClass(/busy/)
  })
})
