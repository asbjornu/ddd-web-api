<script setup lang="ts">
import { BUILDING_FLOORS } from '~/stores/elevator'

const store = useElevatorStore()

const FLOOR_HEIGHT = 60

const floors = computed(() =>
  Array.from({ length: BUILDING_FLOORS }, (_, i) => BUILDING_FLOORS - i)
)

const currentFloor = computed(() => store.status?.currentFloor ?? 1)

const carBottom = computed(() => (currentFloor.value - 1) * FLOOR_HEIGHT)

const doorStateClass = computed(() => {
  const doorState = store.status?.doorState ?? 'CLOSED'
  return doorState.toLowerCase()
})

const directionArrow = computed(() => {
  switch (store.status?.direction) {
    case 'UP': return '\u25B2'
    case 'DOWN': return '\u25BC'
    default: return '\u25A0'
  }
})

const isCarOpen = computed(() =>
  store.status?.doorState === 'OPEN' || store.status?.doorState === 'CLOSING'
)

const targetFloor = computed(() => store.status?.targetFloor)

const isOutOfService = computed(() =>
  store.status?.state === 'OUT_OF_SERVICE'
)

const isEmergency = computed(() =>
  store.status?.state === 'EMERGENCY_RECALL'
)
</script>

<template>
  <div class="shaft-wrapper">
      <div
        class="floor-labels"
        :style="{ height: `${FLOOR_HEIGHT * BUILDING_FLOORS}px` }"
      >
        <div
          v-for="floor in floors"
          :key="floor"
          class="floor-label"
          :style="{ height: `${FLOOR_HEIGHT}px` }"
          :class="{ current: floor === currentFloor }"
        >
          <span class="floor-num">{{ floor }}</span>
          <span v-if="floor === currentFloor" class="floor-dot" />
        </div>
      </div>
    <div class="shaft" :style="{ height: `${FLOOR_HEIGHT * BUILDING_FLOORS}px` }">
      <div
        v-for="floor in floors"
        :key="'line-' + floor"
        class="floor-line"
        :class="{ 'active-line': floor === currentFloor }"
        :style="{ bottom: `${(floor - 1) * FLOOR_HEIGHT}px` }"
      />
      <div
        v-if="targetFloor != null"
        class="target-marker"
        :style="{ bottom: `${(targetFloor - 1) * FLOOR_HEIGHT}px` }"
      />
      <div
        class="car"
        :class="{ 'doors-open': isCarOpen, 'oos': isOutOfService, 'emergency': isEmergency }"
        :style="{ height: `${FLOOR_HEIGHT}px`, bottom: `${carBottom}px` }"
      >
        <div class="car-roof" />
        <div class="car-body">
          <div class="car-info">
            <span class="car-direction" :class="(store.status?.direction ?? '').toLowerCase()">
              {{ directionArrow }}
            </span>
            <span class="car-floor">{{ currentFloor }}</span>
          </div>
          <div class="door left" :class="doorStateClass" />
          <div class="door right" :class="doorStateClass" />
        </div>
        <div class="car-floor-bar" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.shaft-wrapper {
  display: flex;
  gap: 0;
  font-family: system-ui, sans-serif;
  user-select: none;
}

/* ── Floor labels ── */
.floor-labels {
  display: grid;
  grid-template-rows: repeat(9, 1fr);
  flex-shrink: 0;
  width: 2rem;
}
.floor-label {
  display: flex;
  align-items: center;
  gap: 4px;
  padding-right: 6px;
  font-size: 0.75rem;
  font-weight: 600;
  color: #888;
  letter-spacing: 0.02em;
  transition: color 0.3s;
}
.floor-label.current {
  color: #111;
}
.floor-num {
  width: 1ch;
  text-align: right;
}
.floor-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #4caf50;
  box-shadow: 0 0 4px rgba(76, 175, 80, 0.5);
  flex-shrink: 0;
}

/* ── Shaft ── */
.shaft {
  position: relative;
  width: 100px;
  background: #e8e8e8;
  border: 1px solid #bbb;
  border-radius: 4px;
  overflow: hidden;
  box-shadow: inset 0 0 6px rgba(0, 0, 0, 0.06);
}
.floor-line {
  position: absolute;
  left: 0;
  right: 0;
  height: 0;
  border-top: 1px solid #ccc;
  transition: border-color 0.3s;
}
.floor-line.active-line {
  border-top: 2px solid #4caf50;
}
.target-marker {
  position: absolute;
  left: -6px;
  width: calc(100% + 12px);
  height: 0;
  border-top: 2px dashed #ff9800;
  z-index: 1;
  opacity: 0.7;
  transition: bottom 1.5s linear;
}
.target-marker::before {
  content: '';
  position: absolute;
  top: -5px;
  right: -2px;
  width: 0;
  height: 0;
  border-left: 8px solid transparent;
  border-right: 8px solid transparent;
  border-top: 10px solid #ff9800;
}

/* ── Car ── */
.car {
  position: absolute;
  left: 4px;
  right: 4px;
  display: flex;
  flex-direction: column;
  transition: bottom 1.5s linear;
  z-index: 2;
}
.car-roof {
  height: 3px;
  background: linear-gradient(to bottom, #888, #666);
  border-radius: 2px 2px 0 0;
  flex-shrink: 0;
}
.car-body {
  flex: 1;
  position: relative;
  display: flex;
  background: #444;
  overflow: hidden;
}
.car-floor-bar {
  height: 3px;
  background: linear-gradient(to bottom, #666, #555);
  border-radius: 0 0 2px 2px;
  flex-shrink: 0;
}

/* ── Car info overlay ── */
.car-info {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  z-index: 3;
  pointer-events: none;
}
.car-direction {
  color: #fff;
  font-size: 1rem;
  line-height: 1;
  filter: drop-shadow(0 1px 2px rgba(0, 0, 0, 0.4));
  transition: color 0.3s;
}
.car-direction.up {
  color: #4caf50;
}
.car-direction.down {
  color: #ff9800;
}
.car-direction.none {
  color: #aaa;
  font-size: 0.7rem;
}
.car-floor {
  color: #fff;
  font-size: 0.9rem;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  filter: drop-shadow(0 1px 2px rgba(0, 0, 0, 0.4));
}

/* ── Doors ── */
.door {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 50%;
  background: linear-gradient(135deg, #b0b0b0 0%, #909090 50%, #b0b0b0 100%);
  transition: transform 0.35s ease;
  z-index: 1;
}
.door::before {
  content: '';
  position: absolute;
  top: 20%;
  bottom: 20%;
  width: 2px;
  background: rgba(255, 255, 255, 0.25);
}
.door.left {
  left: 0;
  transform-origin: left center;
  border-right: 1px solid rgba(0, 0, 0, 0.2);
}
.door.left::before {
  right: 6px;
}
.door.right {
  right: 0;
  transform-origin: right center;
  border-left: 1px solid rgba(0, 0, 0, 0.2);
}
.door.right::before {
  left: 6px;
}

/* door states */
.door.open {
  transform: scaleX(0.08);
  background: #333;
}
.door.open::before {
  opacity: 0;
}
.door.closing {
  transform: scaleX(0.55);
  background: linear-gradient(135deg, #999 0%, #808080 50%, #999 100%);
}
.door.closed {
  transform: scaleX(1);
}

/* ── Special car states ── */
.car.oos .car-body {
  background: #8b0000;
}
.car.oos .car-direction {
  color: #ff4444;
}
.car.emergency .car-body {
  background: #b8860b;
}
.car.emergency .car-direction {
  color: #ffd700;
}
.car.doors-open .car-body {
  background: #2a2a2a;
}
</style>
