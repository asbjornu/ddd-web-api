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

// Every command now shares one URL (see docs/architecture.md's "Command
// endpoints: no verbs in URLs" note), so one stub captures every
// command's body -- each test asserts on the "type" field the server's
// hidden field put there, never a URL of its own. Every response gets
// the full elevator-state+json shape back, exactly as the real
// CommandsController would answer, since the store now assigns every
// command's response straight to status: the SSE stream never carries
// operations, only properties.
let lastCommandBody: Record<string, unknown> | undefined

const BASE_VIEW = {
  currentFloor: 1,
  state: 'idle',
  direction: 'none',
  doorPosition: 'closed',
  obstructed: false,
  weightKg: 0,
  capacityKg: 800,
  destinationFloor: null
}

// All five door-related operations at once, so a sequence of them (as
// the 'doors' describe block below exercises) keeps every next one
// available exactly as the real doorsOpen representation would.
const DOOR_OPERATIONS = [
  { rel: 'open-doors', type: 'OpenDoors' },
  { rel: 'close-doors', type: 'CloseDoors' },
  { rel: 'obstruct-doors', type: 'ObstructDoors' },
  { rel: 'clear-obstruction', type: 'ClearObstruction' },
  { rel: 'report-load', type: 'ReportLoad' }
].map((op) => ({
  rel: op.rel,
  title: op.rel,
  method: 'POST',
  href: '/elevators/1',
  fields:
    op.type === 'ReportLoad'
      ? [
          { name: 'type', type: 'hidden', value: op.type, required: true },
          { name: 'weightKg', type: 'text', value: null, required: true }
        ]
      : [{ name: 'type', type: 'hidden', value: op.type, required: true }]
}))

registerEndpoint('/elevators/1', {
  method: 'POST',
  handler: async (event) => {
    lastCommandBody = await readBody(event)
    return { ...BASE_VIEW, state: 'doorsOpen', doorPosition: 'open', operations: DOOR_OPERATIONS }
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

// Every operation shares one URL now (see docs/architecture.md's
// "Command endpoints: no verbs in URLs" note): the store follows
// whatever href and method the operation carries, and the body is
// built entirely from the operation's own fields -- including its
// hidden "type" -- never a URL or command name the client invents.
// registerEndpoint here stubs elevator-api directly (not the BFF):
// there is no "/api" prefix, since these commands no longer go
// through one.
describe('useElevatorStore callElevator', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    lastCommandBody = undefined
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
    expect(lastCommandBody).toBeUndefined()
  })

  it("posts to the operation's own href and method, echoing its hidden type", async () => {
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
          rel: 'call-elevator',
          title: 'Call elevator',
          method: 'POST',
          href: '/elevators/1',
          fields: [
            { name: 'type', type: 'hidden', value: 'CallElevator', required: true },
            { name: 'floor', type: 'text', value: null, required: true },
            {
              name: 'direction',
              type: 'select',
              value: null,
              required: true,
              options: ['up', 'down']
            }
          ]
        }
      ]
    }

    await store.callElevator(5, 'up')

    expect(lastCommandBody).toEqual({ type: 'CallElevator', floor: 5, direction: 'up' })
    expect(store.error).toBeNull()
  })
})

describe('useElevatorStore selectFloor', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    lastCommandBody = undefined
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
    expect(lastCommandBody).toBeUndefined()
  })

  it("posts to the operation's own href and method, echoing its hidden type", async () => {
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
          href: '/elevators/1',
          fields: [
            { name: 'type', type: 'hidden', value: 'SelectFloor', required: true },
            { name: 'floor', type: 'text', value: null, required: true }
          ]
        }
      ]
    }

    await store.selectFloor(6)

    expect(lastCommandBody).toEqual({ type: 'SelectFloor', floor: 6 })
    expect(store.error).toBeNull()
  })
})

// Same shape once more: open-doors, close-doors, obstruct-doors,
// clear-obstruction and report-load each follow their own operation's
// href/method/fields -- see docs/architecture.md's slice 4 roadmap
// entry for why toggleObstruction (one endpoint, a boolean payload) is
// gone.
describe('useElevatorStore doors', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    lastCommandBody = undefined
  })

  it('does nothing when no matching operation is present', async () => {
    const store = useElevatorStore()
    store.status = { ...BASE_VIEW, state: 'doorsOpen', doorPosition: 'open', operations: [] }

    await store.openDoors()
    await store.closeDoors()
    await store.obstructDoors()
    await store.clearObstruction()
    await store.reportLoad(500)

    expect(lastCommandBody).toBeUndefined()
    expect(store.error).toBe('Reporting load is not available right now.')
  })

  it('follows each operation when present, echoing its hidden type', async () => {
    const store = useElevatorStore()
    store.status = {
      ...BASE_VIEW,
      state: 'doorsOpen',
      doorPosition: 'open',
      operations: DOOR_OPERATIONS
    }

    await store.openDoors()
    expect(lastCommandBody).toEqual({ type: 'OpenDoors' })

    await store.closeDoors()
    expect(lastCommandBody).toEqual({ type: 'CloseDoors' })

    await store.obstructDoors()
    expect(lastCommandBody).toEqual({ type: 'ObstructDoors' })

    await store.clearObstruction()
    expect(lastCommandBody).toEqual({ type: 'ClearObstruction' })

    await store.reportLoad(500)
    expect(lastCommandBody).toEqual({ type: 'ReportLoad', weightKg: 500 })

    expect(store.error).toBeNull()
    // Each response (stubbed to echo the same DOOR_OPERATIONS) is what
    // keeps the next command's own operation available -- not a stale
    // copy of what an earlier status happened to carry.
    expect(store.status?.operations).toEqual(DOOR_OPERATIONS)
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
