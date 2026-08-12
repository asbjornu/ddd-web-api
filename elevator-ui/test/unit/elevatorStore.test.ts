// Unit tests for the elevator store's derived state (getters). These
// don't hit the network -- see test/e2e for the full-stack smoke test.

import { describe, expect, it, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useElevatorStore } from '~/stores/elevator'

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
      { id: 1, elevatorId: 1, floor: 2, createdAt: '', servedAt: null },
      { id: 2, elevatorId: 1, floor: 7, createdAt: '', servedAt: '2024-01-01T00:00:00Z' }
    ]

    expect(store.pendingCarCalls).toHaveLength(1)
    expect(store.pendingCarCalls[0]?.floor).toBe(2)
  })

  it('merges pending landing calls and car calls into allPendingFloors', () => {
    const store = useElevatorStore()
    store.calls = [
      { id: 1, elevatorId: 1, floor: 3, direction: 'UP', createdAt: '', servedAt: null }
    ]
    store.carCalls = [
      { id: 1, elevatorId: 1, floor: 3, createdAt: '', servedAt: null },
      { id: 2, elevatorId: 1, floor: 9, createdAt: '', servedAt: null }
    ]

    expect(store.allPendingFloors).toEqual(new Set([3, 9]))
  })

  it('toggles technicianKeyInserted', () => {
    const store = useElevatorStore()
    expect(store.technicianKeyInserted).toBe(false)

    store.toggleTechnicianKey()
    expect(store.technicianKeyInserted).toBe(true)

    store.toggleTechnicianKey()
    expect(store.technicianKeyInserted).toBe(false)
  })
})
