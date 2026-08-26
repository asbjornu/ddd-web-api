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

/**
 * Every command now shares one URL (see docs/architecture.md's
 * "Command endpoints: no verbs in URLs" note), so which behaviour a
 * POST invokes is the body's job -- specifically its hidden "type"
 * field, which the server already put in the operation's own fields
 * list. This client never names a command: it just echoes every
 * field's value back, overriding only the ones a form actually
 * collects (floor, direction, weightKg), so "type" (and any other
 * fixed field the server ever adds) rides along unexamined.
 */
function commandBody(
  operation: Operation,
  values: Record<string, unknown> = {}
): Record<string, unknown> {
  const body: Record<string, unknown> = {}
  for (const field of operation.fields ?? []) {
    body[field.name] = field.name in values ? values[field.name] : field.value
  }
  return body
}

// The read side's own shape, from GET /elevators/{id}/events on
// elevator-api directly -- not the BFF, and not the same fields as the
// old CRUD status endpoint this replaces (state and direction are now
// lower camelCase, "doorState" is "doorPosition"). destinationFloor
// (the old "targetFloor") is back as of slice 3: null except while
// state is movingUp/movingDown.
export interface ElevatorView {
  currentFloor: number
  state: string
  direction: string
  doorPosition: string
  obstructed: boolean
  weightKg: number
  capacityKg: number
  destinationFloor: number | null
  operations: Operation[]
}

export const ELEVATOR_ID = 1
export const BUILDING_FLOORS = 9

export const useElevatorStore = defineStore('elevator', {
  state: () => ({
    status: null as ElevatorView | null,
    loading: false,
    error: null as string | null,
    eventSource: null as EventSource | null
  }),
  getters: {
    // The one operation feature/callelevator contributes, when the
    // current state allows it -- absent, not disabled, while
    // outOfService or emergencyRecall. CallPanel renders this
    // generically: it does not know "call-elevator" is a rel any more
    // than it knows the floor count.
    callElevatorOperation: (state) =>
      state.status?.operations?.find((op) => op.rel === 'call-elevator') ?? null,
    // Same seam, for feature/selectfloor's operation.
    selectFloorOperation: (state) =>
      state.status?.operations?.find((op) => op.rel === 'select-floor') ?? null,
    // The remaining door operations: each present or absent on its own
    // terms (open-doors while not moving, close-doors while open,
    // obstruct-doors only while closing, clear-obstruction only while
    // obstructed) -- see docs/architecture.md's slice 4 roadmap entry.
    openDoorsOperation: (state) =>
      state.status?.operations?.find((op) => op.rel === 'open-doors') ?? null,
    closeDoorsOperation: (state) =>
      state.status?.operations?.find((op) => op.rel === 'close-doors') ?? null,
    obstructDoorsOperation: (state) =>
      state.status?.operations?.find((op) => op.rel === 'obstruct-doors') ?? null,
    clearObstructionOperation: (state) =>
      state.status?.operations?.find((op) => op.rel === 'clear-obstruction') ?? null,
    // feature.reportload's operation: present only while doors are
    // open, matching the physical setup it simulates -- see
    // docs/architecture.md's slice 5 roadmap entry.
    reportLoadOperation: (state) =>
      state.status?.operations?.find((op) => op.rel === 'report-load') ?? null,
    // The technician's own operations: present or absent by the exact
    // same seam as every rider operation, computed server-side from
    // one predicate over authority and state -- see
    // docs/architecture.md's "Key-switch and authorization" section.
    // This client never learns that "elevator:maintenance" exists, any
    // more than it learns a URL: it renders whatever operations arrive.
    insertKeyOperation: (state) =>
      state.status?.operations?.find((op) => op.rel === 'insert-key') ?? null,
    enterMaintenanceOperation: (state) =>
      state.status?.operations?.find((op) => op.rel === 'enter-maintenance') ?? null,
    exitMaintenanceOperation: (state) =>
      state.status?.operations?.find((op) => op.rel === 'exit-maintenance') ?? null,
    triggerEmergencyRecallOperation: (state) =>
      state.status?.operations?.find((op) => op.rel === 'trigger-emergency-recall') ?? null
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
            destinationFloor: data.destinationFloor ?? null,
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
          body: commandBody(operation, { floor, direction })
        })
        this.status = data
        this.error = null
      } catch {
        this.error = 'Unable to call the elevator.'
      } finally {
        this.loading = false
      }
    },
    // Same shape as callElevator: no hard-coded URL, and no more
    // pendingCarCalls list to refresh afterward -- the new read model
    // has no equivalent (only the single destinationFloor), which is
    // also why CarPanel now highlights a destination, not a list of
    // "pending" floors.
    async selectFloor(floor: number) {
      const operation = this.selectFloorOperation
      if (!operation) {
        this.error = 'Selecting a floor is not available right now.'
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
          body: commandBody(operation, { floor })
        })
        this.status = data
        this.error = null
      } catch {
        this.error = 'Unable to select floor.'
      } finally {
        this.loading = false
      }
    },
    // Same shape again: open-doors, close-doors, obstruct-doors and
    // clear-obstruction each follow their own operation's href/method.
    // toggleObstruction (one endpoint, a boolean payload doing double
    // duty) is gone with it -- obstructDoors and clearObstruction are
    // two distinct commands now, matching the two distinct affordances
    // the server offers at two different times.
    async openDoors() {
      const operation = this.openDoorsOperation
      if (!operation) {
        this.error = 'Opening the doors is not available right now.'
        return
      }
      try {
        // Without an explicit Accept header, $fetch's default
        // ("application/json") matches none of elevator-api's negotiated
        // formats and gets refused with 406 -- the command still runs,
        // but this client would never see it succeed, or pick up the
        // operations its own new state now offers (the SSE stream
        // carries properties, never operations).
        this.status = await $fetch<ElevatorView>(operation.href, {
          method: operation.method as 'POST',
          headers: { Accept: 'application/vnd.elevator.state+json' },
          body: commandBody(operation)
        })
        this.error = null
      } catch {
        this.error = 'Unable to open doors.'
      }
    },
    async closeDoors() {
      const operation = this.closeDoorsOperation
      if (!operation) {
        this.error = 'Closing the doors is not available right now.'
        return
      }
      try {
        this.error = null
        this.status = await $fetch<ElevatorView>(operation.href, {
          method: operation.method as 'POST',
          headers: { Accept: 'application/vnd.elevator.state+json' },
          body: commandBody(operation)
        })
      } catch (e) {
        // The API answers 409 with a domain reason ("Obstruction detected",
        // "Overload detected"), which is the one place a server-side rule
        // reaches the user verbatim.
        const err = e as { data?: { message?: string }; message?: string }
        const msg = err.data?.message || err.message || 'Unable to close doors.'
        this.error = msg
      }
    },
    async obstructDoors() {
      const operation = this.obstructDoorsOperation
      if (!operation) {
        this.error = 'Simulating an obstruction is not available right now.'
        return
      }
      try {
        this.status = await $fetch<ElevatorView>(operation.href, {
          method: operation.method as 'POST',
          headers: { Accept: 'application/vnd.elevator.state+json' },
          body: commandBody(operation)
        })
        this.error = null
      } catch {
        this.error = 'Unable to simulate an obstruction.'
      }
    },
    async clearObstruction() {
      const operation = this.clearObstructionOperation
      if (!operation) {
        this.error = 'Clearing the obstruction is not available right now.'
        return
      }
      try {
        this.status = await $fetch<ElevatorView>(operation.href, {
          method: operation.method as 'POST',
          headers: { Accept: 'application/vnd.elevator.state+json' },
          body: commandBody(operation)
        })
        this.error = null
      } catch {
        this.error = 'Unable to clear the obstruction.'
      }
    },
    // Sensor telemetry, not a rider's intention -- report-load is only
    // ever offered while doors are open, matching the physical setup:
    // only boarding/alighting changes what the car carries. Replaces
    // the old direct BFF call to /api/elevators/{id}/weight -- deleted
    // with it, along with the client-side "is this overloaded?"
    // re-derivation that gated the slider: canReportLoad now comes
    // from the operation's presence, the same seam every other command
    // uses.
    async reportLoad(weightKg: number) {
      const operation = this.reportLoadOperation
      if (!operation) {
        this.error = 'Reporting load is not available right now.'
        return
      }
      try {
        this.status = await $fetch<ElevatorView>(operation.href, {
          method: operation.method as 'POST',
          headers: { Accept: 'application/vnd.elevator.state+json' },
          body: commandBody(operation, { weightKg })
        })
        this.error = null
      } catch {
        this.error = 'Unable to report load.'
      }
    },
    // The technician cookie is HttpOnly, so the client cannot read it,
    // and the ordinary SSE stream every rider also uses carries no
    // Bearer token and therefore never reflects a technician's own
    // operations (see server/api/elevators/[id]/status.get.ts's own
    // Javadoc-equivalent comment on that gap). This authenticated
    // re-read is how the client picks up insert-key's disappearance
    // and enter-maintenance/exit-maintenance's appearance right after
    // the key actually turns, rather than mirroring a boolean the way
    // the CRUD-shaped BFF used to.
    async refreshAuthenticatedStatus() {
      try {
        const data = await $fetch<ElevatorView>(`/api/elevators/${ELEVATOR_ID}/status`)
        this.status = data
      } catch {
        // The SSE stream will catch up on its own next push.
      }
    },
    async insertKey(secret: string) {
      try {
        await $fetch('/api/key', { method: 'POST', body: { secret } })
        this.error = null
        await this.refreshAuthenticatedStatus()
      } catch {
        this.error = 'Invalid technician key.'
      }
    },
    async withdrawKey() {
      try {
        await $fetch('/api/key', { method: 'DELETE' })
      } catch {
        // Withdrawing is best-effort.
      }
      this.error = null
      await this.refreshAuthenticatedStatus()
    },
    async enterMaintenance() {
      const operation = this.enterMaintenanceOperation
      if (!operation) {
        this.error = 'Entering maintenance is not available right now.'
        return
      }
      try {
        await $fetch(`/api/elevators/${ELEVATOR_ID}/commands`, {
          method: 'POST',
          body: commandBody(operation)
        })
        this.error = null
        await this.refreshAuthenticatedStatus()
      } catch {
        this.error = 'Unable to enter maintenance.'
      }
    },
    async exitMaintenance() {
      const operation = this.exitMaintenanceOperation
      if (!operation) {
        this.error = 'Exiting maintenance is not available right now.'
        return
      }
      try {
        await $fetch(`/api/elevators/${ELEVATOR_ID}/commands`, {
          method: 'POST',
          body: commandBody(operation)
        })
        this.error = null
        await this.refreshAuthenticatedStatus()
      } catch {
        this.error = 'Unable to exit maintenance.'
      }
    },
    async triggerEmergencyRecall() {
      const operation = this.triggerEmergencyRecallOperation
      if (!operation) {
        this.error = 'Emergency recall is not available right now.'
        return
      }
      try {
        await $fetch(`/api/elevators/${ELEVATOR_ID}/commands`, {
          method: 'POST',
          body: commandBody(operation)
        })
        this.error = null
        await this.refreshAuthenticatedStatus()
      } catch {
        this.error = 'Unable to trigger emergency recall.'
      }
    }
  }
})
