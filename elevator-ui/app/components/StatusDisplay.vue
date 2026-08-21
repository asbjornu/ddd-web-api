<script setup lang="ts">
const store = useElevatorStore()

const canOpenDoors = computed(() => {
  const state = store.status?.state
  return state === 'IDLE' || state === 'DOORS_OPEN' || state === 'DOORS_CLOSING'
})

const canCloseDoors = computed(() => {
  return store.status?.state === 'DOORS_OPEN' && !store.status?.obstructed
})

const obstructionWarning = computed(() => {
  return store.status?.obstructed ? 'Doors blocked — cannot close' : ''
})

const inMaintenance = computed(() => store.status?.state === 'OUT_OF_SERVICE')

const keyInput = ref('')

async function submitKey() {
  await store.insertKey(keyInput.value)
  if (store.technicianKeyInserted) keyInput.value = ''
}

let poller: ReturnType<typeof setInterval> | undefined

onMounted(() => {
  store.refreshKeyState()
  store.fetchStatus()
  store.fetchCalls()
  store.fetchCarCalls()
  poller = setInterval(() => {
    store.fetchStatus()
    store.fetchCalls()
    store.fetchCarCalls()
  }, 1500)
})

onUnmounted(() => {
  if (poller) clearInterval(poller)
})
</script>

<template>
  <section class="status">
    <h2>Elevator status</h2>
    <p v-if="store.error" class="error">{{ store.error }}</p>
    <dl v-if="store.status">
      <dt>Current floor</dt>
      <dd>{{ store.status.currentFloor }}</dd>

      <dt>State</dt>
      <dd>{{ store.status.state }}</dd>

      <dt>Direction</dt>
      <dd>{{ store.status.direction }}</dd>

      <dt>Doors</dt>
      <dd>{{ store.status.doorState }}</dd>

      <dt>Target floor</dt>
      <dd>{{ store.status.targetFloor ?? '—' }}</dd>

      <dt>Pending calls</dt>
      <dd>{{ store.pendingCalls.length }}</dd>

      <dt>Pending floors</dt>
      <dd>{{ store.allPendingFloors.size }}</dd>
    </dl>

    <div class="actions">
      <button
        :disabled="!canOpenDoors"
        @click="store.openDoors()"
      >
        Open doors
      </button>
      <button
        :disabled="!canCloseDoors"
        @click="store.closeDoors()"
      >
        Close doors
      </button>
      <p v-if="obstructionWarning" class="obstruction-warning">
        {{ obstructionWarning }}
      </p>
      <label class="obstruction-toggle">
        <input
          type="checkbox"
          :checked="store.status?.obstructed ?? false"
          @change="store.toggleObstruction()"
        />
        Obstruction
      </label>
    </div>

    <hr class="divider" />

    <div class="tech-section">
      <form
        v-if="!store.technicianKeyInserted"
        class="key-form"
        @submit.prevent="submitKey"
      >
        <input
          v-model="keyInput"
          type="password"
          class="key-input"
          placeholder="Technician key"
          autocomplete="off"
          aria-label="Technician key"
        />
        <button type="submit">Insert key</button>
      </form>
      <div v-else class="key-inserted">
        <span>Key inserted</span>
        <button @click="store.withdrawKey()">Withdraw key</button>
      </div>
      <div v-if="store.technicianKeyInserted" class="tech-actions">
        <button
          v-if="!inMaintenance"
          @click="store.enterMaintenance()"
        >
          Enter maintenance
        </button>
        <button
          v-if="inMaintenance"
          @click="store.exitMaintenance()"
        >
          Exit maintenance
        </button>
        <button
          @click="store.triggerEmergencyRecall()"
          class="emergency-btn"
        >
          Emergency recall
        </button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.status {
  border: 1px solid #ccc;
  border-radius: 8px;
  padding: 1rem;
  width: 100%;
  min-height: 18rem;
  box-sizing: border-box;
}
dl {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 0.25rem 1rem;
  margin: 0;
}
dt {
  font-weight: bold;
}
dd {
  margin: 0;
}
.error {
  color: #b00020;
}
.actions {
  margin-top: 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}
.actions button {
  padding: 0.4rem 0.8rem;
  cursor: pointer;
}
.actions button:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.obstruction-toggle {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.9rem;
  cursor: pointer;
}
.obstruction-warning {
  color: #b00020;
  font-size: 0.85rem;
  font-weight: bold;
  margin: 0;
}
.divider {
  border: none;
  border-top: 1px solid #ccc;
  margin: 1rem 0 0.5rem;
}
.tech-section {
  margin-top: 0.5rem;
}
.key-form {
  display: flex;
  gap: 0.4rem;
}
.key-input {
  flex: 1;
  min-width: 0;
  padding: 0.25rem 0.4rem;
  font-size: 0.85rem;
}
.key-inserted {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  font-size: 0.9rem;
}
.tech-actions {
  margin-top: 0.5rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}
.tech-actions button {
  padding: 0.4rem 0.8rem;
  cursor: pointer;
}
.emergency-btn {
  color: #b00020;
  font-weight: bold;
}
</style>
