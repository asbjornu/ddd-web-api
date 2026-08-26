<template>
  <main>
    <h1>Elevator</h1>
    <p class="tagline">Call it, ride it, and (with the right key) service it.</p>
    <div class="layout">
      <div id="entry-point" data-init="@get('/')"></div>
      <div id="shaft"></div>
    </div>
    <nav>
      <a href="/status">Public status page</a>
    </nav>
  </main>
</template>
