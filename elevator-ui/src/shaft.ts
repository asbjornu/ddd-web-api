// Purely decorative: the shaft/car/CarPanel visualisation looks
// exactly like the pre-refactoring ElevatorShaft.vue component in its
// final form (FLOOR_HEIGHT 108px, a 180px shaft, CarPanel riding beside
// the car -- see public/main.css's own comment) but is built and kept
// live from data elevator-api already rendered into the DOM (the
// status dl's own currentFloor/direction/doorPosition/
// destinationFloor/state/travelSecondsPerFloor fields, and the floor
// range already present in any select[name=floor] a command form
// rendered) -- never a constant of its own, per AGENTS.md's "may not
// hard-code ... floor count, travel timing". There is no client-side
// state here: every call below re-derives its answer from the live
// DOM, the same DOM Datastar itself morphs on every SSE patch or
// command response, so this never needs to be told when something
// changed -- it just reads whatever is there right now and
// rebuilds/updates the shaft to match.
//
// CarPanel's own buttons/slider don't invent a URL or a command of
// their own: they set the hidden select-floor/report-load forms'
// own field values (whichever href/fields elevator-api already
// rendered) and call requestSubmit() on them, exactly the same
// Datastar-driven submission a visible click on those forms would
// have triggered.

const contentId = 'elevator-content'
const FLOOR_HEIGHT = 108

function fieldValue(name: string): string | null {
  const dt = Array.from(document.querySelectorAll(`#${contentId} dt`)).find(
    (candidate) => candidate.textContent === name
  )
  const dd = dt?.nextElementSibling
  return dd instanceof HTMLElement ? dd.textContent : null
}

function floorRange(): [number, number] | null {
  const options = Array.from(document.querySelectorAll(`#${contentId} select[name="floor"] option`))
  const values = options
    .map((option) => Number((option as HTMLOptionElement).value))
    .filter((value) => !Number.isNaN(value))
  return values.length ? [Math.min(...values), Math.max(...values)] : null
}

function directionArrow(direction: string | null): string {
  if (direction === 'up') return '\u25B2'
  if (direction === 'down') return '\u25BC'
  return '\u25A0'
}

// The server reports only the departure floor and the destination,
// once, when a trip starts -- there is no per-floor telemetry to poll
// (see docs/architecture.md's "Timing" section: transitions are
// scheduled forward, not derived from elapsed time). The
// pre-refactoring original animated the car itself with its own
// requestAnimationFrame loop rather than waiting for the server to
// narrate every floor in between, and this does the same: an
// unanimated position jump is exactly what it looks like without it.
let animatedFloor = 1
let animFrameId: number | null = null
let animTarget = -1

function startCarAnimation(from: number, to: number, travelSecondsPerFloor: number) {
  animTarget = to
  const distance = Math.abs(to - from)
  if (animFrameId !== null) {
    cancelAnimationFrame(animFrameId)
    animFrameId = null
  }
  if (distance === 0) {
    animatedFloor = from
    return
  }
  const sign = to > from ? 1 : -1
  const startTime = performance.now()
  const duration = distance * travelSecondsPerFloor * 1000

  function frame() {
    const elapsed = performance.now() - startTime
    const t = Math.min(elapsed / duration, 1)
    animatedFloor = from + sign * distance * t
    positionCar()
    animFrameId = t < 1 ? requestAnimationFrame(frame) : null
  }
  animFrameId = requestAnimationFrame(frame)
}

// Applies animatedFloor to the car and its CarPanel follower --
// called every animation frame while moving, and once more whenever
// update() runs for any other reason (a door state change, say),
// so neither ever renders a stale position.
function positionCar() {
  const shaft = document.getElementById('shaft')
  const range = floorRange()
  const car = shaft?.querySelector<HTMLElement>('.car')
  if (!shaft || !range || !car) {
    return
  }
  const [min] = range
  const carBottom = (animatedFloor - min) * FLOOR_HEIGHT
  car.style.bottom = `${carBottom}px`

  const panel = shaft.querySelector<HTMLElement>('#car-panel')
  const follower = shaft.querySelector<HTMLElement>('#car-panel-follower')
  if (panel && follower) {
    const panelHeight = panel.offsetHeight || FLOOR_HEIGHT
    follower.style.bottom = `${carBottom - (panelHeight - FLOOR_HEIGHT) / 2}px`
  }
}

function submitHiddenForm(rel: string, fields: Record<string, string>) {
  const form = document.querySelector<HTMLFormElement>(`#${contentId} form[data-rel="${rel}"]`)
  if (!form) {
    return
  }
  for (const [name, value] of Object.entries(fields)) {
    const field = form.elements.namedItem(name)
    if (field instanceof HTMLInputElement || field instanceof HTMLSelectElement) {
      field.value = value
    }
  }
  form.requestSubmit()
}

// Built once per floor range (it never changes at runtime): the
// static parts of the shaft -- labels, floor lines, and the car
// panel's own floor grid -- plus the car itself, all left in place
// afterward and only adjusted, never rebuilt, so the car's own CSS
// transition has something stable to animate between.
function build(shaft: HTMLElement, min: number, max: number): void {
  const floors = []
  for (let floor = max; floor >= min; floor--) {
    floors.push(floor)
  }
  const height = (max - min + 1) * FLOOR_HEIGHT

  const wrapper = document.createElement('div')
  wrapper.className = 'shaft-wrapper'

  const labels = document.createElement('div')
  labels.className = 'floor-labels'
  labels.style.height = `${height}px`
  for (const floor of floors) {
    const label = document.createElement('div')
    label.className = 'floor-label'
    label.dataset.floor = String(floor)
    label.style.height = `${FLOOR_HEIGHT}px`
    const num = document.createElement('span')
    num.className = 'floor-num'
    num.textContent = String(floor)
    const dot = document.createElement('span')
    dot.className = 'floor-dot'
    dot.hidden = true
    label.append(num, dot)
    labels.append(label)
  }

  const track = document.createElement('div')
  track.className = 'shaft'
  track.style.height = `${height}px`

  for (const floor of floors) {
    const line = document.createElement('div')
    line.className = 'floor-line'
    line.dataset.floor = String(floor)
    line.style.bottom = `${(floor - min) * FLOOR_HEIGHT}px`
    track.append(line)
  }

  const marker = document.createElement('div')
  marker.className = 'target-marker'
  marker.id = 'target-marker'
  marker.hidden = true
  track.append(marker)

  const car = document.createElement('div')
  car.className = 'car'
  car.id = 'car'
  car.style.height = `${FLOOR_HEIGHT}px`
  car.innerHTML =
    '<div class="car-roof"></div>' +
    '<div class="car-body">' +
    '<div class="car-info">' +
    '<span class="car-direction" id="car-direction"></span>' +
    '<span class="car-floor" id="car-floor"></span>' +
    '</div>' +
    '<div class="door left" id="door-left"></div>' +
    '<div class="door right" id="door-right"></div>' +
    '</div>' +
    '<div class="car-floor-bar"></div>'
  track.append(car)

  const panelTrack = document.createElement('div')
  panelTrack.className = 'car-panel-track'
  panelTrack.style.height = `${height}px`

  const follower = document.createElement('div')
  follower.className = 'car-panel-follower'
  follower.id = 'car-panel-follower'

  const panel = document.createElement('div')
  panel.className = 'car-panel'
  panel.id = 'car-panel'

  const heading = document.createElement('h2')
  heading.textContent = 'Select floor'
  panel.append(heading)

  const grid = document.createElement('div')
  grid.className = 'grid'
  for (const floor of floors) {
    const button = document.createElement('button')
    button.type = 'button'
    button.textContent = String(floor)
    button.dataset.floor = String(floor)
    button.addEventListener('click', () => {
      submitHiddenForm('select-floor', { floor: String(floor) })
    })
    grid.append(button)
  }
  panel.append(grid)

  const weightSection = document.createElement('div')
  weightSection.className = 'weight-section'
  const weightLabel = document.createElement('label')
  const weightText = document.createElement('span')
  weightText.id = 'weight-text'
  const weightInput = document.createElement('input')
  weightInput.type = 'range'
  weightInput.min = '0'
  weightInput.id = 'weight-input'
  weightInput.addEventListener('input', () => {
    submitHiddenForm('report-load', { weightKg: weightInput.value })
  })
  weightLabel.append(weightText, weightInput)
  weightSection.append(weightLabel)
  const overloadWarning = document.createElement('p')
  overloadWarning.className = 'overload-warning'
  overloadWarning.id = 'overload-warning'
  overloadWarning.textContent = 'Overloaded — reduce weight to close doors'
  overloadWarning.hidden = true
  weightSection.append(overloadWarning)
  panel.append(weightSection)

  follower.append(panel)
  panelTrack.append(follower)

  wrapper.append(labels, track, panelTrack)
  shaft.replaceChildren(wrapper)
}

function update() {
  const shaft = document.getElementById('shaft')
  const range = floorRange()
  const currentFloor = Number(fieldValue('currentFloor'))
  if (!shaft || !range || Number.isNaN(currentFloor)) {
    return
  }
  const [min, max] = range

  if (!shaft.querySelector('.car')) {
    build(shaft, min, max)
  }

  const car = shaft.querySelector<HTMLElement>('.car')
  if (!car) {
    return
  }

  const travelSeconds = Number(fieldValue('travelSecondsPerFloor'))
  const travelDuration = Number.isNaN(travelSeconds) ? null : `${travelSeconds}s`
  if (travelDuration) {
    shaft
      .querySelectorAll<HTMLElement>('.target-marker')
      .forEach((el) => (el.style.transitionDuration = travelDuration))
  }

  const state = fieldValue('state')
  const destinationFieldRaw = fieldValue('destinationFloor')
  const destinationFloor =
    destinationFieldRaw === null || destinationFieldRaw === 'null'
      ? null
      : Number(destinationFieldRaw)
  const isMoving = state === 'movingUp' || state === 'movingDown'

  if (isMoving && destinationFloor !== null) {
    if (destinationFloor !== animTarget && !Number.isNaN(travelSeconds)) {
      startCarAnimation(currentFloor, destinationFloor, travelSeconds)
    }
  } else if (animFrameId !== null || animTarget !== -1) {
    cancelAnimationFrame(animFrameId ?? 0)
    animFrameId = null
    animTarget = -1
    animatedFloor = currentFloor
  } else {
    animatedFloor = currentFloor
  }
  positionCar()

  car.classList.toggle('oos', state === 'outOfService')
  car.classList.toggle('emergency', state === 'emergencyRecall')
  car.classList.toggle('doors-open', state === 'doorsOpen' || state === 'doorsClosing')

  const direction = fieldValue('direction')
  const directionEl = car.querySelector<HTMLElement>('#car-direction')
  if (directionEl) {
    // Guarded: the MutationObserver below watches childList/
    // characterData on document.body, and textContent always fires
    // one of those even when the value doesn't change -- an
    // unconditional write here reliably retriggers update() on every
    // single mutation callback, forever, pegging a CPU core solid.
    const arrow = directionArrow(direction)
    if (directionEl.textContent !== arrow) {
      directionEl.textContent = arrow
    }
    directionEl.className = `car-direction ${direction ?? 'none'}`
  }
  const floorEl = car.querySelector<HTMLElement>('#car-floor')
  if (floorEl && floorEl.textContent !== String(currentFloor)) {
    floorEl.textContent = String(currentFloor)
  }

  const doorPosition = fieldValue('doorPosition') ?? 'closed'
  const leftDoor = car.querySelector<HTMLElement>('#door-left')
  const rightDoor = car.querySelector<HTMLElement>('#door-right')
  for (const door of [leftDoor, rightDoor]) {
    door?.classList.remove('open', 'closed')
    // No .door.closing style exists (see main.css's own comment): a
    // door that is "closing" simply keeps looking closed until it
    // truly is.
    door?.classList.add(doorPosition === 'open' ? 'open' : 'closed')
  }

  for (const label of shaft.querySelectorAll<HTMLElement>('.floor-label')) {
    const isCurrent = Number(label.dataset.floor) === currentFloor
    label.classList.toggle('current', isCurrent)
    const dot = label.querySelector<HTMLElement>('.floor-dot')
    if (dot) {
      dot.hidden = !isCurrent
    }
  }
  for (const line of shaft.querySelectorAll<HTMLElement>('.floor-line')) {
    line.classList.toggle('active-line', Number(line.dataset.floor) === currentFloor)
  }

  const marker = shaft.querySelector<HTMLElement>('.target-marker')
  if (marker) {
    marker.hidden = destinationFloor === null
    if (destinationFloor !== null) {
      marker.style.bottom = `${(destinationFloor - min) * FLOOR_HEIGHT}px`
    }
  }

  // CarPanel: follows the car vertically, centred on it, since the
  // panel is taller than one floor row -- positionCar() already
  // placed it this update, using animatedFloor rather than
  // currentFloor, so it tracks the car's own in-flight position
  // during a trip instead of jumping only at arrival.
  const panel = shaft.querySelector<HTMLElement>('#car-panel')
  if (panel) {
    for (const button of panel.querySelectorAll<HTMLButtonElement>('.grid button')) {
      const floor = Number(button.dataset.floor)
      button.classList.toggle('current', floor === currentFloor)
      button.disabled = floor === currentFloor
    }

    const weightKg = Number(fieldValue('weightKg'))
    const capacityKg = Number(fieldValue('capacityKg'))
    const weightInput = panel.querySelector<HTMLInputElement>('#weight-input')
    const weightText = panel.querySelector<HTMLElement>('#weight-text')
    const overloadWarning = panel.querySelector<HTMLElement>('#overload-warning')
    if (weightInput && !Number.isNaN(capacityKg)) {
      weightInput.max = String(capacityKg + 200)
      weightInput.disabled = doorPosition !== 'open'
      if (document.activeElement !== weightInput && !Number.isNaN(weightKg)) {
        weightInput.value = String(weightKg)
      }
    }
    if (weightText && !Number.isNaN(weightKg) && !Number.isNaN(capacityKg)) {
      const text = `Weight: ${weightKg} / ${capacityKg} kg`
      if (weightText.textContent !== text) {
        weightText.textContent = text
      }
    }
    if (overloadWarning) {
      overloadWarning.hidden = !(weightKg > capacityKg)
    }
  }
}

// #elevator-content doesn't exist until Datastar's own discovery
// chain (entry point -> elevators collection -> this elevator) has
// fetched it, and a live SSE patch or a command's own response later
// replaces that whole div outright (a fresh node, not merely its
// children) rather than patching it in place -- so this observes
// document.body itself, once, rather than #elevator-content directly:
// an observer attached to a node that later gets replaced stops
// firing for whatever replaced it.
function watch() {
  new MutationObserver(update).observe(document.body, {
    childList: true,
    subtree: true,
    characterData: true
  })
  update()
}

// No Vue/hydration to wait for any more (this script is a plain
// <script type="module"> at the end of body, so #shaft already exists
// by the time it runs) -- see this repository's own history for the
// nuxtApp.hook('app:mounted', ...) this line replaced, kept around
// solely to avoid racing Vue's hydration walk of the same node.
watch()

// Forces module scope (no import/export otherwise), so this file's
// top-level declarations don't collide with the other two -- each is
// loaded as its own <script type="module">, genuinely isolated at
// runtime already; this only affects how tsc analyses them together.
export {}
