// Unit tests for the elevator store's derived state (getters) and the
// technician key session. These don't hit elevator-api -- BFF routes are
// stubbed with registerEndpoint -- see test/e2e for the full-stack smoke
// test.
//
// Note: $fetch cannot be mocked with vi.stubGlobal here. Under
// `environment: 'nuxt'` the bare `$fetch` identifier resolves to an
// auto-imported binding, not globalThis.$fetch, so a stubbed global is
// never consulted. registerEndpoint stubs the endpoint itself, which also
// makes these tests assert the request the store actually sends.

import { describe, expect, it, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { registerEndpoint } from '@nuxt/test-utils/runtime'
import { createError, getHeader, readBody } from 'h3'
import { useElevatorStore } from '~/stores/elevator'

const VALID_SECRET = 'dev-secret-key'

// Stand-in for the BFF's technician key routes. `cookieInserted` plays the
// part of the HttpOnly cookie: server-side state the client cannot read.
let cookieInserted = false
let bffReachable = true
let maintenanceAuthHeader: string | undefined

registerEndpoint('/api/key', {
  method: 'GET',
  handler: () => {
    if (!bffReachable) throw createError({ statusCode: 503 })
    return { inserted: cookieInserted }
  }
})

registerEndpoint('/api/key', {
  method: 'POST',
  handler: async (event) => {
    const body = await readBody<{ secret?: string }>(event)
    if (body?.secret !== VALID_SECRET) {
      throw createError({ statusCode: 401, statusMessage: 'Invalid technician key' })
    }
    cookieInserted = true
    return { inserted: true }
  }
})

registerEndpoint('/api/key', {
  method: 'DELETE',
  handler: () => {
    cookieInserted = false
    return { inserted: false }
  }
})

registerEndpoint('/api/elevators/1/maintenance', {
  method: 'POST',
  handler: (event) => {
    maintenanceAuthHeader = getHeader(event, 'authorization')
    return {}
  }
})

registerEndpoint('/api/elevators/1/status', {
  method: 'GET',
  handler: () => ({ id: 1, currentFloor: 1, state: 'IDLE' })
})

describe('useElevatorStore getters', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('filters served calls out of pendingCalls', () => {
    const store = useElevatorStore()
    store.calls = [
      { id: 1, elevatorId: 1, floor: 3, direction: 'UP', createdAt: '', servedAt: null },
      { id: 2, elevatorId: 1, floor: 5, direction: 'DOWN', createdAt: '', servedAt: '2024-01-01T00:00:00Z' }
    ]

    expect(store.pendingCalls).toHaveLength(1)
    expect(store.pendingCalls[0]?.floor).toBe(3)
  })

  it('filters served car calls out of pendingCarCalls', () => {
    const store = useElevatorStore()
    store.carCalls = [
      { id: 1, elevatorId: 1, floor: 7, createdAt: '', servedAt: null },
      { id: 2, elevatorId: 1, floor: 2, createdAt: '', servedAt: '2024-01-01T00:00:00Z' }
    ]

    expect(store.pendingCarCalls).toHaveLength(1)
    expect(store.pendingCarCalls[0]?.floor).toBe(7)
  })

  it('collects pending floors from both call types', () => {
    const store = useElevatorStore()
    store.calls = [
      { id: 1, elevatorId: 1, floor: 3, direction: 'UP', createdAt: '', servedAt: null }
    ]
    store.carCalls = [
      { id: 2, elevatorId: 1, floor: 9, createdAt: '', servedAt: null }
    ]

    expect(store.allPendingFloors).toEqual(new Set([3, 9]))
  })
})

// The technician secret is server-side only: the store never holds it and
// never sends an Authorization header. It POSTs the key the user typed to
// the BFF, which replies with an HttpOnly cookie. Because that cookie is
// invisible to JavaScript, the store keeps its own mirrored boolean.
describe('useElevatorStore technician key session', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    cookieInserted = false
    bffReachable = true
    maintenanceAuthHeader = undefined
  })

  it('inserts the key when the BFF accepts the secret', async () => {
    const store = useElevatorStore()
    await store.insertKey(VALID_SECRET)

    expect(store.technicianKeyInserted).toBe(true)
    expect(store.error).toBeNull()
    expect(cookieInserted).toBe(true)
  })

  it('rejects a wrong secret without inserting the key', async () => {
    const store = useElevatorStore()
    await store.insertKey('wrong')

    expect(store.technicianKeyInserted).toBe(false)
    expect(store.error).toBe('Invalid technician key.')
    expect(cookieInserted).toBe(false)
  })

  it('withdraws the key', async () => {
    const store = useElevatorStore()
    await store.insertKey(VALID_SECRET)
    expect(cookieInserted).toBe(true)

    await store.withdrawKey()

    expect(store.technicianKeyInserted).toBe(false)
    expect(cookieInserted).toBe(false)
  })

  it('mirrors the server answer when refreshing key state', async () => {
    cookieInserted = true

    const store = useElevatorStore()
    await store.refreshKeyState()

    expect(store.technicianKeyInserted).toBe(true)
  })

  it('treats an unreachable BFF as key not inserted', async () => {
    bffReachable = false

    const store = useElevatorStore()
    store.technicianKeyInserted = true
    await store.refreshKeyState()

    expect(store.technicianKeyInserted).toBe(false)
  })

  // Regression guard for the security fix: the browser must not carry the
  // shared secret, so privileged requests go out with no Authorization
  // header. The BFF attaches the Bearer token itself.
  it('sends no Authorization header on privileged requests', async () => {
    const store = useElevatorStore()
    await store.insertKey(VALID_SECRET)
    await store.enterMaintenance()

    expect(maintenanceAuthHeader).toBeUndefined()
  })
})
