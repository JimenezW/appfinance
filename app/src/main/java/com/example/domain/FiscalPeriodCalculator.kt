package com.example.domain

import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

data class SimpleYmd(val year: Int, val month: Int, val day: Int)

data class FiscalPeriod(val year: Int, val month: Int) {
  val key: String
    get() = "%04d-%02d".format(Locale.US, year, month)

  val label: String
    get() = "${FiscalPeriodCalculator.monthAbbrev(month)} $year"

  fun previous(): FiscalPeriod {
    return if (month == 1) FiscalPeriod(year - 1, 12) else FiscalPeriod(year, month - 1)
  }

  fun next(): FiscalPeriod {
    return if (month == 12) FiscalPeriod(year + 1, 1) else FiscalPeriod(year, month + 1)
  }

  companion object {
    fun fromKey(key: String): FiscalPeriod? {
      val parts = key.split("-")
      if (parts.size != 2) return null
      val y = parts[0].toIntOrNull() ?: return null
      val m = parts[1].toIntOrNull() ?: return null
      if (m !in 1..12) return null
      return FiscalPeriod(y, m)
    }
  }
}

object FiscalPeriodCalculator {
  private val monthNamesEs =
    arrayOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

  private val mxLocale = Locale("es", "MX")

  fun monthAbbrev(month1To12: Int): String {
    return monthNamesEs.getOrElse(month1To12 - 1) { "mes" }
  }

  /**
   * Mandatory 15th-day cutoff rule:
   * - Day 1..14 -> same month & year (e.g. 14/10/2026 -> oct 2026)
   * - Day 15..end of month -> advances to next month (and next year if December, e.g. 15/12/2025 -> ene 2026)
   */
  fun computeFiscalPeriod(year: Int, month1To12: Int, dayOfMonth: Int): FiscalPeriod {
    return if (dayOfMonth <= 14) {
      FiscalPeriod(year, month1To12)
    } else {
      if (month1To12 == 12) FiscalPeriod(year + 1, 1) else FiscalPeriod(year, month1To12 + 1)
    }
  }

  fun computeFiscalPeriod(epochDay: Long): FiscalPeriod {
    val ymd = epochDayToYmd(epochDay)
    return computeFiscalPeriod(ymd.year, ymd.month, ymd.day)
  }

  fun isCutoffApplied(epochDay: Long): Boolean {
    return epochDayToYmd(epochDay).day >= 15
  }

  fun todayEpochDay(): Long {
    val cal = Calendar.getInstance()
    return ymdToEpochDay(
      cal.get(Calendar.YEAR),
      cal.get(Calendar.MONTH) + 1,
      cal.get(Calendar.DAY_OF_MONTH),
    )
  }

  fun ymdToEpochDay(year: Int, month1To12: Int, day: Int): Long {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    cal.clear()
    cal.set(Calendar.YEAR, year)
    cal.set(Calendar.MONTH, month1To12 - 1)
    cal.set(Calendar.DAY_OF_MONTH, day)
    return cal.timeInMillis / 86_400_000L
  }

  fun epochDayToYmd(epochDay: Long): SimpleYmd {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    cal.clear()
    cal.timeInMillis = epochDay * 86_400_000L
    return SimpleYmd(
      year = cal.get(Calendar.YEAR),
      month = cal.get(Calendar.MONTH) + 1,
      day = cal.get(Calendar.DAY_OF_MONTH),
    )
  }

  fun utcMillisToEpochDay(utcMillis: Long): Long {
    return utcMillis / 86_400_000L
  }

  fun epochDayToUtcMillis(epochDay: Long): Long {
    return epochDay * 86_400_000L
  }

  fun daysInMonth(year: Int, month1To12: Int): Int {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    cal.clear()
    cal.set(Calendar.YEAR, year)
    cal.set(Calendar.MONTH, month1To12 - 1)
    cal.set(Calendar.DAY_OF_MONTH, 1)
    return cal.getActualMaximum(Calendar.DAY_OF_MONTH)
  }

  fun formatOperationDateChip(epochDay: Long, today: Long = todayEpochDay()): String {
    val ymd = epochDayToYmd(epochDay)
    val monthCap = monthAbbrev(ymd.month).replaceFirstChar { it.uppercaseChar() }
    val base = "${ymd.day} $monthCap ${ymd.year}"
    return when (epochDay) {
      today -> "Hoy, $base"
      today - 1 -> "Ayer, $base"
      else -> base
    }
  }

  fun formatShortDate(epochDay: Long): String {
    val ymd = epochDayToYmd(epochDay)
    return "%02d/%02d/%04d".format(Locale.US, ymd.day, ymd.month, ymd.year)
  }

  fun formatMxn(cents: Long, includeSign: Boolean = false, isExpense: Boolean = false): String {
    val nf = NumberFormat.getCurrencyInstance(mxLocale)
    nf.minimumFractionDigits = 2
    nf.maximumFractionDigits = 2
    val absFormatted = nf.format(abs(cents) / 100.0)
    return when {
      includeSign && isExpense -> "-$absFormatted"
      includeSign && !isExpense -> "+$absFormatted"
      cents < 0 -> "-$absFormatted"
      else -> absFormatted
    }
  }

  fun parseAmountToCents(raw: String): Long {
    val cleaned = raw.trim().replace(",", "")
    if (cleaned.isEmpty() || cleaned == ".") return 0L
    val parts = cleaned.split(".")
    val whole = parts[0].toLongOrNull() ?: 0L
    val fracStr = if (parts.size > 1) parts[1].take(2).padEnd(2, '0') else "00"
    val frac = fracStr.toLongOrNull() ?: 0L
    return (whole * 100L) + frac
  }
}
