# Lira

A personal finance tracker for Android — log expenses and income, watch spending against a budget, and let recurring bills post themselves.

Built with Kotlin, Jetpack Compose, and Supabase.

## Features

- **Log expenses and income** with categories, descriptions, and custom timestamps
- **Receipt scanning** — snap a photo, Gemini AI extracts the merchant/amount/date/category, you review and confirm before it's saved
- **Recurring payments and income** — set up a schedule (daily/weekly/monthly/yearly, with an optional end date) and it auto-posts real transactions when they come due, no manual re-entry
- **Calendar view** — month grid with spend/income/recurring indicators per day, tap a day to see what happened
- **Stats** — spending trend charts, category breakdown (donut + bars), spending by day of week
- **Spending limits** — daily, weekly, and monthly, with progress bars on the home screen
- **Home screen widget** — today/week/month spending at a glance, with a one-tap camera shortcut straight into receipt scanning
- **Categories** you manage yourself — add, rename, delete
- **EUR/USD** currency display (on-device preference, not a real conversion)

## Tech stack

- **UI**: Jetpack Compose, Material3, dark theme only
- **Backend**: [Supabase](https://supabase.com) — Postgrest (database), Auth, Edge Functions
- **AI**: Google Gemini, called from a Supabase Edge Function for receipt OCR/extraction
- **Widget**: [Jetpack Glance](https://developer.android.com/jetpack/androidx/releases/glance)
- **Min SDK**: 26 (Android 8.0)

## Setup

### 1. Supabase project

You'll need your own Supabase project with:

- Tables for transactions, categories, recurring entries, and spending limits, matching the shapes in `app/src/main/java/com/app/lira/data/repository/`
- Row Level Security policies scoping every table to `auth.uid() = user_id`
- An Edge Function (see `app/src/main/java/com/app/lira/data/repository/ReceiptRepository.kt` for the expected request/response shape) with a `GEMINI_API_KEY` secret configured, for receipt scanning

### 2. Local credentials

Add your Supabase project's URL and anon key to `local.properties` (already gitignored — never commit real credentials):

```properties
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_KEY=your-anon-key
```

These are picked up automatically at build time via the Secrets Gradle Plugin and exposed as `BuildConfig.SUPABASE_URL` / `BuildConfig.SUPABASE_KEY`.

### 3. Build and run

```bash
./gradlew installDebug
```

Or open the project in Android Studio and run the `app` configuration on a device or emulator (API 26+).

## Project structure

```
app/src/main/java/com/app/lira/
├── data/
│   ├── model/           # Data classes shared across repositories
│   ├── repository/      # All Supabase access — one repository per domain area
│   └── remote/          # Supabase client setup
├── navigation/           # NavGraph, routes, session-check splash
├── ui/
│   ├── screens/          # One file per screen
│   ├── components/       # Shared composables (buttons, dialogs, chips, charts)
│   └── theme/            # Colors, typography
└── widget/               # Home screen widget (Jetpack Glance)
```

## Security notes

- The app only ever holds the Supabase **anon key** — all authorization is enforced server-side via Row Level Security.
- Account deletion removes the user's data rows but not their Supabase Auth credential — deleting that requires dashboard/service-role access the client intentionally never has.
