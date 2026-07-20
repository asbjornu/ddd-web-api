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
  targetFloor: number | null
}

export interface Call {
  id: number
  elevatorId: number
  floor: number
  direction: string
  createdAt: string
  servedAt: string | null
}

export const ELEVATOR_ID = 1
export const BUILDING_FLOORS = 9

export const useElevatorStore = defineStore('elevator', {
  state: () => ({
    status: null as ElevatorStatus | null,
    calls: [] as Call[],
    loading: false,
    error: null as string | null
  }),
  getters: {
    pendingCalls: (state) => state.calls.filter((c) => c.servedAt === null),
    floorsWithPendingCalls: (state) =>
      new Set(
        state.calls
          .filter((c) => c.servedAt === null)
          .map((c) => c.floor)
      ),
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
    }
  }
})
