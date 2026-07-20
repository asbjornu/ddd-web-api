<template>
  <main>
    <h1>Elevator</h1>
    <div class="panels">
      <StatusDisplay />
      <CallPanel />
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
.panels {
  display: flex;
  flex-wrap: wrap;
  gap: 1rem;
}
</style>
