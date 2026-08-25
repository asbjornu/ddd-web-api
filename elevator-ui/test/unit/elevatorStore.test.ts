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

describe('useElevatorStore getters', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('finds the call-elevator operation among the current status operations', () => {
    const store = useElevatorStore()
    store.status = {
      currentFloor: 1,
      state: 'idle',
      direction: 'none',
      doorPosition: 'closed',
      obstructed: false,
      weightKg: 0,
      capacityKg: 800,
      destinationFloor: null,
      operations: [
        { rel: 'call-elevator', title: 'Call elevator', method: 'POST', href: '/elevators/1/calls' }
      ]
    }

    expect(store.callElevatorOperation?.rel).toBe('call-elevator')
  })

  it('has no call-elevator operation when the elevator offers none', () => {
    const store = useElevatorStore()
    store.status = {
      currentFloor: 1,
      state: 'outOfService',
      direction: 'none',
      doorPosition: 'closed',
      obstructed: false,
      weightKg: 0,
      capacityKg: 800,
      destinationFloor: null,
      operations: []
    }

    expect(store.callElevatorOperation).toBeNull()
  })

  it('finds the select-floor operation among the current status operations', () => {
    const store = useElevatorStore()
    store.status = {
      currentFloor: 1,
      state: 'movingUp',
      direction: 'up',
      doorPosition: 'closed',
      obstructed: false,
      weightKg: 0,
      capacityKg: 800,
      destinationFloor: 5,
      operations: [
        {
          rel: 'select-floor',
          title: 'Select a floor',
          method: 'POST',
          href: '/elevators/1/car-calls'
        }
      ]
    }

    expect(store.selectFloorOperation?.rel).toBe('select-floor')
  })
})

// The store follows the operation's own href and method rather than
// constructing a URL -- see docs/architecture.md's "Vertical slices"
// rule that the client may not hard-code a URL. registerEndpoint here
// stubs elevator-api directly (not the BFF): there is no "/api" prefix,
// since callElevator no longer goes through one.
describe('useElevatorStore callElevator', () => {
  let calledBody: unknown

  registerEndpoint('/elevators/1/calls', {
    method: 'POST',
    handler: async (event) => {
      calledBody = await readBody(event)
      // The response is what the store's own callElevator now assigns to
      // status, since the SSE stream never carries operations -- only
      // this response tells the client what it may do next.
      return {
        currentFloor: 1,
        state: 'idle',
        direction: 'none',
        doorPosition: 'closed',
        obstructed: false,
        weightKg: 0,
        capacityKg: 800,
        operations: []
      }
    }
  })

  beforeEach(() => {
    setActivePinia(createPinia())
    calledBody = undefined
  })

  it('does nothing when no call-elevator operation is present', async () => {
    const store = useElevatorStore()
    store.status = {
      currentFloor: 1,
      state: 'outOfService',
      direction: 'none',
      doorPosition: 'closed',
      obstructed: false,
      weightKg: 0,
      capacityKg: 800,
      destinationFloor: null,
      operations: []
    }

    await store.callElevator(3, 'up')

    expect(store.error).toBe('Calling the elevator is not available right now.')
    expect(calledBody).toBeUndefined()
  })

  it("posts to the operation's own href and method", async () => {
    const store = useElevatorStore()
    store.status = {
      currentFloor: 1,
      state: 'idle',
      direction: 'none',
      doorPosition: 'closed',
      obstructed: false,
      weightKg: 0,
      capacityKg: 800,
      destinationFloor: null,
      operations: [
        { rel: 'call-elevator', title: 'Call elevator', method: 'POST', href: '/elevators/1/calls' }
      ]
    }

    await store.callElevator(5, 'up')

    expect(calledBody).toEqual({ floor: 5, direction: 'up' })
    expect(store.error).toBeNull()
  })
})

describe('useElevatorStore selectFloor', () => {
  let calledBody: unknown

  registerEndpoint('/elevators/1/car-calls', {
    method: 'POST',
    handler: async (event) => {
      calledBody = await readBody(event)
      // The response is what the store's own selectFloor now assigns to
      // status, since the SSE stream never carries operations -- only
      // this response tells the client what it may do next.
      return {
        currentFloor: 1,
        state: 'movingUp',
        direction: 'up',
        doorPosition: 'closed',
        obstructed: false,
        weightKg: 0,
        capacityKg: 800,
        destinationFloor: null,
        operations: []
      }
    }
  })

  beforeEach(() => {
    setActivePinia(createPinia())
    calledBody = undefined
  })

  it('does nothing when no select-floor operation is present', async () => {
    const store = useElevatorStore()
    store.status = {
      currentFloor: 1,
      state: 'outOfService',
      direction: 'none',
      doorPosition: 'closed',
      obstructed: false,
      weightKg: 0,
      capacityKg: 800,
      destinationFloor: null,
      operations: []
    }

    await store.selectFloor(5)

    expect(store.error).toBe('Selecting a floor is not available right now.')
    expect(calledBody).toBeUndefined()
  })

  it("posts to the operation's own href and method", async () => {
    const store = useElevatorStore()
    store.status = {
      currentFloor: 1,
      state: 'idle',
      direction: 'none',
      doorPosition: 'closed',
      obstructed: false,
      weightKg: 0,
      capacityKg: 800,
      destinationFloor: null,
      operations: [
        {
          rel: 'select-floor',
          title: 'Select a floor',
          method: 'POST',
          href: '/elevators/1/car-calls'
        }
      ]
    }

    await store.selectFloor(6)

    expect(calledBody).toEqual({ floor: 6 })
    expect(store.error).toBeNull()
  })
})

// Same shape once more: open-doors, close-doors, obstruct-doors and
// clear-obstruction each follow their own operation's href/method, with
// no body -- see docs/architecture.md's slice 4 roadmap entry for why
// toggleObstruction (one endpoint, a boolean payload) is gone.
describe('useElevatorStore doors', () => {
  let openCalled = false
  let closeCalled = false
  let obstructCalled = false
  let clearCalled = false

  registerEndpoint('/elevators/1/open-doors', {
    method: 'POST',
    handler: () => {
      openCalled = true
      return {}
    }
  })
  registerEndpoint('/elevators/1/close-doors', {
    method: 'POST',
    handler: () => {
      closeCalled = true
      return {}
    }
  })
  registerEndpoint('/elevators/1/obstruct-doors', {
    method: 'POST',
    handler: () => {
      obstructCalled = true
      return {}
    }
  })
  registerEndpoint('/elevators/1/clear-obstruction', {
    method: 'POST',
    handler: () => {
      clearCalled = true
      return {}
    }
  })

  beforeEach(() => {
    setActivePinia(createPinia())
    openCalled = false
    closeCalled = false
    obstructCalled = false
    clearCalled = false
  })

  function statusWithOperations(...operations: Array<{ rel: string; href: string }>) {
    return {
      currentFloor: 1,
      state: 'doorsOpen',
      direction: 'none',
      doorPosition: 'open',
      obstructed: false,
      weightKg: 0,
      capacityKg: 800,
      destinationFloor: null,
      operations: operations.map((op) => ({
        title: op.rel,
        method: 'POST',
        ...op
      }))
    }
  }

  it('does nothing when no matching operation is present', async () => {
    const store = useElevatorStore()
    store.status = statusWithOperations()

    await store.openDoors()
    await store.closeDoors()
    await store.obstructDoors()
    await store.clearObstruction()

    expect(openCalled).toBe(false)
    expect(closeCalled).toBe(false)
    expect(obstructCalled).toBe(false)
    expect(clearCalled).toBe(false)
  })

  it('follows each operation when present', async () => {
    const store = useElevatorStore()
    store.status = statusWithOperations(
      { rel: 'open-doors', href: '/elevators/1/open-doors' },
      { rel: 'close-doors', href: '/elevators/1/close-doors' },
      { rel: 'obstruct-doors', href: '/elevators/1/obstruct-doors' },
      { rel: 'clear-obstruction', href: '/elevators/1/clear-obstruction' }
    )

    await store.openDoors()
    await store.closeDoors()
    await store.obstructDoors()
    await store.clearObstruction()

    expect(openCalled).toBe(true)
    expect(closeCalled).toBe(true)
    expect(obstructCalled).toBe(true)
    expect(clearCalled).toBe(true)
    expect(store.error).toBeNull()
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
