<script setup lang="ts">
const store = useElevatorStore()

let poller: ReturnType<typeof setInterval> | undefined

onMounted(() => {
  store.fetchStatus()
  poller = setInterval(() => store.fetchStatus(), 1500)
})

onUnmounted(() => {
  if (poller) clearInterval(poller)
})
</script>

<template>
  <section class="status">
    <h2>Elevator status</h2>
    <p v-if="store.error" class="error">{{ store.error }}</p>
    <dl v-else-if="store.status">
      <dt>Current floor</dt>
      <dd>{{ store.status.currentFloor }}</dd>

      <dt>State</dt>
      <dd>{{ store.status.state }}</dd>

      <dt>Direction</dt>
      <dd>{{ store.status.direction }}</dd>

      <dt>Doors</dt>
      <dd>{{ store.status.doorState }}</dd>
    </dl>
    <p v-else>Loading...</p>
  </section>
</template>

<style scoped>
.status {
  border: 1px solid #ccc;
  border-radius: 8px;
  padding: 1rem;
  max-width: 20rem;
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
</style>
