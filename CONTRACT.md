# Contract Worksheet

One section per milestone. Fill each one in as you go, in order. Write each
prediction before you run anything. That is the part a TA asks about.

Keep it short and specific. Point at methods, call sites, and error text.

---

## Milestone 1: The notes overload

### Prediction (write this before you run the build, and you can deliberate with your agent)

**Will the consumer, untouched, still compile and pass?** Yes.

**Why.** The consumer calls `createBooking` in two places, both with 4
arguments: `FrontDesk.java:27` (walk-in, key `null`) and `:33` (waitlist, key =
guest name). When Java picks which `createBooking` to run, it first filters by
name and number of arguments, so the new 5-argument version is never even a
candidate. Both calls keep landing on the same 4-argument method, which keeps
its javadoc promise (it just passes `notes = null` along).

Nothing else the consumer touches changes either. `Booking`'s constructor is
package-private, so the consumer can't call it. `getNotes()` is new and unused.
Nothing in `consumer/` implements `BookingApi`, so a new interface method costs
it nothing. From the front desk's side, nothing happened.

### What happened

**The result.** Green across the board (`mvn -B clean test`):

```
lab06-api       Compiling 4 source files ... Tests run: 5, Failures: 0, Errors: 0
lab06-consumer  Compiling 1 source file  ... Tests run: 7, Failures: 0, Errors: 0
                (no errors, no warnings)
lab06-booking-parent ... SUCCESS
lab06-api .............. SUCCESS
lab06-consumer ......... SUCCESS
BUILD SUCCESS
```

The consumer was recompiled against the new API and didn't notice. Same
calls, same method, same behavior. It just doesn't use notes yet.

**If your prediction was wrong:** it wasn't. One caveat:
green only proves nothing broke. The api suite still has 5 tests and none of
them calls the notes overload or `getNotes()`.

**Is an additive change always safe in Java?** No. It was safe here because of
two facts about this consumer, and changing either one breaks it:

- **Same argument count + `null`.** If the overload had been
  `createBooking(String, long, long, Notes)` (a new `Notes` type, still 4
  arguments), the walk-in call at `FrontDesk.java:27` passes a bare `null`,
  which fits both a `String` and a `Notes`. Java can't pick, so it fails to
  compile on a line we never touched. Tried in a throwaway copy:
  `FrontDesk.java:[27,19] reference to createBooking is ambiguous`.
  `:33` still compiled, since `guestName` is declared as a `String`.
- **Someone implements the interface.** A new abstract method on `BookingApi`
  breaks any outside class that `implements BookingApi` ("does not override
  abstract method"). The front desk only *uses* the interface, so it was spared.
  A `default` method would avoid this.

---

## Milestone 2: The request object

### Prediction (write this before you run the build)

**Will the untouched consumer still compile and pass?** Yes or no, and if no,
which module goes red and whether at compile time or test time.

**Where.** Name the call sites you expect to be affected, if any.

**What about the tests in `api/`, after you update them?** And whether their
result is evidence about the consumer.

### Step 1: after the fold

**What the build printed.** Paste it for each module, including file and
line for anything that failed.

**Which module's tests ran, and which did not.** And what that tells you about
who can detect a contract break.

### Step 2: the deprecation path

**What you added.** The signatures that came back, and what they delegate to.

**The warnings.** Paste one deprecation warning line from the build log (from
a `mvn -B clean test` run, since a rerun with nothing to compile prints none).

**What the deprecation path resolves.** Who can now build that could not build
during step 1, and who is on which schedule.

**What the warnings accomplish that a README note would not.** Be concrete
about where the warning shows up and who sees it without looking for it.

---

## Milestone 3: The misuse critique

Not coded. One misuse, one redesign, one cost. Discuss it with your TA.

### The misuse

**What is easy to get wrong.** One specific thing about the API surface.

**The call site.** File and line in `consumer/`, with the call. Show the
code that a reader cannot understand without opening the javadoc, or that a
caller could get wrong with the compiler still happy.

**What goes wrong when it happens.** Silent bad behavior, wrong data, a crash
somewhere far away?

### The redesign

**The proposal.** Types, enums, factories, or whatever you are proposing. Show
the new signature and the new call site.

**Why the mistake is now hard or impossible to make.** Point at the mechanism,
such as the compiler, a validating constructor, or an exhaustive switch.

### One tradeoff

**What it costs.** Something real, such as caller ceremony, migration burden
against the deprecation path you just built, or more types for a newcomer to
learn. "No real downside" does not count.

**When the price is worth paying.** A condition under which it is.
