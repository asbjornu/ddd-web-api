<script setup lang="ts">
const store = useElevatorStore()

// No hard-coded floor list any more: the form below renders exactly the
// fields the call-elevator operation declares (whichever those turn out
// to be), and disappears entirely once the operation is absent -- see
// docs/architecture.md's "Affordances: hypermedia over the aggregate"
// section. What used to be a button grid sized from BUILDING_FLOORS is
// now a plain form, because the representation only ever carries one
// generic operation, not one per floor -- the rider console is free to
// shape this differently (docs/plan.html section 18), just not yet.
const floor = ref('')
const direction = ref('')

const operation = computed(() => store.callElevatorOperation)

const directionField = computed(() =>
  operation.value?.fields?.find((field) => field.name === 'direction')
)

const directionOptions = computed(() => directionField.value?.options ?? ['up', 'down'])

watch(
  directionOptions,
  (options) => {
    if (!direction.value && options.length > 0) {
      direction.value = options[0] as string
    }
  },
  { immediate: true }
)

function submit() {
  const floorNumber = Number(floor.value)
  if (!Number.isInteger(floorNumber)) return
  store.callElevator(floorNumber, direction.value as 'up' | 'down')
}
</script>

<template>
  <section class="call-panel">
    <h2>Call elevator</h2>
    <p v-if="store.error" class="error">{{ store.error }}</p>
    <p v-if="!operation" class="unavailable">Calling the elevator is not available right now.</p>
    <form v-else class="call-form" @submit.prevent="submit">
      <label>
        Floor
        <input v-model="floor" type="text" inputmode="numeric" required />
      </label>
      <label>
        Direction
        <select v-model="direction">
          <option v-for="option in directionOptions" :key="option" :value="option">
            {{ option }}
          </option>
        </select>
      </label>
      <button type="submit" :disabled="store.loading" :class="{ loading: store.loading }">
        {{ operation.title }}
      </button>
    </form>
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
.unavailable {
  color: #666;
  font-size: 0.85rem;
  margin: 0;
}
.call-form {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}
.call-form label {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.85rem;
  font-weight: bold;
}
.call-form input,
.call-form select {
  padding: 0.35rem 0.4rem;
  font-size: 0.9rem;
  border: 1px solid #999;
  border-radius: 4px;
}
button {
  padding: 0.4rem 0.8rem;
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
button.loading {
  opacity: 0.6;
}
</style>
