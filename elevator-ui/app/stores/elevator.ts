// Elevator status + call state. There's exactly one seeded elevator
// (id 1) in a 9-floor building for v1 -- see docs/architecture.md's
// "Building and elevators" section.

export interface ElevatorStatus {
  id: number
  currentFloor: number
  state: string
  direction: string
  doorState: string
  weightCapacityKg: number
  currentWeightKg: number
  departureFloor: number
  targetFloor: number | null
  obstructed: boolean
}

export interface Call {
  id: number
  elevatorId: number
  floor: number
  direction: string
  createdAt: string
  servedAt: string | null
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
const TECHNICIAN_KEY = 'dev-secret-key'

export const useElevatorStore = defineStore('elevator', {
  state: () => ({
    status: null as ElevatorStatus | null,
    calls: [] as Call[],
    carCalls: [] as CarCall[],
    loading: false,
    error: null as string | null,
    technicianKeyInserted: false
  }),
  getters: {
    pendingCalls: (state) => state.calls.filter((c) => c.servedAt === null),
    pendingCarCalls: (state) => state.carCalls.filter((c) => c.servedAt === null),
    floorsWithPendingCalls: (state) =>
      new Set(
        state.calls
          .filter((c) => c.servedAt === null)
          .map((c) => c.floor)
      ),
    floorsWithPendingCarCalls: (state) =>
      new Set(
        state.carCalls
          .filter((c) => c.servedAt === null)
          .map((c) => c.floor)
      ),
    allPendingFloors(): Set<number> {
      const floors = new Set(this.floorsWithPendingCalls)
      for (const f of this.floorsWithPendingCarCalls) floors.add(f)
      return floors
    },
  },
  actions: {
    async fetchStatus() {
      try {
        this.status = await $fetch<ElevatorStatus>(`/api/elevators/${ELEVATOR_ID}/status`)
        this.error = null
      } catch {
        this.error = 'Unable to reach the elevator.'
      }
    },
    async fetchCalls() {
      try {
        this.calls = await $fetch<Call[]>(`/api/elevators/${ELEVATOR_ID}/calls`)
      } catch {
        // calls list is secondary; don't overwrite a more meaningful error
      }
    },
    async fetchCarCalls() {
      try {
        this.carCalls = await $fetch<CarCall[]>(`/api/elevators/${ELEVATOR_ID}/car-calls`)
      } catch {
        // car calls list is secondary
      }
    },
    async callElevator(floor: number, direction: 'UP' | 'DOWN') {
      this.loading = true
      try {
        await $fetch(`/api/elevators/${ELEVATOR_ID}/calls`, {
          method: 'POST',
          body: { floor, direction }
        })
        await Promise.all([this.fetchStatus(), this.fetchCalls()])
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
        await Promise.all([this.fetchStatus(), this.fetchCarCalls()])
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
        await this.fetchStatus()
        this.error = null
      } catch {
        this.error = 'Unable to open doors.'
      }
    },
    async closeDoors() {
      try {
        this.error = null
        await $fetch(`/api/elevators/${ELEVATOR_ID}/close-doors`, { method: 'POST' })
        await this.fetchStatus()
      } catch (e: any) {
        const msg = e?.data?.message || e?.message || 'Unable to close doors.'
        this.error = msg
        await this.fetchStatus()
      }
    },
    async toggleObstruction() {
      const obstructed = !this.status?.obstructed
      try {
        await $fetch(`/api/elevators/${ELEVATOR_ID}/obstruction`, {
          method: 'POST',
          body: { obstructed }
        })
        await this.fetchStatus()
        this.error = null
      } catch {
        this.error = 'Unable to toggle obstruction.'
      }
    },
    async setWeight(weightKg: number) {
      try {
        await $fetch(`/api/elevators/${ELEVATOR_ID}/weight`, {
          method: 'POST',
          body: { weightKg }
        })
        await this.fetchStatus()
        this.error = null
      } catch {
        this.error = 'Unable to set weight.'
      }
    },
    toggleTechnicianKey() {
      this.technicianKeyInserted = !this.technicianKeyInserted
    },
    async enterMaintenance() {
      try {
        await $fetch(`/api/elevators/${ELEVATOR_ID}/maintenance`, {
          method: 'POST',
          headers: { 'X-Technician-Key': TECHNICIAN_KEY },
          body: { maintenance: true }
        })
        await this.fetchStatus()
        this.error = null
      } catch {
        this.error = 'Unable to enter maintenance.'
      }
    },
    async exitMaintenance() {
      try {
        await $fetch(`/api/elevators/${ELEVATOR_ID}/maintenance`, {
          method: 'POST',
          headers: { 'X-Technician-Key': TECHNICIAN_KEY },
          body: { maintenance: false }
        })
        await this.fetchStatus()
        this.error = null
      } catch {
        this.error = 'Unable to exit maintenance.'
      }
    }
  }
})
