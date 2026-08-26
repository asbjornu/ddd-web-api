<template>
  <main>
    <h1>Elevator status</h1>
    <!--
      Public and read-only in spirit, but there is no separate
      "read-only" resource to point at any more than there is a
      hard-coded one to visit instead: this renders the exact same
      representation index.vue does, forms included -- an anonymous
      caller's own operations are whatever elevator-api decided they
      are, the same seam every other caller goes through.
    -->
    <div class="layout">
      <div id="entry-point" data-init="@get('/')"></div>
      <div id="shaft"></div>
    </div>
    <nav>
      <a href="/">Back to the rider console</a>
    </nav>
  </main>
</template>
