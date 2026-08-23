<script setup lang="ts">
const store = useElevatorStore()

const floors = Array.from({ length: BUILDING_FLOORS }, (_, i) => BUILDING_FLOORS - i)

const currentFloor = computed(() => store.status?.currentFloor ?? 1)

const maxWeight = computed(() => store.status?.capacityKg ?? 800)

const overloaded = computed(() => (store.status?.weightKg ?? 0) > (store.status?.capacityKg ?? 0))

const doorsOpen = computed(() => store.status?.doorPosition === 'open')

function isPending(floor: number) {
  return store.allPendingFloors.has(floor)
}

function isCurrent(floor: number) {
  return floor === currentFloor.value
}

function canSelect(floor: number) {
  return floor !== currentFloor.value && !store.loading
}

function select(floor: number) {
  store.selectFloor(floor)
}
</script>

<template>
  <section class="car-panel">
    <h2>Select floor</h2>
    <div class="grid">
      <button
        v-for="floor in floors"
        :key="floor"
        type="button"
        :class="{
          current: isCurrent(floor),
          pending: isPending(floor)
        }"
        :disabled="!canSelect(floor)"
        @click="select(floor)"
      >
        {{ floor }}
      </button>
    </div>
    <div class="weight-section">
      <label>
        Weight: {{ store.status?.weightKg ?? 0 }} / {{ maxWeight }} kg
        <input
          type="range"
          :min="0"
          :max="maxWeight + 200"
          :value="store.status?.weightKg ?? 0"
          :disabled="!doorsOpen"
          @input="store.setWeight(Number(($event.target as HTMLInputElement).value))"
        />
      </label>
      <p v-if="overloaded" class="overload-warning">Overloaded — reduce weight to close doors</p>
    </div>
  </section>
</template>

<style scoped>
.car-panel {
  position: relative;
  border: 1px solid #ccc;
  border-radius: 8px;
  padding: 1rem;
  width: 10rem;
  box-sizing: border-box;
  background: #fff;
}
.car-panel::before {
  content: '';
  position: absolute;
  left: -10px;
  top: 50%;
  transform: translateY(-50%);
  border: 10px solid transparent;
  border-right-color: #ccc;
  border-left: 0;
}
.car-panel::after {
  content: '';
  position: absolute;
  left: -8px;
  top: 50%;
  transform: translateY(-50%);
  border: 9px solid transparent;
  border-right-color: #fff;
  border-left: 0;
}
h2 {
  margin: 0 0 0.5rem;
  font-size: 0.95rem;
}
.grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 0.35rem;
}
button {
  aspect-ratio: 1;
  border: 1px solid #999;
  border-radius: 6px;
  background: #f5f5f5;
  cursor: pointer;
  font-size: 1rem;
  font-weight: 600;
  transition:
    background 0.15s,
    box-shadow 0.15s;
}
button:hover:not(:disabled) {
  background: #e0e0e0;
}
button:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
button.current {
  background: #333;
  color: #fff;
  border-color: #333;
  opacity: 1;
}
button.pending {
  background: #c8e6c9;
  border-color: #4caf50;
  box-shadow: 0 0 4px rgba(76, 175, 80, 0.4);
}
.weight-section {
  margin-top: 1rem;
  border-top: 1px solid #ccc;
  padding-top: 0.75rem;
}
.weight-section label {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  font-size: 0.8rem;
}
.weight-section input[type='range'] {
  width: 100%;
  cursor: pointer;
}
.overload-warning {
  color: #b00020;
  font-size: 0.8rem;
  font-weight: bold;
  margin: 0.4rem 0 0;
}
</style>
