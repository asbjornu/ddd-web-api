<template>
  <main>
    <h1>Elevator</h1>
    <div class="layout">
      <div class="left-col">
        <CallPanel />
        <StatusDisplay />
      </div>
      <ElevatorShaft />
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
.left-col {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}
</style>
