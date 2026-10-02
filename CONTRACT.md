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

**Will the untouched consumer still compile and pass?** No. `lab06-consumer`
goes red at compile time, and its tests never run.

**Why.** Java checks every call against the API at build time: is there a
`createBooking` that takes these arguments? After the fold, `BookingApi` only
has `createBooking(BookingRequest)`, but the front desk still asks for the old
`(String, long, long, String)` shape. No method matches, so the compiler refuses
to build it. The consumer's code didn't change. The method it points at
disappeared, and that's what makes this a breaking change.

**Where.** The only two places the consumer creates a booking:
`FrontDesk.java:27` (`bookWalkIn`, key `null`) and `:33` (`joinWaitlist`, key =
guest name). Expect one error per line, something like `cannot be applied to
given types; required: BookingRequest`. The `listBookings` (`:39`) and
`cancelBooking` (`:48`, `:53`) calls don't change, so no errors there.

**What about the tests in `api/`, after you update them?** All 5 pass and
`lab06-api` is SUCCESS. Maven builds one module at a time (compile, compile
tests, run tests), and the api goes first, so it finishes green before the
consumer is even looked at. But that's **not** evidence about the consumer. The
api suite only checks the API against itself and never touches `FrontDesk`, and
we rewrote those tests to the new call ourselves. Green api means "the new
method works," not "nobody broke." Only the consumer's build can catch this,
and it will show no `Tests run` line at all, since its 7 tests never compile.

### Step 1: after the fold

**What the build printed.** `mvn -B clean test`, exactly as predicted:

`lab06-api`: compiled (now 5 source files, with `BookingRequest`) and all
5 tests passed, rewritten to `createBooking(BookingRequest.of(...))`.

```
[INFO] Compiling 5 source files with javac [debug deprecation release 21] to target/classes
[INFO] Compiling 1 source file with javac [debug deprecation release 21] to target/test-classes
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
```

`lab06-consumer`: recompiled against the new API and failed on the two
`createBooking` lines. The walk-in call passes 4 arguments, including a bare
`null`; the waitlist call passes the guest name. Neither matches the
one-argument method.

```
[INFO] Recompiling the module because of changed dependency.
[ERROR] COMPILATION ERROR :
[ERROR] consumer/src/main/java/edu/cmu/cs214/frontdesk/FrontDesk.java:[27,19] method createBooking in interface edu.cmu.cs214.booking.BookingApi cannot be applied to given types;
  required: edu.cmu.cs214.booking.BookingRequest
  found:    java.lang.String,long,long,<nulltype>
  reason: actual and formal argument lists differ in length
[ERROR] consumer/src/main/java/edu/cmu/cs214/frontdesk/FrontDesk.java:[33,19] method createBooking in interface edu.cmu.cs214.booking.BookingApi cannot be applied to given types;
  required: edu.cmu.cs214.booking.BookingRequest
  found:    java.lang.String,long,long,java.lang.String
  reason: actual and formal argument lists differ in length
[INFO] 2 errors

[INFO] lab06-booking-parent ............................... SUCCESS
[INFO] lab06-api .......................................... SUCCESS
[INFO] lab06-consumer ..................................... FAILURE
[INFO] BUILD FAILURE
[ERROR] Failed to execute goal ...maven-compiler-plugin:3.13.0:compile (default-compile) on project lab06-consumer
```

**Which module's tests ran, and which did not.** The api's 5 ran and passed.
The consumer's 7 never ran. Maven stopped the consumer at `compile`, so its
tests were never even compiled, and there's no `Tests run` line for it. No
consumer test failed. Its whole suite was skipped.

So the only thing that caught the break was the **consumer's build**. Our own
suite was green the whole time, because it checks the API against itself and
we updated it ourselves. A producer can't prove a change is safe from their
own tests, since the people who get hurt are the ones whose code they don't
see. We only saw it because their build runs inside ours. In real life, the
front desk team would find out the day they upgraded.

### Step 2: the deprecation path

**What you added.** Both old signatures, back on `BookingApi` as `@Deprecated`
`default` methods:

```java
@Deprecated
default Booking createBooking(String roomId, long startMinute, long endMinute,
                              String waitlistKey)
@Deprecated
default Booking createBooking(String roomId, long startMinute, long endMinute,
                              String waitlistKey, String notes)
```

Neither has logic of its own. Each one packs its arguments into a
`BookingRequest` and calls the new method, e.g.
`createBooking(BookingRequest.of(roomId, startMinute, endMinute).withWaitlistKey(waitlistKey))`.
So there's still exactly one real implementation, and old and new callers
can't drift apart. Because they're `default` methods, `InMemoryBookingService`
(or anyone else implementing the interface) gets them for free. Their
`@deprecated` javadoc names the replacement call.

**The warnings.** The consumer's two errors from step 1 are now two warnings,
on the same lines:

```
[WARNING] consumer/src/main/java/edu/cmu/cs214/frontdesk/FrontDesk.java:[27,19] createBooking(java.lang.String,long,long,java.lang.String) in edu.cmu.cs214.booking.BookingApi has been deprecated
[WARNING] consumer/src/main/java/edu/cmu/cs214/frontdesk/FrontDesk.java:[33,19] createBooking(java.lang.String,long,long,java.lang.String) in edu.cmu.cs214.booking.BookingApi has been deprecated
```

What changed in the build output from step 1 to step 2:

| | Step 1 | Step 2 |
|---|---|---|
| `lab06-api` | 5 tests pass, SUCCESS | 5 tests pass, SUCCESS, no warnings |
| `lab06-consumer` compile | 2 **errors** (`:27`, `:33`) | 2 **warnings** (`:27`, `:33`) |
| `lab06-consumer` tests | never ran | `Tests run: 7, Failures: 0` |
| Overall | BUILD FAILURE | BUILD SUCCESS |

**What the deprecation path resolves.** The front desk team can build again,
with zero changes to their code, and all 7 of their tests pass. Old and new
calls now work side by side, so each team moves on its own schedule:

- **Us (API owners):** `createBooking(BookingRequest)` is live now, and our own
  code and tests already use it.
- **Them (front desk):** they keep shipping on the old calls and switch over
  whenever they're ready, two lines in their case.
- **Later:** once callers have migrated, the deprecated methods can be removed
  in a future major version. That's the real break, but by then nobody depends
  on them.

**What the warnings accomplish that a README note would not.** A README only
works if someone goes and reads it, and even then it doesn't know which of
*their* lines are affected. The warning comes to them. It's printed in their
own build log every time they compile, it names the exact file and line
(`FrontDesk.java:[27,19]`), and their IDE strikes through the call as they type
it. The `@deprecated` javadoc then tells them what to use instead, right where
they're looking. It also can't go stale: the warning is attached to the method
itself, so it's there for as long as the old method is, and it disappears from
their log the moment they've migrated.

---

## Milestone 3: The misuse critique

Not coded. One misuse, one redesign, one cost. Discuss it with your TA.

### The misuse

**What is easy to get wrong.** In `createBooking`, the waitlist key does two
jobs at once. Its *value* is a label stored on the booking (the front desk uses
the guest's name), and *whether it's `null`* is the on/off switch for
waitlisting: `null` means "if the room is busy, give up," anything else means
"if the room is busy, put me in line." The compiler can't tell a deliberate
`null` ("don't waitlist") from an accidental one ("we don't know the name").

**The call site.** Two lines in `FrontDesk.java`, one for each half of the
problem:

```java
// :27  bookWalkIn: unreadable without the javadoc. What does null mean?
return api.createBooking(roomId, startMinute, endMinute, null);

// :33  joinWaitlist: the guest's name *is* the switch
return api.createBooking(roomId, startMinute, endMinute, guestName);
```

At `:33`, if `guestName` is ever `null` (a blank form field, a guest who didn't
give a name), "join the waitlist" silently becomes "walk-in." The compiler is
happy, since `null` is a legal `String`.

**What goes wrong when it happens.** Tried in a throwaway copy (book the room,
then `joinWaitlist("Oak", 570, 630, null)`):

```
joinWaitlist returned: null
schedule: [09:00-10:00  CONFIRMED]
java.lang.NullPointerException: Cannot invoke "Booking.getStatus()" because "queued" is null
```

1. **Silent wrong behavior.** The guest asked to wait in line and was quietly
   turned away. No exception, no error.
2. **Lost data.** They're not in the schedule, so when the room frees up,
   nobody gets promoted. The desk thinks someone is waiting, and the system
   has no record of them.
3. **A crash far away.** `createBooking` returns `null`, so the error shows up
   later, wherever someone first uses the result, far from the blank name that
   caused it.

It's sneaky because it **works whenever the room is free** (no conflict, so
the key is ignored and you get CONFIRMED). It only fails when the room is busy,
which is the one case `joinWaitlist` exists for.

Milestone 2's `BookingRequest` doesn't fix it either.
`.withWaitlistKey(guestName)` reads better than a bare `null`, but
`withWaitlistKey(null)` still quietly means "don't waitlist." Better names
didn't fix it. The fix has to come from types the compiler checks.

### The redesign

**The proposal.** Split the two jobs apart, so each decision has its own place.

1. **Two methods, one per intent.** Waitlisting is decided by *which method you
   call*, not by whether a value happens to be `null`.
2. **A `WaitlistKey` type that can't be blank.** It checks itself the moment
   it's created.
3. **No more `null` returns.** "Room taken" becomes part of the return type.

```java
// BookingApi
Optional<Booking> bookIfFree(String roomId, long startMinute, long endMinute);
Booking bookOrWaitlist(String roomId, long startMinute, long endMinute, WaitlistKey key);

// The key validates itself
public record WaitlistKey(String value) {
    public WaitlistKey {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("waitlist key must not be blank");
        }
    }
}
```

The new call sites in `FrontDesk.java`:

```java
// :27  bookWalkIn: the method name says "don't waitlist", no mystery null
return api.bookIfFree(roomId, startMinute, endMinute);

// :33  joinWaitlist: the name is checked before anything is booked
return api.bookOrWaitlist(roomId, startMinute, endMinute, new WaitlistKey(guestName));
```

**Why the mistake is now hard or impossible to make.** Each mechanism fixes
one of the three problems above:

| The problem | Before | After | What enforces it |
|---|---|---|---|
| A blank name flips the mode | `joinWaitlist(…, null)` silently became a walk-in | `new WaitlistKey(null)` throws **right at `:33`**, before anything is booked | **Validating constructor**: an invalid key can't exist |
| A reader can't understand `:27` | You need the javadoc to know what `null` means | `bookIfFree` says it in the name | **Method choice at compile time**: no runtime value can change the mode |
| The crash shows up far away | Returns `null`, which crashes later on `.getStatus()` | Returns `Optional.empty()`, which you can't call `.getStatus()` on | **The compiler**: an `Optional` must be unwrapped, so the empty case has to be handled |

In plain terms, no value silently means "actually, don't waitlist" anymore.
To waitlist, you call the waitlist method, and it won't take a missing name.
The mistake goes from **silent and far away** to **loud and right where it
happened**.

**The honest limit:** Java can't stop a literal `bookOrWaitlist(…, null)`, so
the method rejects it immediately with `Objects.requireNonNull`. The difference
is that `null` no longer *means* anything. Before, it was a valid instruction
the API quietly obeyed. Now it's always a bug, and it fails at the call. That's
"hard to get wrong," not "impossible."

### One tradeoff

**What it costs.** Callers have to migrate twice. We just asked the front
desk to move to `createBooking(BookingRequest)` (that's what the step 2
deprecation warnings point at), and this redesign replaces that method too. So
they get a **second round of deprecation warnings right after the first**, and
anyone who already migrated does it again. It's also more than renaming two
lines:

- **`:27` changes its return type.** `bookIfFree` returns `Optional<Booking>`
  instead of a `Booking` that might be `null`. That ripples into
  `FrontDesk.bookWalkIn`'s own signature and into their test
  `assertNull(desk.bookWalkIn(...))` at `FrontDeskTest.java:33`. Either they
  change their own public method, pushing the change onto *their* callers, or
  they add `.orElse(null)`, which brings the `null` right back at their
  boundary.
- **`:33` now throws where it used to "work."** A nameless guest used to slip
  through silently as a walk-in. Now `new WaitlistKey(null)` throws. That's the
  point, but code that ran without exceptions now fails, and the team has to
  decide what the desk does for a guest with no name.
- **For a while there are five ways to create a booking:** the 2 deprecated
  positional methods, the newly deprecated `createBooking(BookingRequest)`,
  `bookIfFree`, and `bookOrWaitlist`. We maintain all five until removal, and
  a newcomer has to figure out which one is current.

**When the price is worth paying.** Under two conditions:

1. **The API is young and has few callers.** This one says "version 0" in its
   javadoc and has one consumer, whose change is two lines. Breaking it now is
   cheap; breaking it after a dozen teams depend on it is not. And the mistake
   isn't cosmetic: a guest who silently drops off the waitlist is a real person
   turned away while the room sits empty.
2. **It ships in the same migration as the Milestone 2 fold**, so callers
   migrate once, not twice. In hindsight, the better move was to go straight
   from the positional methods to `bookIfFree` / `bookOrWaitlist` in a single
   deprecation cycle. `BookingRequest` fixed readability, not the mistake.

It's **not** worth it on a mature API with many callers you can't reach, for a
mistake that's rare or harmless. There, a clearer javadoc plus a
`requireNonNull` check is the cheaper fix, and the redesign waits for the next
major version, bundled with other breaking changes.
