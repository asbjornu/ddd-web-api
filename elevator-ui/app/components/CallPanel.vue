<script setup lang="ts">
const store = useElevatorStore()

// One floor row per floor, an up-arrow and/or down-arrow button each --
// restoring the pre-hypermedia button grid crud always had, rather than
// a plain floor-number-plus-direction form. call-elevator is still one
// generic operation (never a URL or a per-floor rel this client hard-
// codes): every button posts through the same store.callElevator,
// which itself follows callElevatorOperation's own href/method -- see
// that action's own comment. BUILDING_FLOORS is the one constant this
// client already hard-codes elsewhere (CarPanel's own select-floor
// grid does the same) -- a known gap, not something this component
// introduces on its own.
const operation = computed(() => store.callElevatorOperation)

const floors = Array.from({ length: BUILDING_FLOORS }, (_, i) => BUILDING_FLOORS - i)

function canCallUp(floor: number) {
  return floor < BUILDING_FLOORS && !store.loading && operation.value != null
}

function canCallDown(floor: number) {
  return floor > 1 && !store.loading && operation.value != null
}

function call(floor: number, direction: 'up' | 'down') {
  store.callElevator(floor, direction)
}
</script>

<template>
  <section class="call-panel">
    <h2>Call elevator</h2>
    <p v-if="store.error" class="error">{{ store.error }}</p>
    <p v-if="!operation" class="unavailable">Calling the elevator is not available right now.</p>
    <ul v-else>
      <li v-for="floor in floors" :key="floor" class="floor-row">
        <span class="floor-label">Floor {{ floor }}</span>
        <button
          type="button"
          :class="{ loading: store.loading }"
          :disabled="!canCallUp(floor)"
          @click="call(floor, 'up')"
        >
          ▲
        </button>
        <button
          type="button"
          :class="{ loading: store.loading }"
          :disabled="!canCallDown(floor)"
          @click="call(floor, 'down')"
        >
          ▼
        </button>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.call-panel {
  border: 1px solid #ccc;
  border-radius: 8px;
  padding: 1rem;
  width: 100%;
  box-sizing: border-box;
}
.error {
  color: #b00020;
  font-size: 0.85rem;
  margin: 0 0 0.5rem;
}
.unavailable {
  color: #666;
  font-size: 0.85rem;
  margin: 0;
}
ul {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}
.floor-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
.floor-label {
  flex: 1;
  font-size: 0.85rem;
}
button {
  padding: 0.25rem 0.5rem;
  border: 1px solid #999;
  border-radius: 4px;
  background: #f5f5f5;
  cursor: pointer;
  font-size: 0.9rem;
  transition:
    background 0.2s,
    box-shadow 0.2s;
}
button:hover:not(:disabled) {
  background: #e0e0e0;
}
button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
button.loading {
  opacity: 0.6;
}
</style>
