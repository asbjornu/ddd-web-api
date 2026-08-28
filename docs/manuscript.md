# Domain-driven web APIs

---

## 001 — Opening

```http
GET /presentation HTTP/1.1
Host: asbjornu.no
Accept: application/json
```

Pause.

---

## 002

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "title": "Domain-driven web APIs",
  "speaker": "Asbjørn Ulsberg",
  "topic": "What happens when the API understands the product?"
}
```

---

## 003

Hello.

I'm Asbjørn.

And this talk is about APIs.

But it's really about something else.

It's about **knowledge**.

Where it lives.

Who owns it.

And what happens when we put it in the wrong place.

---

## 004

We are entering a slightly strange period in software development.

Because code is becoming cheaper.

---

## 005

AI can generate code.

A lot of code.

Sometimes even code that works.

---

## 006

It can generate:

controllers.

Repositories.

DTOs.

TypeScript clients.

React components.

Tests.

Database migrations.

YAML.

So much YAML.

---

## 007

And this changes something.

Not because software development disappears.

But because the relative cost of different activities changes.

---

## 008

If producing code becomes cheaper...

what remains expensive?

---

## 009

Understanding the domain.

Discovering workflows.

Understanding boundaries.

Finding the right language.

Finding the rules.

Understanding the people using the system.

Understanding what the product actually **does**.

---

## 010

And if that understanding becomes the expensive part...

we should probably be quite careful about where we put it.

---

# `<before>`

---

## 011 — Meet the elevator

I'm going to demonstrate this with an elevator.

Nine floors.

One elevator.

Nothing particularly exciting.

---

## 012

There are two kinds of users.

A rider.

And a technician.

---

## 013

A rider can:

call the elevator,

select a floor,

open and close the doors.

---

## 014

A technician can do a little more.

Enter maintenance.

Exit maintenance.

Trigger emergency recall.

---

## 015

And the elevator itself does things over time.

It moves.

It reaches floors.

Doors close.

Doors open.

Loads change.

Things happen even when nobody sends an HTTP request.

Remember that.

---

## 016 — Before architecture

Here's where we started.

```text
Browser
   ↓
Vue
   ↓
Pinia
   ↓
Nitro BFF
   ↓
Spring controllers
   ↓
ElevatorService
   ↓
JPA
   ↓
H2
```

This is not particularly unusual.

---

## 017

The API looks REST-ish.

We have resources.

We use HTTP.

We send JSON.

---

## 018

For example:

```http
GET /api/elevators/1 HTTP/1.1
```

---

## 019

And we get:

```json
{
  "id": 1,
  "floor": 3,
  "direction": "NONE",
  "doorOpen": false,
  "doorObstructed": false,
  "maintenance": false,
  "emergencyRecall": false,
  "load": 240
}
```

Perfectly reasonable.

---

## 020

Now I want to call the elevator.

```http
POST /api/elevators/1/calls HTTP/1.1
Content-Type: application/json

{
  "floor": 7
}
```

---

## 021

Select a floor.

```http
POST /api/elevators/1/destination HTTP/1.1
Content-Type: application/json

{
  "floor": 2
}
```

---

## 022

Open the doors.

```http
PUT /api/elevators/1/doors HTTP/1.1
Content-Type: application/json

{
  "open": true
}
```

---

## 023

Close them.

```json
{
  "open": false
}
```

---

## 024

Maintenance?

```http
PATCH /api/elevators/1 HTTP/1.1
Content-Type: application/json

{
  "maintenance": true
}
```

---

## 025

Exit maintenance?

```json
{
  "maintenance": false
}
```

---

## 026

Obstruct the doors?

```json
{
  "doorObstructed": true
}
```

Clear the obstruction?

```json
{
  "doorObstructed": false
}
```

---

## 027

You can see the pattern.

---

## 028

The API exposes **state**.

The client manipulates that state.

---

## 029

Which is basically CRUD.

---

## 030

And CRUD is extremely attractive.

It's easy to understand.

Easy to generate.

Easy to scaffold.

Easy to document.

Easy to put into OpenAPI.

Easy for AI to produce.

---

## 031

Table:

```text
Elevator
```

becomes:

```text
GET    /elevators/{id}
POST   /elevators
PUT    /elevators/{id}
PATCH  /elevators/{id}
DELETE /elevators/{id}
```

Done.

API designed.

---

## 032

Except...

what does:

```json
{
  "maintenance": true
}
```

actually mean?

---

## 033

Does the elevator stop immediately?

Does it finish its current journey?

Do pending calls disappear?

Can passengers still select floors?

Do the doors open?

What happens if emergency recall is active?

Who is allowed to do this?

---

## 034

None of those questions are answered by:

```json
{
  "maintenance": true
}
```

---

## 035

We've reduced a business operation...

to assignment.

---

## 036

```text
maintenance = true
```

is an implementation.

---

## 037

```text
EnterMaintenance
```

is an intention.

---

## 038

Those are not the same thing.

---

## 039

And assignment has another interesting property.

It has a default.

---

## 040

If the field is missing...

if the JSON parser does something surprising...

if a client sends stale data...

we have values.

True.

False.

Null.

Defaults.

---

## 041

But:

```text
EnterMaintenance
```

has no default value.

---

## 042

Either somebody intended to enter maintenance...

or they didn't.

---

## 043 — The first leak

Now let's look at the frontend.

It receives:

```json
{
  "floor": 3,
  "doorOpen": false,
  "maintenance": false,
  "load": 240
}
```

What should it display?

---

## 044

Can I select another floor?

---

## 045

Can I open the doors?

---

## 046

Can I close them?

---

## 047

Can I enter maintenance?

---

## 048

Can I trigger emergency recall?

---

## 049

The API doesn't say.

---

## 050

So the frontend works it out.

---

## 051

Somewhere in Pinia:

```javascript
const canSelectFloor =
  !maintenance &&
  !emergencyRecall &&
  load <= MAX_LOAD &&
  doorsClosed
```

---

## 052

Somewhere else:

```javascript
const canOpenDoors =
  !moving &&
  !maintenance
```

---

## 053

And another rule:

```javascript
const canEnterMaintenance =
  technician &&
  !emergencyRecall
```

---

## 054

Look what happened.

---

## 055

The server has a state machine.

---

## 056

And the client has...

a state machine.

---

## 057

Not because we deliberately designed two state machines.

Because we sent data instead of meaning.

---

## 058

The client owns a copy of the server's rules.

---

## 059 — URL knowledge

It gets better.

The frontend also knows this:

```javascript
const ELEVATOR_ID = 1
```

---

## 060

And this:

```javascript
const BUILDING_FLOORS = 9
```

---

## 061

And this:

```javascript
fetch(`/api/elevators/${id}/doors`)
```

---

## 062

And:

```javascript
fetch(`/api/elevators/${id}/destination`)
```

---

## 063

So the frontend knows:

domain rules,

domain constants,

resource identifiers,

URL structure,

and which URL corresponds to which behavior.

---

## 064

The client owns a copy of the server's state machine.

And a copy of its URL space.

---

## 065 — The UI leak

But there's another copy.

The browser also knows how domain state should become user interaction.

---

## 066

The API says:

```json
{
  "maintenance": false
}
```

The frontend says:

**Show “Enter maintenance”.**

---

## 067

The API says:

```json
{
  "load": 840
}
```

The frontend says:

**Disable floor selection.**

---

## 068

The API says:

```json
{
  "doorObstructed": true
}
```

The frontend says:

**Don't show “Close doors”.**

---

## 069

So the frontend isn't merely rendering data.

---

## 070

**It is interpreting the domain.**

---

## 071

We've copied domain knowledge into the frontend twice.

As rules.

And as UI.

---

## 072 — Why the BFF?

And this helps explain the BFF.

The backend speaks in data structures.

The frontend needs to speak in user interactions.

So we introduce something in between.

---

## 073

But look at what this particular BFF actually does.

```ts
export default defineEventHandler(async (event) => {
  const id = getRouterParam(event, 'id')
  const config = useRuntimeConfig()

  return await $fetch(
    `${config.serviceApiUrl}/elevators/${id}/open-doors`,
    { method: 'POST' }
  )
})
```

---

## 074

That is not business logic.

It is not orchestration.

It is not adapting one domain to another.

It is a URL and verb translation.

---

## 075

There are fourteen route files shaped like this.

Average size:

**12.4 lines.**

Duplication, measured at a threshold suitable for files this small:

**20.2%.**

---

## 076

For every rider action:

```text
Browser
   ↓
BFF route
   ↓
elevator-api
```

Two logical application hops.

---

## 077

The BFF adds another copy of the API's URL space.

Another place to encode the HTTP verb.

Another file to change when the protocol changes.

Another hop.

---

## 078

This is important.

The BFF is not the original problem.

---

## 079

It is **evidence** of the original problem.

---

## 080

The API does not tell the client how to proceed.

So the client needs a translation layer that knows how to proceed on its behalf.

---

## 081

Perhaps the problem isn't that we're missing a translation layer.

---

## 082

Perhaps the API is saying too little.

---

# `</before>`

# `<discovery>`

---

## 076

At this point I stopped refactoring.

---

## 077

Because I realized something uncomfortable.

---

## 078

I'm trying to improve the architecture of an elevator application...

---

## 079

...without being entirely sure I understand elevators.

---

## 080

What happens when the doors are obstructed?

What happens to pending requests in maintenance?

Can emergency recall interrupt maintenance?

What happens if the elevator is overloaded while doors are open?

When does movement actually begin?

---

## 081

These aren't Java questions.

They're not REST questions.

They're not Vue questions.

---

## 082

They're product questions.

---

## 083

And our current model doesn't answer them particularly well.

---

## 084

Maybe:

```java
boolean maintenance;
boolean doorOpen;
boolean obstructed;
int floor;
```

isn't a domain model.

---

## 085

Maybe it's just...

what we happen to store.

---

# `</discovery>`

# `<event-storming>`

---

## 086

So instead of starting with entities...

let's start with facts.

---

## 087

What happened?

---

## 088

**Elevator Called**

---

## 089

**Floor Selected**

---

## 090

**Movement Started**

---

## 091

**Floor Reached**

---

## 092

**Doors Opened**

---

## 093

**Doors Close Started**

---

## 094

**Doors Obstructed**

---

## 095

**Load Changed**

---

## 096

**Maintenance Entered**

---

## 097

**Emergency Recall Triggered**

---

## 098

These are events.

Things that happened.

Past tense.

Facts.

---

## 099

And for every event we can ask:

What caused it?

Who caused it?

Why was it allowed?

What happens next?

---

## 100

For example:

**Maintenance Entered**

Who can cause that?

---

## 101

A technician.

How do we know they're a technician?

What state may the elevator be in?

What happens to pending requests?

---

## 102

Suddenly:

```json
{
  "maintenance": true
}
```

looks rather inadequate.

---

## 103

And that's the point of Event Storming.

---

## 104

The sticky notes are not the deliverable.

---

## 105

The conversation is.

The contradictions are.

The unanswered questions are.

---

## 106

And notice what we haven't done yet.

No controllers.

No DTOs.

No database schema.

No aggregate.

No API design.

---

## 107

Because choosing those things before understanding what happens...

would be architecture by guesswork.

---

# `</event-storming>`

# `<event-modeling>`

---

## 108

Now let's put those events on a timeline.

---

## 109

Something happened.

Before that, somebody intended something.

---

## 110

Above the event:

**Call Elevator**

Below:

**Elevator Called**

---

## 111

**Select Floor**

→

**Floor Selected**

---

## 112

**Enter Maintenance**

→

**Pending Requests Cleared**

→

**Maintenance Entered**

---

## 113

Commands are intentions.

---

## 114

Events are facts.

---

## 115

And between them are rules.

---

## 116

Now add the views people need.

---

## 117

The rider needs to know:

Where is the elevator?

Where is it going?

Are the doors open?

What can I do?

---

## 118

The technician needs more.

Is maintenance available?

Is recall available?

What authority do I currently have?

---

## 119

So we now have three kinds of things.

---

## 120

Commands.

What somebody wants to happen.

---

## 121

Events.

What actually happened.

---

## 122

Views.

What somebody needs to know now.

---

## 123

And this gives us a useful specification format.

---

## 124

Given:

```text
ElevatorCalled(7)
FloorSelected(2)
```

When:

```text
EnterMaintenance
```

Then:

```text
PendingRequestsCleared
MaintenanceEntered
```

---

## 125

Another.

Given:

```text
DoorsCloseStarted
```

When:

```text
ObstructDoors
```

Then:

```text
DoorsObstructed
DoorsOpened
```

---

## 126

And time itself can cause behavior.

Given:

```text
MovementStarted(3, 7)
```

When:

```text
time passes
```

Then:

```text
FloorReached(4)
```

---

## 127

The timeline is becoming a specification.

---

## 128

And the interesting thing is not the boxes.

---

## 129

It's the questions they force us to answer.

---

## 130

Should maintenance clear pending calls?

Can recall interrupt maintenance?

Can the doors be obstructed while stationary?

Can you select the floor you're already on?

---

## 131

We're no longer designing fields.

---

## 132

We're discovering behavior.

---

# `</event-modeling>`

# `<state-modeling>`

---

## 133

We now have a timeline of what happens.

---

## 134

So let's ask a different question.

---

## 135

What must be true **between** these events?

---

## 136

After movement finishes...

before another command arrives...

what matters?

---

## 137

That's state.

---

## 138

**STATE IS A SUMMARY OF HISTORY**

---

## 139

Not every event matters forever.

---

## 140

If I know the elevator is currently idle at floor 4...

I don't necessarily need every motor pulse that got it there.

---

## 141

The history has been compressed into something useful now.

---

## 142 — Animated state model

Start with:

**Idle**

---

## 143

From Idle...

we can open the doors.

Reveal:

**DoorsOpen**

---

## 144

From DoorsOpen...

we can start closing them.

Reveal:

**DoorsClosing**

---

## 145

If closing completes and there's somewhere to go...

Reveal:

**MovingUp**

**MovingDown**

---

## 146

Eventually...

back to:

**Idle**

---

## 147

Now add:

**OutOfService**

---

## 148

And:

**EmergencyRecall**

---

## 149

Now the interesting part.

---

## 150

Every arrow is an opinion.

---

## 151

Can `MovingUp` go directly to `OutOfService`?

---

## 152

Can `DoorsClosing` transition into emergency recall?

---

## 153

Does recall open the doors immediately?

At which floor?

---

## 154

The state diagram doesn't magically answer these questions.

---

## 155

It makes our uncertainty visible.

---

## 156

That's valuable.

---

## 157

I think of the state diagram as a compression of the Event Model.

---

## 158

The Event Model tells us how we got here.

---

## 159

The state model summarizes what matters now.

---

## 160

Events explain the past.

---

## 161

State summarizes the present.

---

## 162

Later...

affordances will describe the possible future.

---

# `</state-modeling>`

# `<ddd>`

---

## 163

Now we have enough understanding to talk about DDD.

---

## 164

Not because DDD tells us how elevators work.

---

## 165

It doesn't.

---

## 166

DDD gives the understanding we've discovered somewhere to live.

---

## 167

A language.

---

## 168

An aggregate.

---

## 169

Value objects.

---

## 170

Commands.

---

## 171

Events.

---

## 172

Invariants.

---

## 173

Boundaries.

---

## 174

And critically:

one authoritative place where the rules belong.

---

## 175

Our elevator becomes plain Java.

---

## 176

No Spring.

No JPA.

No HTTP.

No JSON annotations.

No Lombok.

---

## 177

Just:

```java
public final class Elevator {
    ...
}
```

---

## 178

And methods that sound like the domain.

```java
elevator.call(floor);
elevator.selectFloor(floor);
elevator.openDoors();
elevator.closeDoors();
elevator.enterMaintenance(principal);
```

---

## 179

Not:

```java
elevator.setMaintenance(true);
```

---

## 180

The distinction looks tiny.

---

## 181

It isn't.

---

## 182

One says:

**change this value.**

---

## 183

The other says:

**attempt this behavior.**

---

## 184

And behavior can say no.

---

# `</ddd>`

# `<commands>`

---

## 185

Commands preserve intention.

---

## 186

```java
record EnterMaintenance() implements Command {}
```

---

## 187

```java
record SelectFloor(Floor floor) implements Command {}
```

---

## 188

```java
record ReportLoad(Kilograms load) implements Command {}
```

---

## 189

The command tells us what somebody wants.

---

## 190

The aggregate decides whether that is legal.

---

## 191

Not the controller.

---

## 192

Not the frontend.

---

## 193

Not a validation annotation.

---

## 194

The elevator.

---

## 195

Because the elevator owns elevator rules.

---

# `</commands>`

# `<events>`

---

## 196

And the aggregate doesn't return a mutated DTO.

---

## 197

Its output is facts.

---

## 198

```text
FloorSelected
```

---

## 199

```text
MovementStarted
```

---

## 200

```text
DoorsOpened
```

---

## 201

```text
MaintenanceEntered
```

---

## 202

```text
EmergencyRecallTriggered
```

---

## 203

Events tell us what actually happened.

---

## 204

Commands can fail.

---

## 205

Events cannot.

---

## 206

An event is already past tense.

---

# `</events>`

# `<tests>`

---

## 207

This changes something else.

Tests.

---

## 208

Here's a fairly normal CRUD test.

---

## 209

Send JSON.

---

## 210

Hit controller.

---

## 211

Call service.

---

## 212

Save entity.

---

## 213

Read database.

---

## 214

Assert:

```text
maintenance == true
```

---

## 215

That's useful.

---

## 216

It proves that data moved correctly.

---

## 217

But what did we learn about the elevator?

---

## 218

Not very much.

---

## 219

`maintenance = true`

doesn't tell us what should happen to pending calls.

---

## 220

It doesn't tell us whether movement may continue.

---

## 221

It doesn't tell us whether emergency recall wins.

---

## 222

It doesn't tell us who may enter maintenance.

---

## 223

Now look at this test.

```text
Given:
  ElevatorCalled(7)
  FloorSelected(2)

When:
  EnterMaintenance

Then:
  PendingRequestsCleared
  MaintenanceEntered
```

---

## 224

That is not primarily a test of implementation.

---

## 225

It's a claim.

---

## 226

**This is how we believe an elevator behaves.**

---

## 227

Another.

```text
Given:
  DoorsCloseStarted

When:
  ObstructDoors

Then:
  DoorsObstructed
  DoorsOpened
```

---

## 228

Another.

```text
Given:
  LoadChanged(840)

When:
  SelectFloor(7)

Then:
  CarCallRejected(overloaded)
```

---

## 229

Another.

```text
Given:
  MovementStarted

When:
  TriggerEmergencyRecall

Then:
  PendingRequestsCleared
  EmergencyRecallTriggered
```

---

## 230

Now our tests are starting to look suspiciously like the Event Model.

---

## 231

Good.

---

## 232

We now have several representations of our understanding.

---

## 233

The Event Model.

---

## 234

The state diagram.

---

## 235

The domain tests.

---

## 236

And later:

the affordances.

---

## 237

They should agree.

---

## 238

If they don't...

we've found something interesting.

---

## 239

CRUD-shaped APIs encourage tests that prove data moved correctly.

---

## 240

Domain tests prove that our understanding of behavior is correct.

---

## 241

Or, more accurately...

that the implementation agrees with our current understanding.

---

## 242

Because Product might walk in tomorrow and say:

“No, that's not how elevators work.”

---

## 243

Excellent.

---

## 244

Change the specification.

Change the test.

Change the model.

---

## 245

Knowledge accumulated.

---

# `</tests>`

# `<cqrs>`

---

## 246

We now have another useful separation.

---

## 247

Commands ask the domain to do something.

---

## 248

Queries ask what is true.

---

## 249

CQRS.

---

## 250

Don't panic.

---

## 251

There is no Kafka.

---

## 252

No microservices.

---

## 253

No seventeen databases.

---

## 254

We still have one application.

---

## 255

One H2 database.

---

## 256

Synchronous projections.

---

## 257

Command:

```text
Command
  ↓
Handler
  ↓
Elevator
  ↓
Events
```

---

## 258

Then:

```text
Events
  ↓
Projection
  ↓
Read model
```

---

## 259

Queries never need to load the aggregate.

---

## 260

They read the view designed for the question.

---

## 261

This matters because the aggregate and the UI have different jobs.

---

## 262

The aggregate protects invariants.

---

## 263

The view explains the present.

---

# `</cqrs>`

# `<time>`

---

## 264

And now we have to admit something else into our model.

---

## 265

Time.

---

## 266

The elevator doesn't teleport.

---

## 267

Floor 3 to floor 4 takes roughly two seconds.

---

## 268

Doors remain open for roughly four seconds.

---

## 269

These are domain transitions.

---

## 270

So we schedule them explicitly.

---

## 271

```text
MovementStarted
      ↓
scheduler
      ↓
FloorReached
```

---

## 272

The important distinction is:

we schedule the future transition.

---

## 273

We don't keep asking:

“Has enough time elapsed yet?”

---

## 274

The model tells us what is expected to happen next.

---

# `</time>`

# `<rest>`

---

## 275

Now we can finally talk about REST.

---

## 276

Which is convenient.

Because this is supposedly a talk about web APIs.

---

## 277

REST is not:

HTTP + JSON.

---

## 278

REST is not:

pretty URLs.

---

## 279

REST is not:

CRUD with nouns.

---

## 280

REST gives us constraints.

Client-server.

Statelessness.

Cacheability.

Uniform interface.

Layered system.

Code-on-demand, optional.

---

## 281

And the interesting one for this talk is the Uniform Interface.

---

## 282

Identification of resources.

Manipulation through representations.

Self-descriptive messages.

Hypermedia as the engine of application state.

---

## 283

A resource is not a row.

---

## 284

A representation is not the resource.

---

## 285

The representation tells us something about the resource **now**.

---

## 286

And REST gives us something our CRUD API was missing.

---

## 287

Not just:

What exists?

---

## 288

Not just:

What is true?

---

## 289

But:

**What can happen next?**

---

# `</rest>`

# `<hypermedia>`

---

## 290

We have four useful tenses now.

---

## 291

Command.

Intended future.

---

## 292

Event.

Past fact.

---

## 293

View.

Present truth.

---

## 294

Affordance.

Possible future.

---

## 295

That's the missing piece.

---

## 296

Instead of sending state and asking the client to reconstruct legality...

---

## 297

...the server can simply tell the client what is currently possible.

---

## 298

For example:

```json
{
  "floor": 3,
  "state": "idle",
  "operations": [
    {
      "rel": "call-elevator",
      "method": "POST",
      "href": "/elevators/a7f3"
    },
    {
      "rel": "open-doors",
      "method": "POST",
      "href": "/elevators/a7f3"
    }
  ]
}
```

---

## 299

What isn't there?

---

## 300

`select-floor`.

---

## 301

Maybe the elevator is overloaded.

---

## 302

Maybe I'm not inside the elevator.

---

## 303

Maybe some other invariant applies.

---

## 304

The client doesn't need to know.

---

## 305

The operation is absent.

---

## 306

And if I forge the request anyway?

---

## 307

The aggregate refuses it.

---

## 308

Hypermedia guides.

---

## 309

The domain enforces.

---

## 310

Never confuse those two.

---

## 311

The equation becomes:

**STATE + AUTHORITY = AFFORDANCES**

---

## 312

The state says what is happening.

---

## 313

Authority says who is asking.

---

## 314

Together they determine what may happen next.

---

## 315 — One resource

And here's another change.

Before:

```text
POST /elevators/1/calls
POST /elevators/1/destination
PUT  /elevators/1/doors
PATCH /elevators/1
```

---

## 316

After:

```http
GET /elevators/a7f3
```

and:

```http
POST /elevators/a7f3
```

---

## 317

The command is the message.

```json
{
  "type": "SelectFloor",
  "floor": 7
}
```

---

## 318

Or:

```json
{
  "type": "OpenDoors"
}
```

---

## 319

Or:

```json
{
  "type": "EnterMaintenance"
}
```

---

## 320

This does **not** mean:

“RPC is REST now because we put all the verbs in JSON.”

---

## 321

That would merely move the verb.

---

## 322

The important part is that the client does not invent these messages.

---

## 323

It discovers them.

---

## 324

The representation says:

```json
{
  "rel": "select-floor",
  "method": "POST",
  "href": "/elevators/a7f3",
  "fields": [
    {
      "name": "type",
      "value": "SelectFloor",
      "hidden": true
    },
    {
      "name": "floor",
      "options": [1,2,3,4,5,6,7,8,9]
    }
  ]
}
```

---

## 325

The client doesn't need to know that `type` is required.

---

## 326

The server supplied it.

---

## 327

The client doesn't need to know which floors exist.

---

## 328

The server supplied them.

---

## 329

The client doesn't construct the target.

---

## 330

The server supplied it.

---

## 331

**FOLLOW, DON'T CONSTRUCT**

---

## 332

This has another nice consequence.

---

## 333

Our database ID is no longer our API.

---

## 334

We distinguish:

domain identifiers,

surrogate database identifiers,

resource identifiers.

---

## 335

And the resource identifier belongs to the server.

---

## 336

So in CI we can render URLs like:

```text
/elevators/1
```

---

## 337

Or:

```text
/8f2d7a/91ce3f
```

---

## 338

If the client breaks...

---

## 339

...it knew too much.

---

## 340

We had 34 hard-coded elevator URL literals in the frontend.

---

## 341

Afterwards:

**zero.**

---

# `</hypermedia>`

# `<html>`

---

## 342

Now look again at the affordance.

---

## 343

It has a relation.

---

## 344

A target.

---

## 345

An HTTP method.

---

## 346

Fields.

---

## 347

Required values.

---

## 348

Permitted values.

---

## 349

Maybe labels.

Maybe prompts.

---

## 350

This is starting to look suspiciously like something the Web has had for a while.

---

## 351

**A FORM.**

---

## 352

Here's the machine-oriented representation:

```json
{
  "rel": "select-floor",
  "method": "POST",
  "href": "/elevators/a7f3",
  "fields": [
    {
      "name": "type",
      "value": "SelectFloor",
      "hidden": true
    },
    {
      "name": "floor",
      "options": [1,2,3,4,5,6,7,8,9]
    }
  ]
}
```

---

## 353

And here is another representation of exactly the same affordance.

```html
<form method="post"
      action="/elevators/a7f3">

  <input type="hidden"
         name="type"
         value="SelectFloor">

  <select name="floor">
    ...
  </select>

  <button>Select floor</button>
</form>
```

---

## 354

Those are not two different APIs.

---

## 355

They're two representations of the same affordance.

---

## 356

One is convenient for a program.

---

## 357

One is convenient for a person.

---

## 358

And this leads to a slightly uncomfortable question.

---

## 359

We've spent all this time making the server smarter.

---

## 360

The server knows the state.

---

## 361

The server knows the rules.

---

## 362

The server knows my authority.

---

## 363

The server knows which operations are legal.

---

## 364

The server knows which input they require.

---

## 365

And then...

---

## 366

...we serialize all of that as JSON.

---

## 367

Send it to JavaScript.

---

## 368

And ask JavaScript to turn it into HTML.

---

## 369

Why?

---

## 370

**WHY DOESN'T THE API JUST RETURN HTML?**

---

## 371

```http
GET /elevators/a7f3 HTTP/1.1
Accept: text/html
```

---

## 372

```http
HTTP/1.1 200 OK
Content-Type: text/html
```

---

## 373

And the body is the elevator UI.

---

## 374

Not a separate frontend calling the API.

---

## 375

**This is the API.**

---

## 376

HTML is a representation of the elevator resource.

---

## 377

The same application code that computes the affordances...

renders those affordances as links and forms.

---

## 378

If `select-floor` is legal...

the representation contains the control.

---

## 379

If it isn't...

it doesn't.

---

## 380

If I am a technician...

the representation may contain:

**Enter maintenance.**

---

## 381

If I am a rider...

it doesn't.

---

## 382

We no longer send:

```json
{
  "maintenanceAllowed": true
}
```

---

## 383

...and ask Vue what that means.

---

## 384

We send the thing that lets the user enter maintenance.

---

## 385

That's an important distinction.

---

## 386

This doesn't mean the backend owns visual design.

---

## 387

CSS still owns presentation.

---

## 388

The browser still owns interaction.

---

## 389

JavaScript can still enhance behavior.

---

## 390

What the server owns is the **semantic UI**.

---

## 391

What actions exist?

---

## 392

What input do they require?

---

## 393

What can this user do...

**now?**

---

## 394

That's domain knowledge.

---

## 395

And we've spent this entire talk arguing that domain knowledge should have one authoritative home.

---

## 396 — HTML already is hypermedia

HTML already has hypermedia controls.

---

## 397

```html
<a href="...">
```

---

## 398

Follow another resource.

---

## 399

```html
<form action="..." method="post">
```

---

## 400

Perform an operation.

---

## 401

```html
<select name="floor">
```

---

## 402

Choose from server-provided possibilities.

---

## 403

```html
<input type="hidden"
       name="type"
       value="SelectFloor">
```

---

## 404

Carry protocol information the human doesn't need to know.

---

## 405

Think about what the human does **not** know.

---

## 406

They don't construct the URL.

---

## 407

They don't know the command type.

---

## 408

They don't calculate which floors are valid.

---

## 409

They don't ask whether the operation is legal.

---

## 410

They see a button.

---

## 411

They select a floor.

---

## 412

They follow the controls.

---

## 413

This is exactly what we've been asking our JSON clients to do.

---

## 414

Browsers have been hypermedia clients all along.

---

## 415

We just spent fifteen years teaching them not to be.

---

## 416 — The browser gets stupider

Before:

```text
Vue
 ↓
components
 ↓
Pinia
 ↓
domain rules
 ↓
URL construction
 ↓
Nitro BFF
 ↓
API
```

---

## 417

After:

```text
Browser
 ↓
HTML
 ↓
forms + links
 ↓
API
```

---

## 418

That diagram is almost embarrassing.

---

## 419

Which is usually a good sign.

---

## 420

We deleted the BFF.

---

## 421

We deleted the client-side store.

---

## 422

We deleted the generated API models.

---

## 423

We deleted the hard-coded paths.

---

## 424

But the interesting thing isn't what we deleted.

---

## 425

It's **why** we could delete it.

---

## 426

The browser no longer needs enough domain knowledge to reconstruct the application.

---

## 427

The server sends it an application.

---

## 428

**THE API IS THE APPLICATION**

---

# `</html>`

# `<live-html>`

---

## 429

At this point somebody is thinking:

---

## 430

“Great.”

---

## 431

“We've reinvented 1997.”

---

## 432

Kind of.

---

## 433

Except we can keep the useful things we've learned since 1997.

---

## 434

The elevator moves.

---

## 435

I don't want to hit Refresh every two seconds.

---

## 436

But that does **not** imply that I need a client-side elevator implementation.

---

## 437

Remember our events.

---

## 438

The server already knows:

```text
FloorReached(4)
```

---

## 439

So:

```text
Domain event
     ↓
Projection
     ↓
Read model
     ↓
HTML representation
     ↓
SSE
     ↓
DOM patch
```

---

## 440

The server knows the event happened.

---

## 441

The projection changes.

---

## 442

The server renders the changed representation.

---

## 443

Datastar patches the relevant HTML into the page.

---

## 444

We get a dynamic application...

---

## 445

...without moving the elevator back into JavaScript.

---

## 446

And this gives us a very useful boundary.

---

## 447

**SERVER OWNS TRANSITIONS**

---

## 448

**BROWSER OWNS ANIMATION**

---

## 449

The server knows:

the elevator moved from floor 3 to floor 4.

---

## 450

The browser is perfectly capable of animating a little elevator between 3 and 4.

---

## 451

The browser does not need to know **why** floor 4 was legal.

---

## 452

It doesn't need to know whether overload prevented movement.

---

## 453

It doesn't need to know how recall works.

---

## 454

Those are domain rules.

---

## 455

Animation is presentation.

---

## 456

That's a much healthier division of responsibility.

---

# `</live-html>`

# `<delete-the-bff>`

---

## Deleting the evidence

Now we can do something more convincing than draw another architecture diagram.

We can delete code.

The point is not that the system has fewer lines overall. It doesn't.

The point is that a category of **client-side knowledge is no longer necessary**.

Measured result:

```text
BFF + store files removed     16
BFF + store lines removed     493
BFF route files               14
BFF-route duplication         20.2%
logical hops per rider action 2 → 1
```

The deployable service count did not change. The BFF lived inside the Nuxt container.

What disappeared was pass-through code, duplicated protocol knowledge, and one logical hop.

---

And I want to show the deletion honestly.

Not as bullets.

As diffs.

**The red code is the argument.**

---

## Delete: polling

```diff
--- StatusDisplay.vue
+++ StatusDisplay.vue
@@
-onMounted(() => {
-  poller = setInterval(() => {
-    store.fetchStatus()
-    store.fetchCalls()
-    store.fetchCarCalls()
-  }, 1500)
-})
```

We do not optimize the poller.

We delete the poller.

---

## Delete: duplicated timing

```diff
--- ElevatorShaft.vue
+++ ElevatorShaft.vue
@@
-const TRAVEL_SECONDS_PER_FLOOR = 2
```

The remaining decorative shaft animation reads its endpoints and duration from DOM that the API rendered.

---

## Delete: status BFF route

```diff
--- status.get.ts
+++ /dev/null
@@
-[entire BFF route deleted]
```

The plan records the route deletion, but not its complete old body, so the slide should show the file deletion rather than invent code.

---

## Delete: call-elevator BFF routes

```diff
--- calls.get.ts
+++ /dev/null
@@
-[entire BFF route deleted]

--- calls.post.ts
+++ /dev/null
@@
-[entire BFF route deleted]
```

The client can follow the affordance instead.

---

## Delete: client call action

```diff
--- app/stores/elevator.ts
+++ app/stores/elevator.ts
@@
-store.callElevator
```

The migration plan names this deletion explicitly. The full old method body is not preserved in the plan.

---

## Delete: hard-coded floor list

```diff
--- CallPanel.vue
+++ CallPanel.vue
@@
-[hard-coded floor list deleted]
```

The server now supplies the permitted values.

---

## Delete: car-call routes

```diff
--- car-calls routes
+++ /dev/null
@@
-[all car-call BFF routes deleted]
```

---

## Delete: movement reconstruction

```diff
--- ElevatorService.java
+++ ElevatorService.java
@@
-recomputeState(...)
-recomputeMovement(...)
-dispatchToFloor(...)
-serveNextPendingCall(...)
```

Movement becomes explicit domain behaviour around `RequestQueue`, commands, events and scheduled transitions.

---

## Delete: door routes

```diff
--- open-doors route
+++ /dev/null
@@
-[entire route deleted]

--- close-doors route
+++ /dev/null
@@
-[entire route deleted]

--- obstruction routes
+++ /dev/null
@@
-[all obstruction routes deleted]
```

---

## Delete: obstruction as assignment

```diff
--- client/API payload
+++ client/API payload
@@
-{ "doorObstructed": true }
-{ "doorObstructed": false }
```

The model now has `ObstructDoors` and `ClearObstruction`.

---

## Delete: weight route

```diff
--- weight route
+++ /dev/null
@@
-[entire route deleted]
```

---

## Delete: client-side overload rule

```diff
--- client
+++ client
@@
-if (load > MAX_LOAD) {
-  // decide that floor selection is unavailable
-}
```

When overloaded, `select-floor` is simply absent.

---

## Delete: maintenance route

```diff
--- maintenance route
+++ /dev/null
@@
-[entire route deleted]
```

---

## Delete: mirrored authorization state

```diff
--- app/stores/elevator.ts
+++ app/stores/elevator.ts
@@
-const res = await $fetch<{ inserted: boolean }>('/api/key')
-this.technicianKeyInserted = res.inserted
```

The server already knows the caller's authority. The client no longer asks for a boolean copy of that knowledge.

---

## Delete: key-state round trip

```diff
--- client protocol
+++ client protocol
@@
-GET /api/key
```

---

## Delete: privilege rules in Vue

```diff
--- StatusDisplay.vue
+++ StatusDisplay.vue
@@
-<div v-if="store.technicianKeyInserted" class="tech-actions">
-  <button v-if="!inMaintenance" @click="store.enterMaintenance()">
-  <button v-if="inMaintenance"  @click="store.exitMaintenance()">
-</div>
```

Privileged affordances now appear or do not appear.

---

## Delete: configured OAuth issuer

```diff
--- elevator-ui configuration
+++ elevator-ui configuration
@@
-NUXT_OAUTH_ISSUER=...
```

The issuer is discovered rather than configured.

---

## Delete: emergency-recall route

```diff
--- emergency-recall route
+++ /dev/null
@@
-[entire route deleted]
```

---

## Delete: the God Object

```diff
--- ElevatorService.java
+++ /dev/null
@@
-[the last of ElevatorService deleted]
```

By this point the behaviour has moved into the aggregate and slices.

---

## Delete: the entire BFF

```diff
--- elevator-ui/server/api/
+++ /dev/null
@@
-[all BFF routes deleted]
```

Not renamed.

Not moved.

Deleted.

---

## Delete: the Pinia elevator store

```diff
--- elevator-ui/app/stores/elevator.ts
+++ /dev/null
@@
-[store deleted]
```

The client-side state machine does not become a better client-side state machine.

It disappears.

---

## Delete: typed API models

```diff
--- elevator-ui
+++ elevator-ui
@@
-[typed elevator API models deleted]
```

---

## Delete: domain constants

```diff
--- app/stores/elevator.ts
+++ /dev/null
@@
-const ELEVATOR_ID = 1
-const BUILDING_FLOORS = 9

--- ElevatorShaft.vue
+++ /dev/null
@@
-const TRAVEL_SECONDS_PER_FLOOR = 2
```

---

## Delete: hard-coded URL construction

```diff
--- elevator-ui
+++ elevator-ui
@@
-await $fetch(`/api/elevators/${ELEVATOR_ID}/car-calls`, { ... })
-[33 other hard-coded /elevators/... literals deleted]
```

Measured result:

```text
hard-coded /elevators/... literals
34 → 0
```

The client does not learn a nicer URL scheme.

It stops owning the URL scheme.

---

## Delete: state-rendering Vue components

```diff
--- rider / technician Vue components
+++ /dev/null
@@
-[four state-rendering components deleted]
```

A small CSS-only shaft survives because animation is presentation, not domain interpretation.

---

## Delete: Vitest

```diff
--- elevator-ui test tooling
+++ /dev/null
@@
-[Vitest deleted]
```

There is no client-side store/domain logic left to unit-test.

The Playwright suite survives.

---

## What survives

```diff
 elevator-ui/
+ page shell
+ routing
+ layouts
+ CSS
+ decorative shaft animation
+ Playwright
+ one injected entry-point URL

-server/api/
-app/stores/elevator.ts
-state-rendering Vue components
-typed API models
-hard-coded domain constants
-hard-coded elevator paths
-poller
-Vitest
```

---

The Playwright suite passes against a client that knows exactly **one URL**.

Everything else is discovered.

---

Before:

```text
Browser → BFF route → elevator-api
```

After:

```text
Browser → elevator-api
```

Caddy remains, but it is a transparent reverse proxy, not a logical application hop.

---

The BFF deletion is therefore not primarily:

**493 fewer lines.**

It is:

**493 lines of evidence that the client used to know things it no longer needs to know.**

---

# `</delete-the-bff>`

# `<representations>`

---

## 457

HTML is not the only representation.

---

## 458

**ONE RESOURCE**

---

## 459

**ONE AFFORDANCE MODEL**

---

## 460

**MULTIPLE REPRESENTATIONS**

---

## 461

```http
Accept: text/html
```

---

## 462

For a person.

---

## 463

```http
Accept: application/vnd.elevator.state+json
```

---

## 464

For our simple programmatic client.

---

## 465

```http
Accept: application/vnd.siren+json
```

---

## 466

For a standardized hypermedia representation.

---

## 467

```http
Accept: application/ld+json
```

---

## 468

For a client that benefits from shared semantic vocabulary.

---

## 469

Same elevator.

---

## 470

Same state.

---

## 471

Same authority.

---

## 472

Same affordances.

---

## 473

Different representation.

---

## 474

The important architectural decision isn't:

Siren or Hydra?

---

## 475

It's not even:

JSON or HTML?

---

## 476

The important decision happened earlier.

---

## 477

**THE DOMAIN DECIDES THE POSSIBILITIES ONCE**

---

## 478

Renderers express those possibilities differently.

---

## 479

That's why we have an `AffordanceCatalog`.

---

## 480

Each behavior contributes its affordance.

---

## 481

The HTML renderer can render it.

---

## 482

The Siren renderer can render it.

---

## 483

The Hydra renderer can render it.

---

## 484

Our simple JSON renderer can render it.

---

## 485

Adding another renderer does not mean reimplementing the elevator.

---

## 486

And adding another behavior doesn't mean remembering to teach four renderers the business rule.

---

## 487

The rule belongs to the domain.

---

## 488

The affordance translates that rule into possibility.

---

## 489

The renderer translates possibility into representation.

---

# `</representations>`

# `<failure>`

---

## 490

What happens when a command is refused?

---

## 491

A lot of APIs do this:

```http
HTTP/1.1 400 Bad Request
```

---

## 492

Maybe:

```json
{
  "error": "invalid operation"
}
```

---

## 493

Thanks.

---

## 494

A useful refusal should answer three questions.

---

## 495

**No.**

---

## 496

**Because.**

---

## 497

**You can.**

---

## 498

For example:

```json
{
  "type": "https://example.com/problems/elevator-overloaded",
  "title": "Floor cannot be selected",
  "detail": "The elevator is overloaded.",
  "operations": [
    {
      "rel": "report-load",
      ...
    }
  ]
}
```

---

## 499

Failure continues the conversation.

---

## 500

The response still describes what can happen next.

---

## 501

And again:

the client doesn't invent the recovery path.

---

## 502

It follows it.

---

# `</failure>`

# `<replay>`

---

## 503

Let's replay the elevator.

---

## 504

Before.

Rider calls from floor 7.

```http
POST /api/elevators/1/calls
```

---

## 505

Client knows:

the elevator ID,

the URL,

the payload shape,

the legality.

---

## 506

After.

---

## 507

Client gets elevator representation.

---

## 508

Finds:

```text
rel="call-elevator"
```

---

## 509

Follows it.

---

## 510

The domain handles:

```text
CallElevator(7)
```

---

## 511

Produces:

```text
ElevatorCalled(7)
MovementStarted(...)
```

---

## 512

Projection updates.

---

## 513

New representation arrives.

---

## 514

New possibilities.

---

## 515 — Overload

Load becomes:

```text
840kg
```

---

## 516

Before:

frontend executes:

```javascript
if (load > MAX_LOAD) ...
```

---

## 517

After:

`select-floor` is absent.

---

## 518

And if the client forges it anyway:

---

## 519

The aggregate refuses it.

---

## 520

One rule.

---

## 521

One owner.

---

## 522 — Obstruction

Doors begin closing.

---

## 523

Something obstructs them.

---

## 524

Command:

```text
ObstructDoors
```

---

## 525

Events:

```text
DoorsObstructed
DoorsOpened
```

---

## 526

Representation changes.

---

## 527

`close-doors` disappears.

---

## 528

Maybe `clear-obstruction` appears.

---

## 529

The UI changed because the domain changed.

---

## 530

Not because somebody remembered to update a Vue computed property.

---

# `</replay>`

# `<authorization>`

---

## 531

Now the technician.

---

## 532

In the physical elevator...

there is a key switch.

---

## 533

This is interesting.

---

## 534

Is:

```text
InsertKey
```

an elevator command?

---

## 535

No.

---

## 536

The key does not change elevator state.

---

## 537

It changes **authority**.

---

## 538

That's a different concept.

---

## 539

So the `insert-key` affordance initiates authentication.

---

## 540

At the boundary we validate credentials.

---

## 541

Then the domain receives something typed.

---

## 542

A Principal.

---

## 543

Proof has already been checked.

---

## 544

Now the domain can ask:

Does this principal have the authority to enter maintenance?

---

## 545

Or trigger recall?

---

## 546

Browser authentication might be a cookie.

---

## 547

Machine authentication might be:

```http
Authorization: Bearer ...
```

---

## 548

The domain doesn't care.

---

## 549

Both become authority.

---

## 550

And the representation changes accordingly.

---

## 551

Rider:

no maintenance affordance.

---

## 552

Technician:

maintenance affordance.

---

## 553

Same resource.

---

## 554

Different authority.

---

## 555

Different possible future.

---

## 556

Again:

**STATE + AUTHORITY = AFFORDANCES**

---

## 557

But remember:

absence is not security.

---

## 558

The aggregate still validates the command.

---

## 559

Hypermedia is guidance.

---

## 560

Authorization is enforcement.

---

# `</authorization>`

# `<evolution>`

---

## 561

Now imagine we change the API.

---

## 562

`EnterMaintenance` gains a required reason.

---

## 563

Traditional client?

Potential breaking change.

---

## 564

Hypermedia client?

---

## 565

The server can provide:

```json
{
  "name": "reason",
  "required": true,
  "options": [
    "inspection",
    "repair",
    "testing"
  ]
}
```

---

## 566

A sufficiently generic client can render it.

---

## 567

The HTML client gets the new `<select>` directly.

---

## 568

No coordinated frontend release required.

---

## 569

Now imagine an entirely new capability.

---

## 570

```text
rel="fire-service-mode"
```

---

## 571

Old clients don't know it.

---

## 572

They ignore it.

---

## 573

Clients that understand it can follow it.

---

## 574

This expands the space of compatible change.

---

## 575

Does hypermedia eliminate versioning?

---

## 576

No.

---

## 577

Semantics still change.

Representations still change.

Contracts can still break.

---

## 578

But we have removed a large class of coupling caused by clients constructing the protocol themselves.

---

# `</evolution>`

# `<documentation>`

---

## 579

What about OpenAPI?

---

## 580

Still useful.

---

## 581

It documents the stable parts.

---

## 582

The media types.

---

## 583

The affordance envelope.

---

## 584

The field structure.

---

## 585

Problem Details.

---

## 586

Authentication.

---

## 587

Relation vocabulary.

---

## 588

But OpenAPI can't tell you which operations are available for:

this elevator,

in this state,

for this user,

right now.

---

## 589

The live representation can.

---

## 590

So documentation has two levels.

---

## 591

The protocol documentation tells us what kinds of messages exist.

---

## 592

The representation tells us what this conversation permits now.

---

## 593

The live API is part of its own documentation.

---

# `</documentation>`

# `<vertical-slices>`

---

## 594

How is this code organized?

---

## 595

Not:

```text
controllers/
services/
repositories/
models/
```

---

## 596

Instead:

```text
callelevator/
selectfloor/
opendoors/
closedoors/
reportload/
entermaintenance/
triggeremergencyrecall/
```

---

## 597

Each behavior owns:

command,

handler,

endpoint integration,

affordance contribution,

tests.

---

## 598

Vertical slices.

---

## 599

But not everything is duplicated.

---

## 600

The aggregate is shared.

---

## 601

Events are shared.

---

## 602

Persistence is shared.

---

## 603

Renderers are shared.

---

## 604

Scheduler is shared.

---

## 605

**SLICE THE SHELL**

---

## 606

**SHARE THE CORE**

---

## 607

And because all commands target the elevator resource...

we don't need a controller per command URL.

---

## 608

A shared command controller receives:

```http
POST /elevators/a7f3
```

---

## 609

Reads:

```json
{
  "type": "OpenDoors"
}
```

---

## 610

And dispatches to the slice-owned endpoint.

---

## 611

Unknown type?

---

## 612

400.

---

## 613

The resource exists.

---

## 614

The message is invalid.

---

# `</vertical-slices>`

# `<migration>`

---

## Migration

We did not rewrite everything in one heroic weekend.

We moved one behaviour at a time.

But now I want to describe the migration not only by what each slice added.

I want to describe it by **what each slice made safe to delete**.

---

## Slice 0 — Hypermedia kernel

```diff
# nothing deleted yet
```

That is deliberate.

The first slice creates somewhere for knowledge to move **to**.

---

## Slice 1 — Status + SSE

```diff
-status.get.ts
-setInterval(..., 1500)
-TRAVEL_SECONDS_PER_FLOOR
```

---

## Slice 2 — Call elevator

```diff
-calls.get.ts
-calls.post.ts
-store.callElevator
-CallPanel hard-coded floor list
```

---

## Slice 3 — Select floor

```diff
-car-calls routes
-recomputeState
-recomputeMovement
-dispatchToFloor
-serveNextPendingCall
```

---

## Slice 4 — Doors

```diff
-open-doors route
-close-doors route
-obstruction routes
-{ "doorObstructed": true|false }
```

---

## Slice 5 — Overload

```diff
-weight route
-client-side overload warning / legality logic
```

---

## Slice 6 — Maintenance + authorization

```diff
-maintenance route
-technicianKeyInserted
-GET /api/key
-v-if guards for privileged operations
-NUXT_OAUTH_ISSUER
```

---

## Slice 7 — Emergency recall

```diff
-emergency-recall route
-ElevatorService.java
```

---

## Slice 8 — Delete the evidence

This slice adds nothing.

That is the feature.

```diff
-server/api/
-app/stores/elevator.ts
-typed API models
-ELEVATOR_ID
-BUILDING_FLOORS
-TRAVEL_SECONDS_PER_FLOOR
-every hard-coded elevator path
-state-rendering Vue components
-Vitest
```

The Playwright suite must still pass.

That commit's diff is the argument.

---

The migration rule remains:

Move one piece of knowledge at a time.

Give it one authoritative home.

Then delete every other copy.

---

# `</migration>`

# `<metrics>`

---

## 635

So.

Was it worth it?

---

## 636

Let's measure.

---

## 637

First:

the bad news.

---

## 638

Backend production code.

Before:

**1,043 lines.**

---

## 639

After:

**6,598 lines.**

---

## 640

Six times bigger.

---

## 641

We paid for this architecture.

---

## 642

Tests:

970 lines.

---

## 643

After:

3,336.

---

## 644

More code.

More tests.

More concepts.

---

## 645

If lines of code is your architectural metric...

---

## 646

...this refactoring is a catastrophe.

---

## 647 — Complexity

But look at where complexity lives.

---

## 648

Old layer-based files:

average cyclomatic complexity:

**5.8**

Maximum:

**113**

---

## 649

Feature slices:

average:

**2.3**

Maximum:

**17**

---

## 650

Shared kernel:

average:

**2**

Maximum:

**27**

---

## 651

And where is that 27?

---

## 652

The elevator aggregate.

---

## 653

Good.

---

## 654

We didn't remove domain complexity.

---

## 655

We changed its location.

---

## 656

The complicated thing is complicated:

**the elevator.**

---

## 657

Spring controllers shouldn't be.

---

## 658 — BFF

BFF and store files removed:

**16**

Of those, **14 are BFF route files**.

---

## 659

BFF + store lines removed:

**493**

The BFF route files average **12.4 lines** each.

At a clone-detection threshold suitable for files that small:

**20.2% duplication.**

---

## 660

Logical network hops per rider action:

**2 → 1**

---

## 661

Before:

```text
Browser → BFF → API
```

---

## 662

After:

```text
Browser → API
```

---

## 663

Important caveat.

---

## 664

Deployable service count did **not** decrease.

---

## 665

The BFF lived in the Nuxt container.

---

## 666

We removed a logical application layer.

Not a deployment.

And we did not relocate those routes.

Their protocol knowledge became unnecessary.

---

## 667 — URLs

Hard-coded `/elevators/...` literals in UI:

---

## 668

**34**

---

## 669

After:

---

## 670

**0**

---

## 671

That's one of my favorite numbers in this entire refactoring.

---

## 672 — Endpoint mappings

Endpoint mappings:

**12 → 9**

---

## 673

Despite adding explicit domain behaviors.

---

## 674

Because behavior no longer implies a new client-known URL.

---

## 675 — UI

UI diff:

```text
+1518
-2585
```

---

## 676

Net negative.

---

## 677

Same feature parity.

---

## 678

Why?

---

## 679

Because the client stopped implementing the elevator.

---

## 680 — Cost per capability

Average feature slice:

about **209 lines**.

---

## 681

That's not inherently good.

---

## 682

209 lines is not a universal architectural constant.

Please don't put it on LinkedIn.

---

## 683

The useful property is the shape.

---

## 684

A new behavior has a predictable home.

---

## 685

Command.

Handler.

Affordance.

Tests.

---

## 686

Behavior-local.

Testable.

Predictable.

---

## 687 — Framework coupling

Before, our domain and business layer imported:

**13 distinct framework symbols.**

---

## 688

The old Elevator was also a JPA entity.

---

## 689

Persistence model.

Domain model.

JSON model.

Same class.

---

## 690

The old service even knew about HTTP exceptions.

---

## 691

After:

domain framework imports:

**zero.**

---

## 692

Command and handler layer?

One distinct framework concept.

---

## 693

`@Component`.

---

## 694

This doesn't prove upgrades will be cheap.

---

## 695

But it does show where framework coupling now lives.

---

## 696

Outside the domain.

---

## 697 — Duplication

Now another bad-looking metric.

---

## 698

Duplication before:

**7.1%**

---

## 699

After:

**16%**

---

## 700

Architecture got worse!

---

## 701

Maybe.

---

## 702

Or perhaps the detector sees:

similar imports,

similar command records,

similar affordance contributors,

similar slice structure.

---

## 703

Before, the clones often copied controller and validation logic.

---

## 704

After, much of the duplication is structural repetition.

---

## 705

Metrics need interpretation.

---

## 706

A number is evidence.

---

## 707

It is not understanding.

---

# `</metrics>`

# `<change-cost>`

---

## 708

Let's try another perspective.

---

## 709

Suppose we need five more capabilities.

---

## 710

And suppose the old API needs several versions alive in parallel.

---

## 711

Using the measured old versionable layer...

a full fork is roughly:

**5,730 lines.**

---

## 712

Controller-only duplication:

roughly:

**2,140.**

---

## 713

Five additive slices at our measured average:

roughly:

**1,043.**

---

## 714

These are not empirical predictions.

---

## 715

They're illustrative economics.

---

## 716

The interesting distinction is:

---

## 717

**MULTIPLICATIVE**

---

## 718

versus:

---

## 719

**ADDITIVE**

---

## 720

Do the sums for your own estate.

---

# `</change-cost>`

# `<product>`

---

## 721

But none of those metrics are the biggest win.

---

## 722

Not the deleted BFF.

---

## 723

Not the 34 URLs.

---

## 724

Not the complexity numbers.

---

## 725

Not the framework imports.

---

## 726

The biggest thing that happened was this:

---

## 727

**WE HAD TO UNDERSTAND THE ELEVATOR**

---

## 728

Really understand it.

---

## 729

We had to ask:

What actually happens?

---

## 730

What events exist?

---

## 731

What states matter?

---

## 732

What transitions are legal?

---

## 733

Who may cause them?

---

## 734

What happens over time?

---

## 735

What should the user be able to do next?

---

## 736

Before this refactoring...

we had an elevator application.

---

## 737

After it...

we have a substantially better understanding of elevator behavior.

---

## 738

Those are not the same thing.

---

## 739

And that understanding is reusable.

---

## 740

Imagine Product arrives tomorrow.

---

## 741

“We need fire-service mode.”

---

## 742

In the old model:

---

## 743

Add:

```java
boolean fireService;
```

---

## 744

Then spend the next three weeks discovering what that boolean actually means.

---

## 745

Now?

---

## 746

What event initiates fire-service mode?

---

## 747

Who may initiate it?

---

## 748

Which states may it interrupt?

---

## 749

What happens to the queue?

---

## 750

What happens to the doors?

---

## 751

How does it complete?

---

## 752

What commands become available?

---

## 753

Which affordances should appear?

---

## 754

What do the tests say?

---

## 755

The architecture gives us a vocabulary for product discovery.

---

## 756

A good domain model isn't primarily a nicer way to organize Java.

---

## 757

It's accumulated product understanding.

---

## 758

Unfortunately...

---

## 759

there is no:

```bash
npm run measure-domain-understanding
```

---

## 760

Which is inconvenient for this section of the talk.

---

## 761

But very convenient for our Product Manager.

---

## 762

Because when Product asks:

“What should happen if...?”

---

## 763

we can increasingly answer:

---

## 764

“Let's look at the specification.”

---

## 765

And the specification runs.

---

# `</product>`

# `<who-wins>`

---

## 766

So who wins?

---

## 767

The application wins.

---

## 768

Rules have one authoritative home.

---

## 769

The users win.

---

## 770

The interface reflects what they can actually do.

---

## 771

And we win.

---

## 772

Because changing the product increasingly means changing a coherent model...

rather than finding every copy of an assumption.

---

# `</who-wins>`

# `<attribution>`

---

## 773

I want to be careful about attribution.

---

## 774

Not everything good here came from DDD.

---

## 775

DDD gave us:

language,

value objects,

aggregate boundaries,

invariants,

commands,

events,

and a deliberate home for domain understanding.

---

## 776

Ordinary refactoring removed ordinary code smells.

---

## 777

CQRS gave us:

separation between behavior and views.

---

## 778

Events gave us:

explicit facts and server-owned transitions over time.

---

## 779

Hypermedia gave us:

current operations,

opaque URIs,

server-supplied fields,

authority-dependent capabilities.

---

## 780

Content negotiation gave us:

multiple representations.

---

## 781

HTML gave us:

a human-facing hypermedia representation without a second domain interpreter.

---

## 782

SSE gave us:

live server-driven updates.

---

## 783

These ideas reinforce each other.

---

## 784

But two are especially load-bearing.

---

## 785

**DDD gives us one place to understand the domain.**

---

## 786

**Hypermedia lets the client benefit from that understanding without copying it.**

---

## 787

And HTML is where that becomes almost embarrassingly literal.

---

## 788

The server knows what you can do.

---

## 789

So it sends you the control that lets you do it.

---

# `</attribution>`

# `<agents>`

---

## 790

There's another interesting consequence.

---

## 791

Agents.

---

## 792

An agent can have a static catalog of tools.

---

## 793

```text
openDoors()
closeDoors()
selectFloor()
enterMaintenance()
...
```

---

## 794

But which of those tools are useful **now**?

---

## 795

That's exactly the question our frontend had.

---

## 796

A hypermedia representation can say:

---

## 797

For this resource...

---

## 798

in this state...

---

## 799

with this authority...

---

## 800

these operations are currently available.

---

## 801

That's potentially useful to agents too.

---

## 802

But don't overclaim this.

---

## 803

A private relation called:

```text
urn:asbjorn:elevator:do-weird-thing
```

---

## 804

does not magically give an AI semantic understanding.

---

## 805

Hypermedia does not replace MCP.

---

## 806

It does not replace tool descriptions.

---

## 807

It does not replace shared vocabularies.

---

## 808

The modest claim is enough.

---

## 809

If the domain already knows an action is impossible...

---

## 810

...perhaps don't offer it to the agent.

---

# `</agents>`

# `<limits>`

---

## 811

Should every application work like this?

---

## 812

No.

---

## 813

Offline-first applications have different constraints.

---

## 814

High-frequency interactive applications have different constraints.

---

## 815

If you need client-side decisions at 60 frames per second...

don't round-trip every thought through a server.

---

## 816

Generic data APIs may genuinely be about data.

---

## 817

If client and server always deploy atomically...

some forms of coupling are less costly.

---

## 818

And this architecture has machinery.

---

## 819

Commands.

Events.

Projections.

Affordances.

Renderers.

Schedulers.

---

## 820

That cost is real.

---

## 821

This is also a nine-floor elevator demo.

---

## 822

It demonstrates architectural properties.

---

## 823

It does not economically prove that you should rebuild your bank like this.

---

## 824

And our bespoke state JSON format?

---

## 825

Useful for teaching.

---

## 826

Not necessarily something I would standardize the world on.

---

# `</limits>`

# `<before-and-after>`

---

## 827

Let's put the two systems next to each other.

---

## 828 — Before

```text
GET state
   ↓
interpret state
   ↓
apply copied rules
   ↓
decide controls
   ↓
construct URL
   ↓
perform mutation
   ↓
refetch
   ↓
interpret again
```

---

## 829 — After

```text
GET representation
   ↓
discover affordance
   ↓
follow affordance
   ↓
domain handles command
   ↓
events
   ↓
projection
   ↓
new representation
   ↓
new possibilities
```

---

## 830

For a machine:

hypermedia.

---

## 831

For a person:

HTML.

---

## 832

Same model.

---

## 833

Same possibilities.

---

## 834

Different representation.

---

## 835 — The real before/after

But the architectural diagram isn't actually the most interesting before and after.

---

## 836

Before:

**What field do we change?**

---

## 837

After:

**What behavior do we want?**

---

## 838

Before:

**Which endpoint do I call?**

---

## 839

After:

**What is possible now?**

---

## 840

Before:

**Where is that rule?**

---

## 841

After:

**What does the domain say?**

---

## 842

Before:

**Does the test pass?**

---

## 843

After:

**Is this really how the product should behave?**

---

# `</before-and-after>`

# `<knowledge>`

---

## 844

I said at the beginning that this talk was really about knowledge.

---

## 845

So let's measure coupling differently.

---

## 846

Who knows how many floors the building has?

---

## 847

Before:

server.

BFF.

store.

components.

---

## 848

After:

the domain.

---

## 849

Who knows when doors may close?

---

## 850

Before:

server.

frontend.

probably a button somewhere.

---

## 851

After:

the domain.

---

## 852

Who knows how overload affects floor selection?

---

## 853

Before:

server and client.

---

## 854

After:

the domain.

---

## 855

Who knows the elevator URL?

---

## 856

Before:

the client.

---

## 857

After:

the representation.

---

## 858

Who knows whether this user may enter maintenance?

---

## 859

Before:

authentication code,

frontend code,

backend code.

---

## 860

After:

authority reaches the domain.

The affordance exposes the consequence.

---

## 861

Who knows which button to show?

---

## 862

Before:

the frontend interprets the domain.

---

## 863

After:

the API renders the affordance.

---

## 864

That's the architectural change I care about.

---

## 865

Not fewer classes.

---

## 866

Not more patterns.

---

## 867

**Fewer copies of knowledge.**

---

# `</knowledge>`

# `<tests-as-knowledge>`

---

## 868

And tests now become part of that knowledge system.

---

## 869

Every interesting product question can become an executable example.

---

## 870

What happens when recall starts during movement?

---

## 871

Test it.

---

## 872

What happens when doors are obstructed?

---

## 873

Test it.

---

## 874

What can a rider do when overloaded?

---

## 875

Affordance test.

---

## 876

What can a technician do?

---

## 877

Affordance test.

---

## 878

What should the view show after FloorReached?

---

## 879

Projection test.

---

## 880

The suite accumulates understanding.

---

## 881

The old tests protected our implementation.

---

## 882

The new tests protect our understanding of the product.

---

# `</tests-as-knowledge>`

# `<closing>`

---

## 883

So this talk is not:

CRUD bad.

---

## 884

It's not:

JSON bad.

---

## 885

It's not:

Everyone should use Siren.

---

## 886

It's not:

Server-side HTML solves software.

---

## 887

It's about essential and accidental complexity.

---

## 888

The elevator has real complexity.

---

## 889

Movement.

Doors.

Obstruction.

Load.

Maintenance.

Recall.

Authority.

Time.

---

## 890

We can't delete that complexity.

---

## 891

But we can decide where it lives.

---

## 892

And we can stop copying it.

---

## 893

DDD gives us one place to understand the domain.

---

## 894

Hypermedia gives us a way to communicate what the domain permits.

---

## 895

HTML lets us communicate it directly to a person.

---

## 896

Tests give us a way to remember why.

---

## 897

And the metrics give us evidence that something structural changed.

---

## 898

But the metrics are not the whole value.

---

## 899

The thing I care most about is difficult to count.

---

## 900

We understand the product better than we did before.

---

## 901

And the code now contains more of that understanding.

---

## 902

I just can't prove it with `scc`.

---

## 903

Which is unfortunate.

---

## 904

But I think it's a pretty good trade.

---

## 905

The application wins.

---

## 906

The users win.

---

## 907

We win.

---

## 908

And perhaps this matters even more as code becomes cheaper.

---

## 909

Because if AI can produce another controller in seconds...

---

## 910

...then producing controllers is not our scarce skill.

---

## 911

Knowing whether the controller should exist...

might be.

---

## 912

Knowing what behavior it represents...

might be.

---

## 913

Knowing what the business means...

definitely is.

---

## 914

So when you go back to your own APIs...

ask one question.

---

## 915

**What does the client know that the server could know instead?**

---

## 916

Maybe it knows a URL.

---

## 917

Maybe it knows a business rule.

---

## 918

Maybe it knows which transition is legal.

---

## 919

Maybe it knows which values are permitted.

---

## 920

Maybe it knows which button should exist.

---

## 921

And when you find that knowledge...

---

## 922

don't immediately ask:

---

## 923

**How do I move this `if` statement to the backend?**

---

## 924

Ask:

---

## 925

**What does this knowledge tell me about my domain?**

---

## 926

Because that may be the more valuable discovery.

---

## 927

And if the domain knows what happens next...

---

## 928

let the domain decide.

---

## 929

Let the API tell the client.

---

## 930

Let the client follow.

---

## 931

And let the team...

---

## 932

understand why.

---

# `</closing>`

## 933 — Final slide

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "domain": "decides",
  "api": "tells",
  "client": "follows",
  "team": "understands"
}
```

`@asbjornu`
