package com.example

import com.example.domain.FiscalPeriodCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

class FiscalPeriodCalculatorTest {
  @Test
  fun fiscalPeriod_day14StaysInSameMonth() {
    val period = FiscalPeriodCalculator.computeFiscalPeriod(2026, 10, 14)
    assertEquals("2026-10", period.key)
    assertEquals("oct 2026", period.label)
  }

  @Test
  fun fiscalPeriod_day15AdvancesToNextMonth() {
    val oct15 = FiscalPeriodCalculator.computeFiscalPeriod(2026, 10, 15)
    assertEquals("2026-11", oct15.key)
    assertEquals("nov 2026", oct15.label)

    val aug15 = FiscalPeriodCalculator.computeFiscalPeriod(2026, 8, 15)
    assertEquals("2026-09", aug15.key)
    assertEquals("sep 2026", aug15.label)
  }

  @Test
  fun fiscalPeriod_december15RollsOverToJanuaryNextYear() {
    val dec15 = FiscalPeriodCalculator.computeFiscalPeriod(2025, 12, 15)
    assertEquals("2026-01", dec15.key)
    assertEquals("ene 2026", dec15.label)
  }

  @Test
  fun parseAmountToCents_handlesIntegersAndDecimals() {
    assertEquals(24500L, FiscalPeriodCalculator.parseAmountToCents("245"))
    assertEquals(24500L, FiscalPeriodCalculator.parseAmountToCents("245.00"))
    assertEquals(68940L, FiscalPeriodCalculator.parseAmountToCents("689.4"))
    assertEquals(0L, FiscalPeriodCalculator.parseAmountToCents(""))
    assertEquals(0L, FiscalPeriodCalculator.parseAmountToCents("."))
  }
}
