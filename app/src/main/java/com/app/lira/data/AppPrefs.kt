package com.app.lira.data

import android.content.Context
import android.content.SharedPreferences

/** Small on-device flags for the redesigned flows (onboarding, weekly recap, pinned currencies). */
object AppPrefs {
    private const val PREFS = "lira_app_prefs"
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    /** True once the post-signup onboarding (currency → limit → first log) has finished or been skipped. */
    var onboardingDone: Boolean
        get() = prefs?.getBoolean("onboarding_done", false) ?: false
        set(v) { prefs?.edit()?.putBoolean("onboarding_done", v)?.apply() }

    /** Show the one-time "log your first expense" coach card on Home. */
    var showFirstLogCoach: Boolean
        get() = prefs?.getBoolean("first_log_coach", false) ?: false
        set(v) { prefs?.edit()?.putBoolean("first_log_coach", v)?.apply() }

    /** ISO week key ("2026-W46") of the last weekly recap shown on Home — never shows twice. */
    var lastRecapWeek: String?
        get() = prefs?.getString("last_recap_week", null)
        set(v) { prefs?.edit()?.putString("last_recap_week", v)?.apply() }

    /** Currencies pinned at the top of the currency picker. */
    var pinnedCurrencies: List<String>
        get() = prefs?.getString("pinned_currencies", null)?.split(',')?.filter { it.isNotBlank() } ?: emptyList()
        set(v) { prefs?.edit()?.putString("pinned_currencies", v.joinToString(","))?.apply() }

    /** No account: data lives only on this phone (data/local/LocalDb), nothing is backed up. */
    var offlineMode: Boolean
        get() = prefs?.getBoolean("offline_mode", false) ?: false
        set(v) { prefs?.edit()?.putBoolean("offline_mode", v)?.commit() }

    /** Set when leaving offline mode for an account: the offline entries get copied into it after sign-in. */
    var migrateOffline: Boolean
        get() = prefs?.getBoolean("migrate_offline", false) ?: false
        set(v) { prefs?.edit()?.putBoolean("migrate_offline", v)?.commit() }

    /** Keys of subscription suggestions the user marked "Not a subscription" (see SubscriptionDetector). */
    var dismissedSubscriptions: Set<String>
        get() = prefs?.getStringSet("dismissed_subscriptions", null)?.toSet() ?: emptySet()
        set(v) { prefs?.edit()?.putStringSet("dismissed_subscriptions", HashSet(v))?.apply() }
}
