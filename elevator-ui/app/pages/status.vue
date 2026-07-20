<template>
  <main>
    <h1>Elevator status</h1>
    <p>Public, read-only -- no key required.</p>
    <div class="layout">
      <ElevatorShaft />
      <StatusDisplay />
    </div>
  </main>
</template>

<style scoped>
main {
  max-width: 30rem;
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
