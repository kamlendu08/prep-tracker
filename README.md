# Prep Tracker

An Android app for GATE preparation days: how many hours went into studying, and how much money
went out. Built for a Galaxy S23, runs on any phone with Android 10 or newer.

Companion to the GATE exam platform at `~/codeit/gate-exam-platform` (deployed at
**https://gatexprep.vercel.app**) — that one is what you study on, this one records whether you
actually sat down. They share one account and one Neon database.

This file is the handover document: it should be enough to pick the project up cold.

---

## 1. What it does

**Study timer.** Pick what the sitting is for — Lecture, Practice or Test — and the screen becomes
a full-bleed black stopwatch in the style of the Giant Stopwatch app. The digits are grey by
default; Settings → *Clock colour* swaps them for neon green, aqua, amber or violet. Each palette
keeps the grey one's three steps and its relative brightness — the dimmed shade is about 61% of the
bright one, the paused shade about 53% — so "Dim the clock" reads as the same clock turned down
rather than a different colour, and none of them is a fully saturated hue at full brightness,
which on an OLED panel at a desk at 3am glares exactly the way white does. Tap
anywhere to pause, tap again to resume, "Stop & log" banks it against today.

The clock **pauses itself whenever you stop looking at it**: home button, another app, another tab,
the lock button, or an incoming call. The one exception is resuming from the notification, which is
an explicit instruction to keep counting while the phone is used for something else — the
studying-from-a-paper-book case.

While a sitting runs: the screen stays awake, Do Not Disturb goes on with **calls still allowed
through**, and the session lives in a foreground service with pause/stop controls in the shade.

The Timer tab also lists every sitting logged, grouped by day. Tapping one offers **Resume** —
which continues that same sitting rather than starting a second one — or Delete, with a
three-second undo.

**Expenses.** Debits are read from **bank SMS only** (see §4), categorised by payee, and checked
against **two** budgets: a daily limit, and an optional **overall budget** — the whole pot set
aside for the preparation months, shown on the dashboard as a bar with a rough runway estimate
("about 47 days left at your recent rate"). Everything is correctable by hand, and deleting offers
a three-second undo.

**Reports.** Six ranges — 7 days, 30 days, 3 months, 6 months, 1 year, all time — with per-day and
per-study-day averages, days studied, days on target, current and best streak, trend against the
previous equal period, the split by activity and category, which weekday you actually study on,
longest sittings and biggest spends.

**Home-screen widget.** Today's study time counting down to the target and today's spend against
the limit, as thick gradient bars. It includes a sitting *in progress*, refreshed each minute.

---

## 2. Build, install, deploy

The toolchain on this Mac is command-line only — **Android Studio is not installed**. Both exports
are needed in every shell, because `openjdk@21` is keg-only:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools

./gradlew installDebug        # build + push to the connected phone
./gradlew testDebugUnitTest   # 37 tests, all parser/dedup logic
./gradlew lintDebug
./gradlew assembleRelease     # ~2.4 MB after R8
```

**On a fresh clone** you also need `local.properties`, which is machine-specific and deliberately
not committed:

```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties
```

Installed by the original setup: `brew install openjdk@21`,
`brew install --cask android-commandlinetools`, then
`sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"`. Gradle wrapper 8.9,
AGP 8.7.3, Kotlin 2.0.21, compileSdk 35, minSdk 29.

The phone connects over USB with developer options + USB debugging on. `adb` lives at
`$ANDROID_HOME/platform-tools/adb`.

**Driving the phone from adb has one trap worth knowing:** when the stopwatch face rotates to
landscape, injected taps land in the wrong coordinate space and silently miss. Real finger taps are
unaffected. Verify UI behaviour through `dumpsys notification` (the timer's title says
`Studying — …` or `Paused — …`) and `uiautomator dump` rather than trusting a screenshot alone.

**Server side** lives in the other repo. `git push origin main` triggers the Vercel deploy;
migrations are applied with `npx prisma migrate deploy` (never `migrate dev` against production).

---

## 3. Layout

```
app/src/main/java/dev/kamlendu/preptracker/
  PrepApp.kt              Application + the one dependency container (no DI framework)
  MainActivity.kt         Auth gate, bottom tabs, foreground sync trigger
  data/                   Room entities, DAOs, database + migrations, DataStore settings
  timer/
    TimerEngine.kt        Stopwatch arithmetic, persisted across process death
    TimerService.kt       Foreground service, shade controls, screen-off and call pausing
    FocusMode.kt          Do Not Disturb save / set / restore, persisted to disk
  expense/
    TxnParser.kt          SMS text -> amount, payee, category, fingerprint
    Deduper.kt            Whether two rows are the same real payment
    ExpenseCapture.kt     The single funnel every captured spend goes through
    SmsReceiver.kt        Live bank SMS
    SmsImporter.kt        Inbox back-fill, re-parse, duplicate sweep
  sync/
    AuthStore.kt          Token + account, in DataStore
    SyncApi.kt            The three HTTP calls (login, register, sync)
    SyncEngine.kt         Push pending rows, pull server changes, last-write-wins
  ui/
    auth/                 Landing page, sign-in, sign-up
    theme/  components/   Colours, typography, charts drawn on Canvas (no chart library)
    dashboard/  timer/  expenses/  reports/  settings/
  widget/PrepWidgetProvider.kt
```

---

## 4. Decisions, and why they are that way

These are the ones that will look arbitrary without the reason.

**Kotlin + Compose, not Flutter or React Native.** Reading SMS, controlling Do Not Disturb, a
foreground service, keeping the screen awake — all native APIs with no first-party cross-platform
binding. A cross-platform stack would mean writing all of it in Kotlin anyway, behind a bridge.

**Time is measured, never counted.** The stopwatch reads `elapsedRealtime()` and subtracts; it
never accumulates ticks. A UI that counts frames loses minutes when the screen sleeps. It redraws
on the second boundary — one wake per second, not five.

**A session is only credited while something is watching it.** The service writes a heartbeat every
minute. If it is killed — app update, force-stop, low-memory — the clock comes back *paused*,
crediting only up to the last heartbeat, and says so on screen. A reboot is handled the same way.
`MainActivity.onStart` re-attaches a surviving session to a new foreground service.

**Focus mode is restored from disk, not memory.** DND is a global system setting. Keeping the
"previous value" in memory meant a killed process left the phone silenced indefinitely, so the
previous filter and policy are written to disk when focus mode goes on, and restored on next launch
if the session that owned them is gone.

**"Dim the clock" dims the digits, not the backlight.** A window brightness override is an absolute
level, not a reduction: asking for 0.35 on a phone already at 15% makes it *brighter*. On an OLED
panel, dimmer pixels are also what actually saves power.

**SMS only, deliberately.** An earlier version also read notifications (bank email, UPI apps). One
Swiggy order paid with Amazon Pay produced a payment email *and* an order-confirmation email, both
quoting the same ₹232, naming two different "merchants" — and no dedup rule could reliably tell
them apart from two real payments. Watching one channel makes the problem tractable. **The cost:**
anything paid without a bank SMS (wallet balance, Pay Later) is not captured and must be added by
hand.

**One payment, however many messages** (`Deduper`):
- two different bank references are two different payments, full stop;
- otherwise the same amount within **5 minutes** is one payment whatever the text says;
- between 5 and 15 minutes it merges only on evidence — payees agree, one side named no payee, or
  the two arrived through different channels.

The bias is deliberate: missing a genuine second payment of the identical amount minutes apart is
fixable by hand; counting one payment twice corrupts the budget invisibly.

**The parser refuses what it cannot read.** OTPs, credits, balance summaries, uncharged mandates,
order confirmations, failed payments and loan marketing are all filtered out, and a message with no
clean amount is dropped rather than guessed at. Bank-specific patterns come first: Axis puts the
payee in `UPI/P2M/<ref>/<NAME>` or on a bare line above "Avl Limit", Kotak writes "Sent Rs.X from
A/c to NAME". All of these are pinned in `RealBankSmsTest` against real messages.

**A card purchase counts on the day you make it; paying the card bill does not.** Settling a credit
card bill is filed as `CARD_BILL` and excluded from every total — those purchases already hit the
budget when they happened. The rule matches on the *payee* only, because a Kotak purchase message
also contains the words "Credit Card".

**Money is stored in paise.** Integer arithmetic; rupee floats drift over a month of small
transactions and the limit maths has to be exact.

**Deletes are tombstones.** A row that simply vanished is indistinguishable from one that never
existed, which decides whether a reinstall restores it. It is also what makes undo possible.

**Resuming a sitting extends its row rather than writing a second one.** `TimerState` carries
`resumedSessionId` and `carriedMs`; the clock face shows `displayMs()` (carried + new) while
anything adding live time to a stored total uses `elapsedMs()` (new only), because the carried time
is already in the database. Getting that backwards double-counts. Resume is offered for **today's**
sittings only: study time is filed by day, so adding tonight's hour to Tuesday's sitting would
credit it to Tuesday.

**The runway estimate uses the last seven days, not all time.** The pot is being spent at the
current pace, not at the average of a month that may have looked nothing like it.

**Message text never leaves the phone.** The server schema has no column for it. What syncs is
amount, payee, category and time.

---

## 5. Account and sync

Accounts are the **same as the GATE platform**. Three endpoints, all `runtime = "nodejs"`:

| Endpoint | Behaviour |
| --- | --- |
| `POST /api/mobile/register` | Creates the account, returns a token — no second sign-in |
| `POST /api/mobile/login` | Email matched case-insensitively; one generic error for bad email and bad password |
| `POST /api/mobile/sync` | Push changes + pull anything newer since the last sync |

Tokens are HS256 signed with the platform's existing `AUTH_SECRET`, valid 180 days, written by hand
in `src/lib/mobileToken.ts` rather than adding a JWT dependency for two fields and a signature;
signatures are compared in constant time.

Sync rules: row ids are generated **on the phone**, so a retried request updates rather than
duplicates; the later `updatedAt` wins on both sides; every write is scoped by `userId` *before*
touching a row, so a client-supplied id cannot reach another account's data.

Syncs run when the app comes to the foreground, right after a sitting is logged, and from
Settings → Account → Sync now. Signing out clears this phone's copy — after a final sync — so one
account's records are never left behind for the next person who signs in.

Postgres tables: `StudySession` and `SpendEntry`, both keyed to `User`, in the same Neon database as
the exam platform.

---

## 6. Permissions

| Permission | Where | What breaks without it |
| --- | --- | --- |
| Notifications | asked on first launch | The stopwatch has no shade controls |
| Do Not Disturb access | Settings tab → Grant | Focus mode cannot mute anything |
| SMS | Settings tab → Grant | No expense capture at all, no back-fill |

On One UI, keep the app out of **Settings → Battery → Background usage limits → sleeping apps**.

`READ_SMS` and `RECEIVE_SMS` are Play-restricted permissions granted only to default SMS handlers,
so this build installs by sideloading and cannot be listed on the Play Store. Irrelevant for
personal use; worth knowing before planning a release.

---

## 7. Testing

`./gradlew testDebugUnitTest` — 37 tests, all on the logic that can silently corrupt data:

- `RealBankSmsTest` — Axis and Kotak message templates with the amounts and payees they must
  produce, plus the messages that must be **refused**. Names, account digits, references and
  amounts are stand-ins; the *structure* is verbatim, which is what the regexes actually read.
  Real transactions do not belong in a repository.
- `TxnParserTest` — generic shapes, category rules, fingerprints.
- `DeduperTest` — one purchase arriving as several messages, and two real payments that must stay
  two.

Add a test whenever a new message shape turns up; the parser is regex and regressions are silent.

---

## 8. Known gaps

- **Anything paid without a bank SMS is not captured** (see §4) — add by hand from the Money tab.
- Sync only runs in the foreground; there is no background worker, so a phone that never opens the
  app never uploads. Fine in practice, wrong in principle.
- Focus mode on *resume* was added late and could not be verified from adb; worth a finger check.
- Rows captured by the old notification path are still in the database, labelled `Notification`.
- The `Deduper` windows (5 and 15 minutes) are judgement calls, not measurements.
