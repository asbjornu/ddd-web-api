<script setup lang="ts">
const store = useElevatorStore()

const floors = Array.from({ length: BUILDING_FLOORS }, (_, i) => BUILDING_FLOORS - i)

function canCallUp(floor: number) {
  return floor < BUILDING_FLOORS
}

function canCallDown(floor: number) {
  return floor > 1
}

function isPending(floor: number) {
  return store.floorsWithPendingCalls.has(floor)
}

function call(floor: number, dir: 'UP' | 'DOWN') {
  store.callElevator(floor, dir)
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
          :class="{ active: isPending(floor), loading: store.loading }"
          :disabled="store.loading"
          @click="call(floor, 'UP')"
        >
          ▲
        </button>
        <button
          v-if="canCallDown(floor)"
          type="button"
          :class="{ active: isPending(floor), loading: store.loading }"
          :disabled="store.loading"
          @click="call(floor, 'DOWN')"
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
button.active {
  background: #c8e6c9;
  border-color: #4caf50;
  box-shadow: 0 0 4px rgba(76, 175, 80, 0.4);
}
button.loading {
  opacity: 0.6;
}
</style>
