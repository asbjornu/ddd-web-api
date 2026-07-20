<script setup lang="ts">
const store = useElevatorStore()

const floors = Array.from({ length: BUILDING_FLOORS }, (_, i) => BUILDING_FLOORS - i)

function canCallUp(floor: number) {
  return floor < BUILDING_FLOORS
}

function canCallDown(floor: number) {
  return floor > 1
}
</script>

<template>
  <section class="call-panel">
    <h2>Call elevator</h2>
    <ul>
      <li v-for="floor in floors" :key="floor" class="floor-row">
        <span class="floor-label">Floor {{ floor }}</span>
        <button
          v-if="canCallUp(floor)"
          type="button"
          :disabled="store.loading"
          @click="store.callElevator(floor, 'UP')"
        >
          Up
        </button>
        <button
          v-if="canCallDown(floor)"
          type="button"
          :disabled="store.loading"
          @click="store.callElevator(floor, 'DOWN')"
        >
          Down
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
  max-width: 20rem;
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
</style>
