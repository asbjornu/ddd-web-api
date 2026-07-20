<script setup lang="ts">
const store = useElevatorStore()

let poller: ReturnType<typeof setInterval> | undefined

onMounted(() => {
  store.fetchStatus()
  store.fetchCalls()
  poller = setInterval(() => {
    store.fetchStatus()
    store.fetchCalls()
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
    <dl v-else-if="store.status">
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
    </dl>
    <p v-else>Loading...</p>
  </section>
</template>

<style scoped>
.status {
  border: 1px solid #ccc;
  border-radius: 8px;
  padding: 1rem;
  width: 16rem;
  min-height: 16rem;
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
</style>
