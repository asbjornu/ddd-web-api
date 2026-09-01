<script setup lang="ts">
import { BUILDING_FLOORS } from '~/stores/elevator'
import CarPanel from '~/components/CarPanel.vue'

const store = useElevatorStore()

const FLOOR_HEIGHT = 108

const floors = computed(() =>
  Array.from({ length: BUILDING_FLOORS }, (_, i) => BUILDING_FLOORS - i)
)

const currentFloor = computed(() => store.status?.currentFloor ?? 1)

// The car's position is a requestAnimationFrame tween, exactly like
// crud's own -- the one difference is the seconds-per-floor duration
// comes from the server's own travelSecondsPerFloor (every
// representation carries it, per elevator-api's own
// ElevatorRepresentations Javadoc), never a client-side guessed-
// physics constant. The server reports only the departure floor and
// the destination once, when a trip starts -- see
// docs/architecture.md's "Timing" section -- so there is no per-floor
// telemetry to poll; this is what makes the car's own position
// between those two reports anything other than a jump at arrival.
const animatedFloor = ref(store.status?.currentFloor ?? 1)
let animFrameId: number | null = null
let animTarget = -1

function startCarAnimation(from: number, to: number, travelSecondsPerFloor: number) {
  animTarget = to
  const distance = Math.abs(to - from)
  if (distance === 0) {
    animatedFloor.value = from
    return
  }
  const sign = to > from ? 1 : -1
  const startTime = performance.now()
  const duration = distance * travelSecondsPerFloor * 1000

  function frame() {
    const elapsed = performance.now() - startTime
    const t = Math.min(elapsed / duration, 1)
    animatedFloor.value = from + sign * distance * t
    if (t < 1) {
      animFrameId = requestAnimationFrame(frame)
    } else {
      animFrameId = null
    }
  }
  if (animFrameId) cancelAnimationFrame(animFrameId)
  animFrameId = requestAnimationFrame(frame)
}

watch(
  () => store.status,
  (status) => {
    if (!status) return
    const isMoving = status.state === 'movingUp' || status.state === 'movingDown'

    if (isMoving && status.destinationFloor != null) {
      const from = status.currentFloor
      const to = status.destinationFloor
      if (to !== animTarget) {
        startCarAnimation(from, to, status.travelSecondsPerFloor)
      }
    } else if (!isMoving) {
      if (animFrameId) {
        cancelAnimationFrame(animFrameId)
        animFrameId = null
      }
      animTarget = -1
      animatedFloor.value = status.currentFloor
    }
  }
)

const carBottom = computed(() => (animatedFloor.value - 1) * FLOOR_HEIGHT)

const destinationFloor = computed(() => store.status?.destinationFloor ?? null)

const panelRef = ref<HTMLElement | null>(null)
const panelHeight = ref(FLOOR_HEIGHT)

onMounted(() => {
  if (panelRef.value) {
    panelHeight.value = panelRef.value.offsetHeight
  }
})

const followerBottom = computed(() => {
  return carBottom.value - (panelHeight.value - FLOOR_HEIGHT) / 2
})

const doorStateClass = computed(() => store.status?.doorPosition ?? 'closed')

const directionArrow = computed(() => {
  switch (store.status?.direction) {
    case 'up':
      return '\u25B2'
    case 'down':
      return '\u25BC'
    default:
      return '\u25A0'
  }
})

const isCarOpen = computed(
  () => doorStateClass.value === 'open' || doorStateClass.value === 'closing'
)

const isOutOfService = computed(() => store.status?.state === 'outOfService')

const isEmergency = computed(() => store.status?.state === 'emergencyRecall')
</script>

<template>
  <div class="shaft-wrapper">
    <div class="floor-labels" :style="{ height: `${FLOOR_HEIGHT * BUILDING_FLOORS}px` }">
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
        v-if="destinationFloor != null"
        class="destination-marker"
        :style="{ bottom: `${(destinationFloor - 1) * FLOOR_HEIGHT}px` }"
      />
      <div
        class="car"
        :class="{ 'doors-open': isCarOpen, oos: isOutOfService, emergency: isEmergency }"
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
    <div class="car-panel-track" :style="{ height: `${FLOOR_HEIGHT * BUILDING_FLOORS}px` }">
      <div class="car-panel-follower" :style="{ bottom: `${followerBottom}px` }">
        <div ref="panelRef">
          <CarPanel />
        </div>
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
  width: 3.6rem;
}
.floor-label {
  display: flex;
  align-items: center;
  gap: 7px;
  padding-right: 11px;
  font-size: 1.2rem;
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
  width: 11px;
  height: 11px;
  border-radius: 50%;
  background: #4caf50;
  box-shadow: 0 0 7px rgba(76, 175, 80, 0.5);
  flex-shrink: 0;
}

/* ── Shaft ── */
.shaft {
  position: relative;
  width: 180px;
  background: #e8e8e8;
  border: 2px solid #bbb;
  border-radius: 7px;
  overflow: hidden;
  box-shadow: inset 0 0 11px rgba(0, 0, 0, 0.06);
}
.floor-line {
  position: absolute;
  left: 0;
  right: 0;
  height: 0;
  border-top: 2px solid #ccc;
  transition: border-color 0.3s;
}
.floor-line.active-line {
  border-top: 4px solid #4caf50;
}
.destination-marker {
  position: absolute;
  left: -11px;
  width: calc(100% + 22px);
  height: 0;
  border-top: 4px dashed #ff9800;
  z-index: 1;
  opacity: 0.7;
  transition: bottom 0.6s ease;
}
.destination-marker::before {
  content: '';
  position: absolute;
  top: -10px;
  right: -4px;
  width: 0;
  height: 0;
  border-left: 14px solid transparent;
  border-right: 14px solid transparent;
  border-top: 18px solid #ff9800;
}

/* ── Car ── */
.car {
  position: absolute;
  left: 7px;
  right: 7px;
  display: flex;
  flex-direction: column;
  z-index: 2;
}
.car-roof {
  height: 6px;
  background: linear-gradient(to bottom, #888, #666);
  border-radius: 4px 4px 0 0;
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
  height: 6px;
  background: linear-gradient(to bottom, #666, #555);
  border-radius: 0 0 4px 4px;
  flex-shrink: 0;
}

/* ── Car info overlay ── */
.car-info {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  z-index: 3;
  pointer-events: none;
}
.car-direction {
  color: #fff;
  font-size: 1.4rem;
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
  font-size: 1.1rem;
}
.car-floor {
  color: #fff;
  font-size: 1.3rem;
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
  background: #b0b0b0;
  transition:
    transform 1s ease,
    background 1s ease;
  z-index: 1;
}
.door::before {
  content: '';
  position: absolute;
  top: 20%;
  bottom: 20%;
  width: 4px;
  background: rgba(255, 255, 255, 0.25);
}
.door.left {
  left: 0;
  transform-origin: left center;
  border-right: 1px solid rgba(0, 0, 0, 0.2);
}
.door.left::before {
  right: 11px;
}
.door.right {
  right: 0;
  transform-origin: right center;
  border-left: 1px solid rgba(0, 0, 0, 0.2);
}
.door.right::before {
  left: 11px;
}

/* door states */
.door.open {
  transform: scaleX(0.08);
  background: #333;
}
.door.open::before {
  opacity: 0;
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

/* ── Car panel follower ── */
.car-panel-track {
  position: relative;
  width: 12rem;
  flex-shrink: 0;
  margin-left: 0.75rem;
}
.car-panel-follower {
  position: absolute;
  left: 0;
  right: 0;
}
</style>
