package com.app.lira.data

import com.app.lira.data.model.Category
import com.app.lira.data.repository.DailyLimitRepository

/**
 * In-memory snapshot of the home screen's data, populated during the app's session-check
 * splash (see NavGraph) so AddPayedScreen can render real data on its very first frame
 * instead of an empty/zero state while its own fetch is in flight.
 */
object HomeDataCache {
    var categories: List<Category> = emptyList()
    var todaySpent: Double = 0.0
    var todayReceived: Double = 0.0
    var dailyLimit: Double = DailyLimitRepository.DEFAULT_DAILY_LIMIT
}
