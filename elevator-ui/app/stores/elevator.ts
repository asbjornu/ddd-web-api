// Elevator status + call state. There's exactly one seeded elevator
// (id 1) in a 9-floor building for v1 -- see docs/architecture.md's
// "Building and elevators" section.

/**
 * One field of an operation's payload, mirroring
 * elevator-api's shared.hypermedia.Field -- see docs/plan.html section
 * 9 for the format this is a minimal (bespoke, JSON) rendering of.
 */
export interface OperationField {
  name: string
  type: string
  value: unknown
  required?: boolean
  options?: string[]
}

/**
 * One legally available command, as the vnd.elevator.state+json format
 * carries it -- rel, title, method, href and fields, never a bare
 * "you may PATCH this". The client hard-codes neither which operations
 * exist nor their URLs: it renders whatever is present and posts to
 * whatever href it is given. See docs/architecture.md's "Affordances:
 * hypermedia over the aggregate" section.
 */
export interface Operation {
  rel: string
  title: string
  method: string
  href: string
  fields?: OperationField[]
}

// The read side's own shape, from GET /elevators/{id}/events on
// elevator-api directly -- not the BFF, and not the same fields as the
// old CRUD status endpoint this replaces (state and direction are now
// lower camelCase, "doorState" is "doorPosition", and there is no
// "targetFloor": nothing can move yet, since no command has landed on
// the new aggregate. See docs/architecture.md's "Roadmap" -- slice 3
// (Select floor) is what gives a moving elevator a destination again).
export interface ElevatorView {
  currentFloor: number
  state: string
  direction: string
  doorPosition: string
  obstructed: boolean
  weightKg: number
  capacityKg: number
  operations: Operation[]
}

export interface CarCall {
  id: number
  elevatorId: number
  floor: number
  createdAt: string
  servedAt: string | null
}

export const ELEVATOR_ID = 1
export const BUILDING_FLOORS = 9

export const useElevatorStore = defineStore('elevator', {
  state: () => ({
    status: null as ElevatorView | null,
    carCalls: [] as CarCall[],
    loading: false,
    error: null as string | null,
    technicianKeyInserted: false,
    eventSource: null as EventSource | null
  }),
  getters: {
    pendingCarCalls: (state) => state.carCalls.filter((c) => c.servedAt === null),
    // Landing calls (the old "floorsWithPendingCalls") have no
    // equivalent in the new read model yet: call-elevator moved onto
    // the new aggregate in slice 2, which does not expose pending
    // landing calls in its representation. Car-call highlighting is
    // unaffected -- select floor is still slice 3's to migrate.
    allPendingFloors(state): Set<number> {
      return new Set(state.carCalls.filter((c) => c.servedAt === null).map((c) => c.floor))
    },
    // The one operation feature/callelevator contributes, when the
    // current state allows it -- absent, not disabled, while
    // outOfService or emergencyRecall. CallPanel renders this
    // generically: it does not know "call-elevator" is a rel any more
    // than it knows the floor count.
    callElevatorOperation: (state) =>
      state.status?.operations?.find((op) => op.rel === 'call-elevator') ?? null
  },
  actions: {
    // Replaces the 1.5 s poller: one connection, pushed to rather than
    // asked, per docs/plan.html section 12. A relative URL works because
    // Caddy (docker-compose) puts elevator-api and elevator-ui behind one
    // origin -- see docs/architecture.md's "elevator-ui: front-end only,
    // no BFF" section. Running elevator-ui's dev server standalone,
    // without Caddy in front, cannot reach this endpoint.
    connectToEvents() {
      if (this.eventSource || import.meta.server) return
      const source = new EventSource(`/elevators/${ELEVATOR_ID}/events`)
      source.addEventListener('elevator-updated', (event) => {
        try {
          const data = JSON.parse((event as MessageEvent).data) as ElevatorView
          this.status = {
            currentFloor: data.currentFloor,
            state: data.state,
            direction: data.direction,
            doorPosition: data.doorPosition,
            obstructed: data.obstructed,
            weightKg: data.weightKg,
            capacityKg: data.capacityKg,
            operations: data.operations ?? []
          }
          this.error = null
        } catch {
          // A malformed event is dropped; the last good status stands.
        }
      })
      source.onerror = () => {
        this.error = 'Unable to reach the elevator.'
      }
      this.eventSource = source
    },
    disconnectFromEvents() {
      this.eventSource?.close()
      this.eventSource = null
    },
    async fetchCarCalls() {
      try {
        this.carCalls = await $fetch<CarCall[]>(`/api/elevators/${ELEVATOR_ID}/car-calls`)
      } catch {
        // car calls list is secondary
      }
    },
    // Follows the call-elevator operation's own href and method rather
    // than constructing a URL -- the one action this store no longer
    // hard-codes anything about, per docs/architecture.md's "Vertical
    // slices" rule that the client may not hard-code a URL. Goes
    // directly to elevator-api (the operation's href is server-issued,
    // absolute-path, and same-origin via Caddy), not the BFF.
    async callElevator(floor: number, direction: 'up' | 'down') {
      const operation = this.callElevatorOperation
      if (!operation) {
        this.error = 'Calling the elevator is not available right now.'
        return
      }
      this.loading = true
      try {
        // Without an explicit Accept header, $fetch's default
        // ("application/json") matches none of elevator-api's negotiated
        // formats and gets refused with 406 -- the command still runs
        // (its side effect already happened server-side before content
        // negotiation is even attempted), but this client would never
        // see it succeed, or pick up the operations its own new state
        // now offers (the SSE stream carries properties, never
        // operations).
        const data = await $fetch<ElevatorView>(operation.href, {
          method: operation.method as 'POST',
          headers: { Accept: 'application/vnd.elevator.state+json' },
          body: { floor, direction }
        })
        this.status = data
        this.error = null
      } catch {
        this.error = 'Unable to call the elevator.'
      } finally {
        this.loading = false
      }
    },
    async selectFloor(floor: number) {
      this.loading = true
      try {
        await $fetch(`/api/elevators/${ELEVATOR_ID}/car-calls`, {
          method: 'POST',
          body: { floor }
        })
        await this.fetchCarCalls()
        this.error = null
      } catch {
        this.error = 'Unable to select floor.'
      } finally {
        this.loading = false
      }
    },
    async openDoors() {
      try {
        await $fetch(`/api/elevators/${ELEVATOR_ID}/open-doors`, { method: 'POST' })
        this.error = null
      } catch {
        this.error = 'Unable to open doors.'
      }
    },
    async closeDoors() {
      try {
        this.error = null
        await $fetch(`/api/elevators/${ELEVATOR_ID}/close-doors`, { method: 'POST' })
      } catch (e) {
        // The API answers 409 with a domain reason ("Obstruction detected",
        // "Overload detected"), which is the one place a server-side rule
        // reaches the user verbatim.
        const err = e as { data?: { message?: string }; message?: string }
        const msg = err.data?.message || err.message || 'Unable to close doors.'
        this.error = msg
      }
    },
    async toggleObstruction() {
      const obstructed = !this.status?.obstructed
      try {
        await $fetch(`/api/elevators/${ELEVATOR_ID}/obstruction`, {
          method: 'PUT',
          body: { obstructed }
        })
        this.error = null
      } catch {
        this.error = 'Unable to toggle obstruction.'
      }
    },
    async setWeight(weightKg: number) {
      try {
        await $fetch(`/api/elevators/${ELEVATOR_ID}/weight`, {
          method: 'PUT',
          body: { weightKg }
        })
        this.error = null
      } catch {
        this.error = 'Unable to set weight.'
      }
    },
    // The technician cookie is HttpOnly, so the client cannot read it and
    // has to ask the BFF whether the key is still inserted. This mirrored
    // boolean is a second copy of authorization state the server already
    // holds -- see docs/architecture.md.
    async refreshKeyState() {
      try {
        const res = await $fetch<{ inserted: boolean }>('/api/key')
        this.technicianKeyInserted = res.inserted
      } catch {
        this.technicianKeyInserted = false
      }
    },
    async insertKey(secret: string) {
      try {
        await $fetch('/api/key', { method: 'POST', body: { secret } })
        this.technicianKeyInserted = true
        this.error = null
      } catch {
        this.technicianKeyInserted = false
        this.error = 'Invalid technician key.'
      }
    },
    async withdrawKey() {
      try {
        await $fetch('/api/key', { method: 'DELETE' })
      } catch {
        // Withdrawing is best-effort; the local flag is cleared regardless.
      }
      this.technicianKeyInserted = false
      this.error = null
    },
    async enterMaintenance() {
      try {
        await $fetch(`/api/elevators/${ELEVATOR_ID}/maintenance`, {
          method: 'POST',
          body: { maintenance: true }
        })
        this.error = null
      } catch {
        this.error = 'Unable to enter maintenance.'
      }
    },
    async exitMaintenance() {
      try {
        await $fetch(`/api/elevators/${ELEVATOR_ID}/maintenance`, {
          method: 'POST',
          body: { maintenance: false }
        })
        this.error = null
      } catch {
        this.error = 'Unable to exit maintenance.'
      }
    },
    async triggerEmergencyRecall() {
      try {
        await $fetch(`/api/elevators/${ELEVATOR_ID}/emergency-recall`, {
          method: 'POST'
        })
        this.error = null
      } catch {
        this.error = 'Unable to trigger emergency recall.'
      }
    }
  }
})
