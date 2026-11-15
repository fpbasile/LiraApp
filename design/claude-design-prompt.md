# Lira — UI/UX redesign brief

## What Lira is
Lira is a personal finance tracker for Android (Kotlin + Jetpack Compose). People use it to log expenses and income right after a purchase, keep spending under daily/weekly/monthly limits, let recurring bills post automatically, and track spending while travelling in other currencies.

The app works, but logging currently feels like **filling out a form**: an "Enter amount" field followed by labeled rows (Category, From who, Time / Date, Currency, Description) and an ADD button. It's clean but tedious. **The goal of this redesign: logging a purchase should take ~3 seconds and feel quietly satisfying, something I look forward to doing after a purchase, not a chore.**

## Design direction

### Mood
- **Dark mode only.** No light theme.
- **Minimal, but not sterile.** Clean and calm, with warmth from typography, motion and small moments of feedback.
- **Monochrome UI with meaningful color.** Everything structural (text, buttons, keypad, navigation, cards) is white and grays on a near-black base. Color appears **only where it means something**:
  - **Accent: electric lime (~`#C6F432`)**, used sparingly for positive moments: the save confirmation, the "left to spend" number when on track, the budget ring filling, the streak.
  - **Muted category colors**: each category gets a soft, desaturated tone plus an icon, so categories are recognizable at a glance.
  - **Red only for actually going over a limit.** No guilt-red progress bars by default.
- At a glance the app should read as black-and-white; color is the reward.

### Typography
- Numbers are the hero. Large, confident amounts in a font with **tabular figures** (the current font is Inter; feel free to propose a pairing, e.g. a distinctive numeric/display face with Inter for UI text).
- Hierarchy comes from size, weight and opacity, not boxes and borders.

### Motion and feedback
- Motion only where it carries meaning: amounts counting up, the ring filling, the saved entry sliding into today's total, smooth sheet transitions.
- Haptic tick on save (note it in the specs).
- Nothing blocks the user. Feedback lasts under a second and never needs dismissing.

### Playfulness level: subtle
- **No mascot, no badges, no confetti.**
- **Logging streak**: a quiet "12 days" next to the budget ring. It rewards the habit of logging, not how little was spent.
- **Weekly recap card**: one well-designed card at the start of each week ("Your week in Lira": total, top category, one insight such as "20% less on food than last week").
- Positive framing everywhere: "€18 left today" instead of "€32 / €50 spent".

## Logo and app icon
The current logo is the wordmark "LIRA" in white on near-black, where the "L" looks like a paperclip. It gets too small to read as a launcher icon, and the paperclip suggests attachments more than money. Please design:
- **A simple monogram/mark** that works as an Android adaptive launcher icon (foreground + background layers, legible at 48dp, works inside circle and squircle masks) and as a monochrome themed icon (Android 13+).
- **A wordmark** for the login/splash screen that pairs with the mark.
- Same system as the app: white/near-black, with the lime accent allowed as a small detail at most.
- Optional exploration: one version that keeps a nod to the paperclip-L, and one that drops it completely.

## Core experience (design in depth)

### 1. Home = quick add
Opening the app **is** logging. No dashboard to get through first.
- A **large amount display** with a **custom numeric keypad** takes most of the screen. The user types `12.50`, taps a category, and it's saved.
- **Category chips** above the keypad: the 4–5 most-used categories first (ordered by usage and time of day), plus a "more" chip that opens the full list.
- **Everything else is an optional chip**, pre-filled with smart defaults: date/time (default: now), currency (default: home currency, or the active trip's), description/note. Tapping a chip edits it inline or in a small sheet.
- **Expense / income toggle**, lightweight (segmented control or swipe). Income has a "from who" field instead of a category-first flow.
- **"Repeat last" suggestion chip** (e.g. "Coffee · €3.20 again?") for one-tap logging of frequent purchases.
- **Scan receipt** is an equal entry point next to the keypad (camera icon), not hidden in a dialog. The flow: photo → AI extracts merchant/amount/date/category → the user reviews the pre-filled quick-add screen and confirms.
- **Top strip**: "€18 left today", a small budget ring, and the streak. Tapping it or swiping up opens the Today sheet.

### 2. The save moment
Design this carefully, it's the emotional core of the app:
- Haptic tick, the amount animates/slides into the "left today" number, the ring fills a bit in lime, and the keypad resets instantly for the next entry.
- An undo affordance for a few seconds.
- An over-limit variant: calm, informative, red used only here.

### 3. Today sheet / timeline
Pulled up from Home.
- Budget ring with daily / weekly / monthly limits (swipeable or stacked).
- Today's entries as a clean timeline (time, category icon/color, description, amount). Tap to edit; swipe to delete.
- Upcoming recurring payments for today/this week, shown as softer "scheduled" entries.

### 4. Insights (replaces the separate Calendar and Stats screens)
- Tells a story instead of only showing charts: short written insights first ("Weekends cost you 2× your weekdays", "Food is up 15% this month"), then the charts.
- Contains: a month calendar with per-day spend/income/recurring indicators (tap a day to see entries), the spending trend, the category breakdown (donut + bars), and spending by day of week.
- The weekly recap card lives here (and appears once on Home at the start of the week).

### 5. Travel mode
When a trip is active, the **whole app shifts context** instead of the user going to a separate tab:
- A trip banner on Home (e.g. "🇯🇵 Japan · JPY") with a distinct but still restrained treatment.
- Quick-add defaults to the trip's local currency, with a live conversion back to the home currency shown under the amount.
- Trip view: per-trip totals converted to the home currency, a breakdown by category, and a built-in quick converter.
- Creating, activating and ending a trip should be quick. A trip can have multiple currencies.

### 6. Home screen widget (Jetpack Glance)
- "€18 left today" plus a small ring, with today/week/month toggles.
- One-tap quick add (opens the keypad) and one-tap scan (opens the camera).
- Must work at small (2×1/2×2) and medium (4×2) sizes; tap targets ≥ 48dp.

## Secondary screens (same system, lighter detail)
Make sure these follow the same design system so the app feels like one piece:
- **Login / sign up**: wordmark, minimal, fast.
- **More / Settings**: profile, home currency (EUR/USD display), spending limits (daily/weekly/monthly), manage categories, recurring payments & income, currency converter, account deletion.
- **Manage categories**: add, rename, delete, pick color + icon.
- **Recurring**: list of recurring payments/income (daily/weekly/monthly/yearly, optional end date), each showing its next post date; add/edit flow reusing the quick-add style.
- **Currency converter**: ~380 currencies, swap, searchable currency picker.
- **Edit transaction**: reuses the quick-add layout, pre-filled.
- **Empty states**: first launch, no entries today, no trips. Friendly, one line, one action.

## Navigation
- Suggest a minimal bottom nav (e.g. **Home · Insights · Travel · More**), or argue for fewer tabs if Travel works better as a mode. Home must always be one tap away.

## Deliverables
1. **A small design system first**: color tokens (base, surfaces, text opacities, lime accent, category palette, over-limit red), type scale including number styles, spacing, corner radii, and core components (amount display, keypad, category chip, option chip, budget ring, timeline row, card, bottom sheet, nav bar, buttons).
2. **The logo and app icon** (see above).
3. **High-fidelity Android screens** (~412×915) for the core experience: Home/quick-add (empty, typing, category picked), the save moment (as a short sequence of frames), Today sheet, Insights, Travel mode (Home with an active trip + trip view), weekly recap card, and the widget in two sizes.
4. **Secondary screens** listed above.
5. **Motion notes**: short descriptions of each key animation (duration, easing, what moves).

## Constraints
- It will be built in **Jetpack Compose with Material 3** as a base, so prefer components that map cleanly to Compose (custom styling is fine; avoid effects that are impractical on Android, such as heavy background blur on every surface).
- Edge-to-edge layout: account for the status bar and the gesture navigation area.
- Accessible contrast for text on the dark base; never rely on color alone (category = color + icon; over-limit = red + text).
- Amounts must handle large numbers and different currency formats (€1.234,56 / $1,234.56 / ¥123,456).
