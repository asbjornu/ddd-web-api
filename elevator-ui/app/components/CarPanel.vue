<script setup lang="ts">
const store = useElevatorStore()

const floors = Array.from({ length: BUILDING_FLOORS }, (_, i) => BUILDING_FLOORS - i)

const currentFloor = computed(() => store.status?.currentFloor ?? 1)

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
          pending: isPending(floor),
        }"
        :disabled="!canSelect(floor)"
        @click="select(floor)"
      >
        {{ floor }}
      </button>
    </div>
  </section>
</template>

<style scoped>
.car-panel {
  border: 1px solid #ccc;
  border-radius: 8px;
  padding: 1rem;
  width: 10rem;
  box-sizing: border-box;
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
</style>
