// The pre-refactoring CallPanel/StatusDisplay components, rebuilt from
// the DOM elevator-api already rendered rather than a Pinia store --
// see src/shaft.ts's own comment for why this project builds bespoke
// markup this way instead of styling the generic dl/form output
// directly (main.css hides the generic call-elevator/open-doors/
// close-doors/obstruct-doors/clear-obstruction forms and the generic
// dl once these exist, exactly as CallPanel/StatusDisplay were the
// only rendering of those commands before).
//
// Every button here still submits one of those hidden forms -- its own
// href and fields, never a URL or field name invented here -- so a
// slice that changes an affordance's shape never needs a matching
// change in this file, only in main.css's selector list if the rel
// itself is renamed.

const contentId = 'elevator-content'

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

function formFor(rel: string): HTMLFormElement | null {
  return document.querySelector<HTMLFormElement>(`#${contentId} form[data-rel="${rel}"]`)
}

function submitHiddenForm(rel: string, fields: Record<string, string>) {
  const form = formFor(rel)
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

function buildCallPanel(min: number, max: number): HTMLElement {
  const panel = document.createElement('section')
  panel.className = 'call-panel'
  panel.id = 'call-panel'
  const heading = document.createElement('h2')
  heading.textContent = 'Call elevator'
  panel.append(heading)

  const list = document.createElement('ul')
  for (let floor = max; floor >= min; floor--) {
    const row = document.createElement('li')
    row.className = 'floor-row'
    const label = document.createElement('span')
    label.className = 'floor-label'
    label.textContent = `Floor ${floor}`
    row.append(label)
    if (floor < max) {
      const up = document.createElement('button')
      up.type = 'button'
      up.textContent = '\u25B2'
      up.addEventListener('click', () =>
        submitHiddenForm('call-elevator', { floor: String(floor), direction: 'up' })
      )
      row.append(up)
    }
    if (floor > min) {
      const down = document.createElement('button')
      down.type = 'button'
      down.textContent = '\u25BC'
      down.addEventListener('click', () =>
        submitHiddenForm('call-elevator', { floor: String(floor), direction: 'down' })
      )
      row.append(down)
    }
    list.append(row)
  }
  panel.append(list)
  return panel
}

function buildStatusPanel(): HTMLElement {
  const panel = document.createElement('section')
  panel.className = 'status'
  panel.id = 'status-panel'
  const heading = document.createElement('h2')
  heading.textContent = 'Elevator status'
  panel.append(heading)

  const dl = document.createElement('dl')
  dl.id = 'status-dl'
  panel.append(dl)

  const actions = document.createElement('div')
  actions.className = 'actions'

  const openButton = document.createElement('button')
  openButton.type = 'button'
  openButton.id = 'open-doors-button'
  openButton.textContent = 'Open doors'
  openButton.addEventListener('click', () => submitHiddenForm('open-doors', {}))
  actions.append(openButton)

  const closeButton = document.createElement('button')
  closeButton.type = 'button'
  closeButton.id = 'close-doors-button'
  closeButton.textContent = 'Close doors'
  closeButton.addEventListener('click', () => submitHiddenForm('close-doors', {}))
  actions.append(closeButton)

  const obstructionWarning = document.createElement('p')
  obstructionWarning.className = 'obstruction-warning'
  obstructionWarning.id = 'obstruction-warning'
  obstructionWarning.textContent = 'Doors blocked — cannot close'
  obstructionWarning.hidden = true
  actions.append(obstructionWarning)

  const toggleLabel = document.createElement('label')
  toggleLabel.className = 'obstruction-toggle'
  const toggleInput = document.createElement('input')
  toggleInput.type = 'checkbox'
  toggleInput.id = 'obstruction-toggle'
  toggleInput.addEventListener('change', () => {
    if (toggleInput.checked) {
      submitHiddenForm('obstruct-doors', {})
    } else {
      submitHiddenForm('clear-obstruction', {})
    }
  })
  toggleLabel.append(toggleInput, document.createTextNode('Obstruction'))
  actions.append(toggleLabel)

  panel.append(actions)

  const divider = document.createElement('hr')
  divider.className = 'divider'
  panel.append(divider)

  const techSection = document.createElement('div')
  techSection.className = 'tech-section'
  techSection.id = 'tech-section'
  panel.append(techSection)

  return panel
}

// The technician section's own shape depends on which of insert-key/
// withdraw-key/enter-maintenance/exit-maintenance/trigger-emergency-
// recall currently exist -- "absence is refusal" (see
// docs/architecture.md's "Key-switch and authorization" section)
// stands in for the pre-refactoring store's own technicianKeyInserted
// flag, and is the more faithful signal of the two: it can never drift
// from what the server would actually accept.
function renderTechSection(section: HTMLElement) {
  const withdrawForm = formFor('withdraw-key')
  const keyInserted = withdrawForm !== null
  const shape = keyInserted ? 'inserted' : 'key-form'
  if (section.dataset.shape === shape) {
    return
  }
  section.replaceChildren()
  section.dataset.shape = shape

  if (!keyInserted) {
    const form = document.createElement('form')
    form.className = 'key-form'
    const input = document.createElement('input')
    input.type = 'password'
    input.className = 'key-input'
    input.placeholder = 'Technician key'
    input.autocomplete = 'off'
    input.setAttribute('aria-label', 'Technician key')
    const submit = document.createElement('button')
    submit.type = 'submit'
    submit.textContent = 'Insert key'
    form.append(input, submit)
    form.addEventListener('submit', (event) => {
      event.preventDefault()
      submitHiddenForm('insert-key', { secret: input.value })
    })
    section.append(form)
    return
  }

  const inserted = document.createElement('div')
  inserted.className = 'key-inserted'
  const label = document.createElement('span')
  label.textContent = 'Key inserted'
  const withdrawButton = document.createElement('button')
  withdrawButton.type = 'button'
  withdrawButton.textContent = 'Withdraw key'
  withdrawButton.addEventListener('click', () => submitHiddenForm('withdraw-key', {}))
  inserted.append(label, withdrawButton)
  section.append(inserted)

  const techActions = document.createElement('div')
  techActions.className = 'tech-actions'

  const maintenanceButton = document.createElement('button')
  maintenanceButton.type = 'button'
  const enterMaintenance = formFor('enter-maintenance') !== null
  const exitMaintenance = formFor('exit-maintenance') !== null
  maintenanceButton.textContent = enterMaintenance ? 'Enter maintenance' : 'Exit maintenance'
  // Neither exists mid emergencyRecall (pre-empts everything else,
  // including a technician's own maintenance transition) -- a state
  // the pre-refactoring original never had to account for.
  maintenanceButton.disabled = !enterMaintenance && !exitMaintenance
  maintenanceButton.addEventListener('click', () =>
    // Read fresh at click time, never the enterMaintenance captured
    // above: update() keeps this button's own text/disabled state
    // live without rebuilding it (rebuilding would lose focus/mid-
    // interaction state), so by the time this fires the state that
    // built it may no longer be current.
    submitHiddenForm(formFor('enter-maintenance') ? 'enter-maintenance' : 'exit-maintenance', {})
  )
  techActions.append(maintenanceButton)

  const emergencyButton = document.createElement('button')
  emergencyButton.type = 'button'
  emergencyButton.className = 'emergency-btn'
  emergencyButton.textContent = 'Emergency recall'
  emergencyButton.addEventListener('click', () => submitHiddenForm('trigger-emergency-recall', {}))
  techActions.append(emergencyButton)

  section.append(techActions)
}

function update() {
  const content = document.getElementById(contentId)
  if (!content) {
    return
  }
  const range = floorRange()
  if (range && !document.getElementById('call-panel')) {
    content.prepend(buildCallPanel(range[0], range[1]))
  }
  if (!document.getElementById('status-panel')) {
    const callPanel = document.getElementById('call-panel')
    const statusPanel = buildStatusPanel()
    if (callPanel) {
      callPanel.after(statusPanel)
    } else {
      content.prepend(statusPanel)
    }
  }

  const dl = document.getElementById('status-dl')
  if (dl) {
    const rows: [string, string | null][] = [
      ['Current floor', fieldValue('currentFloor')],
      ['State', fieldValue('state')],
      ['Direction', fieldValue('direction')],
      ['Doors', fieldValue('doorPosition')],
      [
        'Target floor',
        fieldValue('destinationFloor') === 'null' ? '—' : fieldValue('destinationFloor')
      ]
      // No "Pending calls"/"Pending floors" rows: the pre-refactoring
      // read model tracked which floors had a pending call, and this
      // one doesn't -- restoring the look of a feature needs the
      // feature's own data back first, not a placeholder. Weight is
      // shown in CarPanel instead (its own pre-refactoring position),
      // and obstruction only ever showed as the checkbox's own state,
      // never a dl row of its own.
    ]
    const text = rows.map(([, value]) => value).join('|')
    if (dl.dataset.rendered !== text) {
      dl.replaceChildren()
      for (const [label, value] of rows) {
        const dt = document.createElement('dt')
        dt.textContent = label
        const dd = document.createElement('dd')
        dd.textContent = value ?? ''
        dl.append(dt, dd)
      }
      dl.dataset.rendered = text
    }
  }

  const openButton = document.getElementById('open-doors-button') as HTMLButtonElement | null
  if (openButton) {
    openButton.disabled = !formFor('open-doors')
  }
  const closeButton = document.getElementById('close-doors-button') as HTMLButtonElement | null
  if (closeButton) {
    closeButton.disabled = !formFor('close-doors')
  }
  const obstructed = fieldValue('obstructed') === 'true'
  const warning = document.getElementById('obstruction-warning')
  if (warning) {
    warning.hidden = !obstructed
  }
  const toggle = document.getElementById('obstruction-toggle') as HTMLInputElement | null
  if (toggle) {
    // obstruct-doors only exists while the doors are actually closing
    // (there is nothing to obstruct otherwise), and clear-obstruction
    // only once obstructed -- the checkbox can only ever do one or the
    // other, never both at once, so its own enabled state mirrors
    // whichever of the two the current state actually offers.
    toggle.disabled = obstructed ? !formFor('clear-obstruction') : !formFor('obstruct-doors')
    if (document.activeElement !== toggle) {
      toggle.checked = obstructed
    }
  }

  const techSection = document.getElementById('tech-section')
  if (techSection) {
    renderTechSection(techSection)
    const maintenanceButton = techSection.querySelector<HTMLButtonElement>(
      '.tech-actions button:first-child'
    )
    if (maintenanceButton) {
      const enterMaintenance = formFor('enter-maintenance') !== null
      const exitMaintenance = formFor('exit-maintenance') !== null
      const text = enterMaintenance ? 'Enter maintenance' : 'Exit maintenance'
      if (maintenanceButton.textContent !== text) {
        maintenanceButton.textContent = text
      }
      maintenanceButton.disabled = !enterMaintenance && !exitMaintenance
    }
    const emergencyButton = techSection.querySelector<HTMLButtonElement>('.emergency-btn')
    if (emergencyButton) {
      emergencyButton.disabled = !formFor('trigger-emergency-recall')
    }
  }
}

// Same reasoning as src/shaft.ts's own watch(): observe document.body
// itself, since a live SSE patch or a command's own response replaces
// #elevator-content outright.
function watch() {
  new MutationObserver(update).observe(document.body, {
    childList: true,
    subtree: true,
    characterData: true
  })
  update()
}

watch()

// Forces module scope (no import/export otherwise), so this file's
// top-level declarations don't collide with the other two -- each is
// loaded as its own <script type="module">, genuinely isolated at
// runtime already; this only affects how tsc analyses them together.
export {}
