<template>
  <main>
    <h1>Elevator</h1>
    <div class="layout">
      <CallPanel />
      <ElevatorShaft />
      <StatusDisplay />
    </div>
    <p>
      <NuxtLink to="/status">Public status page</NuxtLink>
    </p>
  </main>
</template>

<style scoped>
main {
  max-width: 45rem;
  margin: 2rem auto;
  padding: 0 1rem;
  font-family: system-ui, sans-serif;
}
.layout {
  display: flex;
  gap: 2rem;
  align-items: flex-start;
}
</style>
