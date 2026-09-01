<script setup lang="ts">
const store = useElevatorStore()

// Exactly crud's own button grid (HTML/CSS unchanged): one row per
// floor, an up-arrow and/or down-arrow button each, no button at all
// past the top or bottom floor. call-elevator is still one generic
// operation (never a URL or a per-floor rel this client hard-codes):
// every button posts through the same store.callElevator, which itself
// follows callElevatorOperation's own href/method -- see that action's
// own comment. BUILDING_FLOORS is the one constant this client already
// hard-codes elsewhere (CarPanel's own select-floor grid does the
// same) -- a known gap, not something this component introduces on its
// own. crud's isPending/floorsWithPendingCalls has no equivalent in the
// new read model (only the single destinationFloor CarPanel already
// highlights), so "active" never applies here; disabled follows
// whether call-elevator is offered at all, the same way every other
// button in this app already does.
const operation = computed(() => store.callElevatorOperation)

const floors = Array.from({ length: BUILDING_FLOORS }, (_, i) => BUILDING_FLOORS - i)

function canCallUp(floor: number) {
  return floor < BUILDING_FLOORS
}

function canCallDown(floor: number) {
  return floor > 1
}

function call(floor: number, direction: 'up' | 'down') {
  store.callElevator(floor, direction)
}
</script>

<template>
  <section class="call-panel">
    <h2>Call elevator</h2>
    <p v-if="store.error" class="error">{{ store.error }}</p>
    <ul>
      <li v-for="floor in floors" :key="floor" class="floor-row">
        <span class="floor-label">Floor {{ floor }}</span>
        <button
          v-if="canCallUp(floor)"
          type="button"
          :class="{ loading: store.loading }"
          :disabled="store.loading || !operation"
          @click="call(floor, 'up')"
        >
          ▲
        </button>
        <button
          v-if="canCallDown(floor)"
          type="button"
          :class="{ loading: store.loading }"
          :disabled="store.loading || !operation"
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
ul {
  list-style: none;
  margin: 0;
  padding: 0;
}
.floor-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.25rem 0;
}
.floor-label {
  width: 5rem;
  font-weight: bold;
}
button {
  width: 2.5rem;
  padding: 0.3rem 0;
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
