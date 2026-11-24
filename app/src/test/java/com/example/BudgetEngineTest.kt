package com.example

import com.example.data.local.BudgetEntity
import com.example.data.local.BudgetItemEntity
import com.example.data.local.ClassificationEntity
import com.example.data.local.DetailItemEntity
import com.example.data.local.FinanceTransactionEntity
import com.example.data.local.ScheduledRuleEntity
import com.example.data.local.SyncEntityTypes
import com.example.data.local.SyncOperations
import com.example.data.local.SyncQueueItemEntity
import com.example.data.local.TransactionTypes
import com.example.data.sync.ApiJsonBuilder
import com.example.domain.BudgetAlertStatus
import com.example.domain.BudgetEngine
import com.example.domain.BudgetPaceStatus
import com.example.domain.BudgetPeriodStatus
import com.example.domain.BudgetSource
import com.example.domain.FiscalPeriod
import com.example.domain.FiscalPeriodCalculator
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetEngineTest {

  private val clsVivienda =
    ClassificationEntity(
      id = "cls-vivienda",
      typeId = TransactionTypes.GASTO_UUID,
      name = "Vivienda",
      sortOrder = 1,
      monthlyBudgetCents = 800_000L,
    )

  private val clsBebe =
    ClassificationEntity(
      id = "cls-bebe",
      typeId = TransactionTypes.GASTO_UUID,
      name = "Bebé",
      sortOrder = 2,
      monthlyBudgetCents = 500_000L,
    )

  private val clsOtroIngreso =
    ClassificationEntity(
      id = "cls-otro-ing",
      typeId = TransactionTypes.INGRESO_UUID,
      name = "Otro",
      sortOrder = 1,
    )

  private val detLeche =
    DetailItemEntity(id = "det-leche", classificationId = "cls-bebe", name = "Leche", sortOrder = 1)
  private val detPanal =
    DetailItemEntity(id = "det-panal", classificationId = "cls-bebe", name = "Pañal", sortOrder = 2)
  private val detRetiro =
    DetailItemEntity(id = "det-retiro", classificationId = "cls-otro-ing", name = "Retiro", sortOrder = 1)

  private val classifications = listOf(clsVivienda, clsBebe, clsOtroIngreso)
  private val details = listOf(detLeche, detPanal, detRetiro)

  @Test
  fun evaluatesCatalogDefaultWhenNoExplicitBudgetExists() {
    val period = FiscalPeriod(2026, 12)
    val today = FiscalPeriodCalculator.ymdToEpochDay(2026, 11, 24) // 10 days elapsed in 2026-12

    val summary =
      BudgetEngine.evaluatePeriod(
        period = period,
        budgets = emptyList(),
        budgetItems = emptyList(),
        transactions = emptyList(),
        classifications = classifications,
        details = details,
        scheduledRules = emptyList(),
        todayEpochDay = today,
      )

    assertEquals(BudgetSource.CATALOG_DEFAULT, summary.source)
    assertEquals(BudgetPeriodStatus.CURRENT, summary.periodStatus)
    assertEquals(30, summary.totalDays)
    assertEquals(10, summary.daysElapsed)
    assertEquals(21, summary.daysRemaining)
    assertEquals(1_300_000L, summary.totalBudgetedCents)
    assertEquals(2, summary.categories.size)
  }

  @Test
  fun inheritsFromPreviousSavedBudgetWhenTargetPeriodHasNone() {
    val novBudget =
      BudgetEntity(
        id = BudgetEngine.budgetIdForPeriod("2026-11"),
        fiscalPeriodKey = "2026-11",
        expectedIncomeCents = 2_200_000L,
        savingsGoalCents = 400_000L,
      )
    val novItem =
      BudgetItemEntity(
        id = BudgetEngine.budgetItemId(novBudget.id, clsBebe.id, null),
        budgetId = novBudget.id,
        fiscalPeriodKey = "2026-11",
        classificationId = clsBebe.id,
        amountCents = 500_000L,
        rolloverEnabled = true,
        rolloverCapCents = 100_000L,
      )

    // In Nov, spent $3,500 out of $5,000 -> $1,500 remaining, capped at $1,000 rollover
    val novTx =
      FinanceTransactionEntity(
        id = "tx-nov-1",
        createdAtMillis = 1L,
        operationEpochDay = FiscalPeriodCalculator.ymdToEpochDay(2026, 11, 1),
        fiscalPeriodKey = "2026-11",
        fiscalPeriodLabel = "nov 2026",
        typeId = TransactionTypes.GASTO_UUID,
        classificationId = clsBebe.id,
        detailId = detLeche.id,
        accountId = "acc-1",
        amountCents = 350_000L,
      )

    val decSummary =
      BudgetEngine.evaluatePeriod(
        period = FiscalPeriod(2026, 12),
        budgets = listOf(novBudget),
        budgetItems = listOf(novItem),
        transactions = listOf(novTx),
        classifications = classifications,
        details = details,
        scheduledRules = emptyList(),
        todayEpochDay = FiscalPeriodCalculator.ymdToEpochDay(2026, 11, 24),
      )

    assertEquals(BudgetSource.INHERITED_FROM_PREVIOUS, decSummary.source)
    assertEquals("2026-11", decSummary.inheritedFromPeriodKey)
    val bebeEval = decSummary.categories.first { it.classificationId == clsBebe.id }
    assertEquals(500_000L, bebeEval.budgetedCents)
    assertEquals(100_000L, bebeEval.rolloverCents) // capped from 150_000 to 100_000
    assertEquals(600_000L, bebeEval.effectiveBudgetCents)
  }

  @Test
  fun negativeOverspendNeverRollsOver() {
    val novBudget =
      BudgetEntity(id = BudgetEngine.budgetIdForPeriod("2026-11"), fiscalPeriodKey = "2026-11")
    val novItem =
      BudgetItemEntity(
        id = BudgetEngine.budgetItemId(novBudget.id, clsBebe.id, null),
        budgetId = novBudget.id,
        fiscalPeriodKey = "2026-11",
        classificationId = clsBebe.id,
        amountCents = 500_000L,
        rolloverEnabled = true,
      )
    val overspendTx =
      FinanceTransactionEntity(
        id = "tx-nov-over",
        createdAtMillis = 1L,
        operationEpochDay = FiscalPeriodCalculator.ymdToEpochDay(2026, 11, 2),
        fiscalPeriodKey = "2026-11",
        fiscalPeriodLabel = "nov 2026",
        typeId = TransactionTypes.GASTO_UUID,
        classificationId = clsBebe.id,
        detailId = detLeche.id,
        accountId = "acc-1",
        amountCents = 650_000L,
      )

    val decSummary =
      BudgetEngine.evaluatePeriod(
        period = FiscalPeriod(2026, 12),
        budgets = listOf(novBudget),
        budgetItems = listOf(novItem),
        transactions = listOf(overspendTx),
        classifications = classifications,
        details = details,
        scheduledRules = emptyList(),
        todayEpochDay = FiscalPeriodCalculator.ymdToEpochDay(2026, 11, 24),
      )

    val bebeEval = decSummary.categories.first { it.classificationId == clsBebe.id }
    assertEquals(0L, bebeEval.rolloverCents)
    assertEquals(500_000L, bebeEval.effectiveBudgetCents)
  }

  @Test
  fun validatesSubLimitsAndCountsWithoutDoubleCounting() {
    val ok = BudgetEngine.validateSubLimits(500_000L, listOf(200_000L, 150_000L))
    assertTrue(ok.isValid)
    assertEquals(350_000L, ok.sumSubLimitsCents)
    assertEquals(0L, ok.excessCents)

    val bad = BudgetEngine.validateSubLimits(500_000L, listOf(300_000L, 250_000L))
    assertFalse(bad.isValid)
    assertEquals(50_000L, bad.excessCents)

    val budget = BudgetEntity(id = BudgetEngine.budgetIdForPeriod("2026-12"), fiscalPeriodKey = "2026-12")
    val root =
      BudgetItemEntity(
        id = BudgetEngine.budgetItemId(budget.id, clsBebe.id, null),
        budgetId = budget.id,
        fiscalPeriodKey = "2026-12",
        classificationId = clsBebe.id,
        amountCents = 500_000L,
      )
    val subLeche =
      BudgetItemEntity(
        id = BudgetEngine.budgetItemId(budget.id, clsBebe.id, detLeche.id),
        budgetId = budget.id,
        fiscalPeriodKey = "2026-12",
        classificationId = clsBebe.id,
        detailId = detLeche.id,
        amountCents = 200_000L,
      )
    val tx =
      FinanceTransactionEntity(
        id = "tx-dec-1",
        createdAtMillis = 1L,
        operationEpochDay = FiscalPeriodCalculator.ymdToEpochDay(2026, 11, 20),
        fiscalPeriodKey = "2026-12",
        fiscalPeriodLabel = "dic 2026",
        typeId = TransactionTypes.GASTO_UUID,
        classificationId = clsBebe.id,
        detailId = detLeche.id,
        accountId = "acc-1",
        amountCents = 170_000L,
      )
    val withdrawalTx =
      FinanceTransactionEntity(
        id = "tx-dec-retiro",
        createdAtMillis = 2L,
        operationEpochDay = FiscalPeriodCalculator.ymdToEpochDay(2026, 11, 21),
        fiscalPeriodKey = "2026-12",
        fiscalPeriodLabel = "dic 2026",
        typeId = TransactionTypes.INGRESO_UUID,
        classificationId = clsOtroIngreso.id,
        detailId = detRetiro.id,
        accountId = "acc-debit",
        amountCents = 100_000L,
      )
    val pendingRule =
      ScheduledRuleEntity(
        id = "rule-1",
        title = "Internet",
        typeId = TransactionTypes.GASTO_UUID,
        classificationId = clsVivienda.id,
        detailId = detLeche.id,
        accountId = "acc-1",
        amountCents = 60_900L,
        dayOfMonth = 25,
      )

    val summary =
      BudgetEngine.evaluatePeriod(
        period = FiscalPeriod(2026, 12),
        budgets = listOf(budget),
        budgetItems = listOf(root, subLeche),
        transactions = listOf(tx, withdrawalTx),
        classifications = classifications,
        details = details,
        scheduledRules = listOf(pendingRule),
        todayEpochDay = FiscalPeriodCalculator.ymdToEpochDay(2026, 11, 24),
      )

    assertEquals(0L, summary.actualIncomeCents) // Withdrawal excluded
    assertEquals(170_000L, summary.totalSpentCents) // Not double-counted
    assertEquals(330_000L, summary.totalRemainingCents) // Pending scheduled not deducted
    assertEquals(60_900L, summary.pendingScheduledExpenseCents)

    val bebe = summary.categories.first()
    assertEquals(170_000L, bebe.spentCents)
    assertEquals(BudgetAlertStatus.OK, bebe.alertStatus)
    assertEquals(BudgetPaceStatus.AT_RISK, bebe.paceStatus) // 170k in 10d -> 510k projected > 500k
    val lecheEval = bebe.subLimits.first()
    assertEquals(170_000L, lecheEval.spentCents)
    assertEquals(85.0, lecheEval.consumedPercent, 0.01)
    assertEquals(BudgetAlertStatus.WARNING, lecheEval.alertStatus)

    // Live capture impact preview
    val impact =
      BudgetEngine.computeImpactPreview(
        typeId = TransactionTypes.GASTO_UUID,
        classificationId = clsBebe.id,
        detailId = detLeche.id,
        amountCents = 50_000L,
        summary = summary,
      )
    assertNotNull(impact)
    assertEquals(220_000L, impact!!.projectedSpentCents)
    assertEquals(BudgetAlertStatus.OK, impact.alertStatus)
    assertNotNull(impact.subLimit)
    assertEquals(BudgetAlertStatus.EXCEEDED, impact.subLimit!!.alertStatus)
    assertEquals(-20_000L, impact.subLimit!!.remainingCents)

    val incomeImpact =
      BudgetEngine.computeImpactPreview(
        typeId = TransactionTypes.INGRESO_UUID,
        classificationId = clsBebe.id,
        detailId = detLeche.id,
        amountCents = 50_000L,
        summary = summary,
      )
    assertNull(incomeImpact)
  }

  @Test
  fun computesSuggestionsRoundedUpTo50Mxn() {
    val txs =
      listOf(
        FinanceTransactionEntity(
          id = "t1",
          createdAtMillis = 1L,
          operationEpochDay = FiscalPeriodCalculator.ymdToEpochDay(2026, 9, 5),
          fiscalPeriodKey = "2026-09",
          fiscalPeriodLabel = "sep 2026",
          typeId = TransactionTypes.GASTO_UUID,
          classificationId = clsVivienda.id,
          detailId = detLeche.id,
          accountId = "acc-1",
          amountCents = 420_000L,
        ),
        FinanceTransactionEntity(
          id = "t2",
          createdAtMillis = 2L,
          operationEpochDay = FiscalPeriodCalculator.ymdToEpochDay(2026, 10, 5),
          fiscalPeriodKey = "2026-10",
          fiscalPeriodLabel = "oct 2026",
          typeId = TransactionTypes.GASTO_UUID,
          classificationId = clsVivienda.id,
          detailId = detLeche.id,
          accountId = "acc-1",
          amountCents = 496_000L,
        ),
        FinanceTransactionEntity(
          id = "t3",
          createdAtMillis = 3L,
          operationEpochDay = FiscalPeriodCalculator.ymdToEpochDay(2026, 11, 5),
          fiscalPeriodKey = "2026-11",
          fiscalPeriodLabel = "nov 2026",
          typeId = TransactionTypes.GASTO_UUID,
          classificationId = clsVivienda.id,
          detailId = detLeche.id,
          accountId = "acc-1",
          amountCents = 530_000L,
        ),
      )

    val res =
      BudgetEngine.computeSuggestions(
        referencePeriod = FiscalPeriod(2026, 12),
        transactions = txs,
        classifications = classifications,
        details = details,
        lookbackPeriods = 3,
      )

    assertEquals(listOf("2026-09", "2026-10", "2026-11"), res.analyzedPeriods)
    assertEquals(1, res.categories.size)
    // Avg = (420_000 + 496_000 + 530_000) / 3 = 482_000 -> rounded up to multiple of 5_000 = 485_000
    assertEquals(482_000L, res.categories.first().avgCents)
    assertEquals(485_000L, res.categories.first().suggestedCents)
    assertEquals(485_000L, res.totalSuggestedCents)
  }

  @Test
  fun serializesBudgetsAndBudgetItemsInWifiSyncBatchPayload() {
    val budget = BudgetEntity(id = "b-1", fiscalPeriodKey = "2026-12", expectedIncomeCents = 2_000_000L)
    val item =
      BudgetItemEntity(
        id = "bi-1",
        budgetId = "b-1",
        fiscalPeriodKey = "2026-12",
        classificationId = clsVivienda.id,
        amountCents = 800_000L,
      )
    val queue =
      listOf(
        SyncQueueItemEntity(
          id = 1L,
          entityType = SyncEntityTypes.BUDGET,
          entityId = budget.id,
          operation = SyncOperations.UPSERT,
          payloadJson = ApiJsonBuilder.budgetToJson(budget).toString(),
        ),
        SyncQueueItemEntity(
          id = 2L,
          entityType = SyncEntityTypes.BUDGET_ITEM,
          entityId = item.id,
          operation = SyncOperations.UPSERT,
          payloadJson = ApiJsonBuilder.budgetItemToJson(item).toString(),
        ),
        SyncQueueItemEntity(
          id = 3L,
          entityType = SyncEntityTypes.BUDGET_ITEM,
          entityId = "bi-deleted",
          operation = SyncOperations.DELETE,
          payloadJson = "{}",
        ),
      )

    val batch =
      ApiJsonBuilder.buildWifiSyncBatchJson(
        deviceLabel = "Test",
        pendingQueueItems = queue,
        unsyncedTransactions = emptyList(),
        classificationsById = emptyMap(),
        detailsById = emptyMap(),
        accountsById = emptyMap(),
      )

    assertEquals(3, batch.totalItems)
    val root = JSONObject(batch.jsonString)
    val adminChanges = root.getJSONObject("adminChanges")
    assertEquals(1, adminChanges.getJSONArray("budgets").length())
    assertEquals(1, adminChanges.getJSONArray("budgetItems").length())
    val deletedIds = adminChanges.getJSONObject("deletedIds")
    assertEquals(1, deletedIds.getJSONArray("budgetItems").length())
    assertEquals("bi-deleted", deletedIds.getJSONArray("budgetItems").getString(0))
  }
}
