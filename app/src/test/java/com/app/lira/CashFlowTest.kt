package com.app.lira

import com.app.lira.data.BalanceAnchor
import com.app.lira.data.CashFlow
import com.app.lira.data.HomeTxn
import com.app.lira.data.IncomeKinds
import com.app.lira.data.RecurrenceMath
import com.app.lira.data.model.Recurring
import com.app.lira.ui.screens.wallet.IncomeDraft
import com.app.lira.ui.screens.wallet.parseAmount
import com.app.lira.ui.screens.wallet.scheduleLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class CashFlowTest {
    private val zone = ZoneId.of("Europe/Paris")
    private val today = LocalDate.of(2026, 11, 21)
    private val eur: (HomeTxn, String) -> Double? = { t, home -> if (t.currency == null || t.currency == home) t.amount else null }

    private fun income(name: String, amount: Double, start: String, interval: String = "monthly", occurrences: Int? = null, id: Int = 1) =
        Recurring(id = id, user_id = "u", name = name, amount = amount, start_date = start, interval = interval, occurrences = occurrences, type = "received")

    private fun bill(name: String, amount: Double, start: String, interval: String = "monthly", id: Int = 100) =
        Recurring(id = id, user_id = "u", name = name, amount = amount, start_date = start, interval = interval, type = "payed", category_id = 1)

    private var nextId = 1
    private fun txn(amount: Double, type: String, at: String, currency: String? = null) =
        HomeTxn(id = nextId++, amount = amount, type = type, date = at, currency = currency)

    private fun anchorAt(amount: Double, y: Int, m: Int, d: Int, h: Int) =
        BalanceAnchor(amount, ZonedDateTime.of(y, m, d, h, 0, 0, 0, zone).toInstant().toEpochMilli())

    // ───── Dates ─────

    @Test fun monthlyOn31stStaysOnLastDay() {
        val start = LocalDate.of(2026, 1, 31)
        assertEquals(LocalDate.of(2026, 2, 28), RecurrenceMath.nth(start, "monthly", 1))
        assertEquals(LocalDate.of(2026, 3, 31), RecurrenceMath.nth(start, "monthly", 2))
        assertEquals(LocalDate.of(2026, 4, 30), RecurrenceMath.nth(start, "monthly", 3))
    }

    @Test fun nextDayOfMonthIsStrictlyAfterToday() {
        assertEquals(LocalDate.of(2026, 12, 5), CashFlow.nextDayOfMonth(5, today))
        assertEquals(LocalDate.of(2026, 12, 21), CashFlow.nextDayOfMonth(21, today))
        assertEquals(LocalDate.of(2026, 11, 22), CashFlow.nextDayOfMonth(22, today))
        assertEquals(LocalDate.of(2026, 11, 30), CashFlow.nextDayOfMonth(31, today))
        assertEquals(LocalDate.of(2027, 2, 28), CashFlow.nextDayOfMonth(30, LocalDate.of(2027, 1, 30)))
    }

    @Test fun weeklyDraftStartsOnNextWeekday() {
        // 21 Nov 2026 is a Saturday.
        assertEquals(LocalDate.of(2026, 11, 27), IncomeDraft("Job", 100.0, "weekly", 5).startDate(today))
        assertEquals(LocalDate.of(2026, 11, 28), IncomeDraft("Job", 100.0, "weekly", 6).startDate(today))
    }

    @Test fun draftRoundTripsThroughRecurring() {
        val d = IncomeDraft.from(income("CAF", 210.0, "2026-12-05"))
        assertEquals("monthly", d.interval); assertEquals(5, d.day)
        val w = IncomeDraft.from(income("Job", 90.0, "2026-11-27", interval = "weekly"))
        assertEquals("weekly", w.interval); assertEquals(5, w.day)
    }

    @Test fun labels() {
        assertEquals("Monthly · on the 1st", scheduleLabel("monthly", 1))
        assertEquals("Monthly · on the 22nd", scheduleLabel("monthly", 22))
        assertEquals("Monthly · on the 13th", scheduleLabel("monthly", 13))
        assertEquals("Monthly · last day", scheduleLabel("monthly", 31))
        assertEquals("Every Friday", scheduleLabel("weekly", 5))
    }

    // ───── Parsing & kinds ─────

    @Test fun parsesAmountsInAnyFormat() {
        assertEquals(1234.5, parseAmount("1 234,50")!!, 0.001)
        assertEquals(1234.5, parseAmount("1,234.50")!!, 0.001)
        assertEquals(1234.5, parseAmount("1.234,50")!!, 0.001)
        assertEquals(12.5, parseAmount("12,5")!!, 0.001)
        assertEquals(1600.0, parseAmount("1600")!!, 0.001)
        assertEquals(1600.0, parseAmount("1 600")!!, 0.001)
        assertNull(parseAmount(""))
        assertNull(parseAmount("abc"))
    }

    @Test fun recognisesIncomeKinds() {
        assertEquals(IncomeKinds.Salary, IncomeKinds.of("Salaire"))
        assertEquals(IncomeKinds.Salary, IncomeKinds.of("Salary"))
        assertEquals(IncomeKinds.Caf, IncomeKinds.of("CAF"))
        assertEquals(IncomeKinds.Caf, IncomeKinds.of("Prime d'activité"))
        assertEquals(IncomeKinds.Apl, IncomeKinds.of("APL"))
        assertEquals(IncomeKinds.Grant, IncomeKinds.of("Bourse CROUS"))
        assertEquals(IncomeKinds.Pension, IncomeKinds.of("Retraite"))
        assertEquals(IncomeKinds.Unemployment, IncomeKinds.of("France Travail"))
        assertEquals(IncomeKinds.Other, IncomeKinds.of("Loyer coloc"))
        // Whole words only: "are" (chômage) must not match inside "careful".
        assertEquals(IncomeKinds.Other, IncomeKinds.of("careful"))
        assertEquals(IncomeKinds.Other, IncomeKinds.of(""))
    }

    // ───── Balance ─────

    @Test fun balanceMovesOnlyWithRowsAfterTheAnchor() {
        val anchor = anchorAt(1000.0, 2026, 11, 20, 12)
        val txns = listOf(
            txn(50.0, "payed", "2026-11-20T10:00:00+01:00"),   // before: already in the bank balance
            txn(30.0, "payed", "2026-11-20T15:00:00+01:00"),
            txn(200.0, "received", "2026-11-21T00:00:00+01:00")
        )
        val s = CashFlow.snapshot(anchor, emptyList(), txns, "EUR", today, zone, eur)
        assertEquals(1170.0, s.balance!!, 0.001)
    }

    @Test fun paydayPostedBeforeAnchorIsNotCountedTwice() {
        val anchor = anchorAt(500.0, 2026, 11, 21, 9)
        val txns = listOf(txn(1600.0, "received", "2026-11-21T00:00:00+01:00"))
        val s = CashFlow.snapshot(anchor, emptyList(), txns, "EUR", today, zone, eur)
        assertEquals(500.0, s.balance!!, 0.001)
    }

    @Test fun unknownForeignRateCountsAsZero() {
        val anchor = anchorAt(100.0, 2026, 11, 20, 12)
        val txns = listOf(txn(1000.0, "payed", "2026-11-20T15:00:00+01:00", currency = "JPY"))
        val s = CashFlow.snapshot(anchor, emptyList(), txns, "EUR", today, zone, eur)
        assertEquals(100.0, s.balance!!, 0.001)
    }

    @Test fun noAnchorMeansNoBalanceButIncomesStillWork() {
        val s = CashFlow.snapshot(null, listOf(income("Salary", 1600.0, "2026-12-01")), emptyList(), "EUR", today, zone, eur)
        assertNull(s.balance); assertNull(s.perDayUntilPayday); assertNull(s.endOfMonth)
        assertEquals(LocalDate.of(2026, 12, 1), s.nextPayday)
        assertEquals(1600.0, s.monthlyIncome, 0.001)
    }

    // ───── Payday, per day, end of month ─────

    @Test fun paydayPerDayAndEndOfMonth() {
        val anchor = anchorAt(1170.0, 2026, 11, 21, 8)
        val rec = listOf(
            income("Salary", 1600.0, "2026-12-01", id = 1),
            income("APL", 250.0, "2026-12-05", id = 2),
            bill("Phone", 100.0, "2026-11-25"),
            bill("Gym", 30.0, "2026-12-01", id = 101)        // on payday itself: not "before" payday
        )
        val s = CashFlow.snapshot(anchor, rec, emptyList(), "EUR", today, zone, eur)
        assertEquals(LocalDate.of(2026, 12, 1), s.nextPayday)
        assertEquals(listOf("Salary"), s.nextPaydayItems.map { it.name })
        assertEquals(10, s.daysToPayday)
        assertEquals(100.0, s.billsBeforePayday, 0.001)
        assertEquals(107.0, s.perDayUntilPayday!!, 0.001)
        assertEquals(1070.0, s.endOfMonth!!, 0.001)              // only the phone bill lands in November
        assertEquals(1850.0, s.monthlyIncome, 0.001)
        assertEquals(0.0, s.expectedThisMonth, 0.001)            // both start in December
    }

    @Test fun perDayNeverGoesNegative() {
        val anchor = anchorAt(50.0, 2026, 11, 21, 8)
        val rec = listOf(income("Salary", 1600.0, "2026-12-01"), bill("Rent", 600.0, "2026-11-28"))
        val s = CashFlow.snapshot(anchor, rec, emptyList(), "EUR", today, zone, eur)
        assertEquals(0.0, s.perDayUntilPayday!!, 0.001)
        assertTrue(s.endOfMonth!! < 0)
    }

    @Test fun monthTotalsAndExpectedIncome() {
        val rec = listOf(income("Salary", 1600.0, "2026-10-01"), income("CAF", 200.0, "2026-10-28", id = 2))
        val txns = listOf(
            txn(1600.0, "received", "2026-11-01T00:00:00+01:00"),
            txn(40.0, "payed", "2026-11-03T12:00:00+01:00"),
            txn(25.0, "payed", "2026-10-30T12:00:00+01:00")      // last month: not in totals
        )
        val s = CashFlow.snapshot(null, rec, txns, "EUR", today, zone, eur)
        assertEquals(1800.0, s.expectedThisMonth, 0.001)
        assertEquals(1600.0, s.receivedThisMonth, 0.001)
        assertEquals(40.0, s.spentThisMonth, 0.001)
        assertEquals(LocalDate.of(2026, 11, 28), s.nextPayday)   // CAF comes before December's salary
        assertEquals(LocalDate.of(2026, 10, 28), s.cycleStart)
    }

    @Test fun endedIncomesAreIgnored() {
        val rec = listOf(income("Bourse", 300.0, "2026-01-05", occurrences = 6), income("Salary", 1600.0, "2026-12-01", id = 2))
        val s = CashFlow.snapshot(null, rec, emptyList(), "EUR", today, zone, eur)
        assertEquals(listOf("Salary"), s.incomes.map { it.name })
    }

    @Test fun lastDaySalaryLandsOnMonthEnd() {
        val rec = listOf(income("Salary", 2000.0, "2026-01-31"))
        val s = CashFlow.snapshot(null, rec, emptyList(), "EUR", LocalDate.of(2026, 4, 15), zone, eur)
        assertEquals(LocalDate.of(2026, 4, 30), s.nextPayday)
    }

    @Test fun weeklyIncomeCountsPerMonth() {
        val r = income("Job", 120.0, "2026-11-27", interval = "weekly")
        assertEquals(520.0, CashFlow.perMonth(r), 0.001)
        val s = CashFlow.snapshot(null, listOf(r), emptyList(), "EUR", today, zone, eur)
        assertEquals(LocalDate.of(2026, 11, 27), s.nextPayday)
    }
}
