package com.example.domain

import com.example.data.local.BudgetEntity
import com.example.data.local.BudgetItemEntity
import com.example.data.local.ClassificationEntity
import com.example.data.local.DetailItemEntity
import com.example.data.local.FinanceTransactionEntity
import com.example.data.local.ScheduledRuleEntity
import com.example.data.local.TransactionTypes
import com.example.data.local.UuidV5
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

enum class BudgetSource {
  EXPLICIT,
  INHERITED_FROM_PREVIOUS,
  CATALOG_DEFAULT,
}

enum class BudgetPeriodStatus {
  PAST,
  CURRENT,
  FUTURE,
}

enum class BudgetAlertStatus {
  OK,
  WARNING,
  EXCEEDED,
}

enum class BudgetPaceStatus {
  ON_TRACK,
  AT_RISK,
  EXCEEDED,
}

data class BudgetSubLimitEvaluation(
  val itemId: String?,
  val detailId: String,
  val detailName: String,
  val budgetedCents: Long,
  val rolloverCents: Long,
  val effectiveBudgetCents: Long,
  val spentCents: Long,
  val remainingCents: Long,
  val consumedPercent: Double,
  val warningThresholdPercent: Int,
  val alertStatus: BudgetAlertStatus,
  val paceStatus: BudgetPaceStatus,
)

data class BudgetCategoryEvaluation(
  val itemId: String?,
  val classificationId: String,
  val classificationName: String,
  val budgetedCents: Long,
  val rolloverCents: Long,
  val effectiveBudgetCents: Long,
  val spentCents: Long,
  val remainingCents: Long,
  val consumedPercent: Double,
  val warningThresholdPercent: Int,
  val rolloverEnabled: Boolean,
  val rolloverCapCents: Long?,
  val alertStatus: BudgetAlertStatus,
  val paceStatus: BudgetPaceStatus,
  val dailyAllowanceCents: Long,
  val projectedSpentCents: Long,
  val subLimits: List<BudgetSubLimitEvaluation>,
)

data class UnbudgetedCategoryEvaluation(
  val classificationId: String,
  val classificationName: String,
  val spentCents: Long,
)

data class BudgetPeriodSummary(
  val fiscalPeriodKey: String,
  val fiscalPeriodLabel: String,
  val periodStartDate: String,
  val periodEndDate: String,
  val periodStatus: BudgetPeriodStatus,
  val totalDays: Int,
  val daysElapsed: Int,
  val daysRemaining: Int,
  val periodProgressPercent: Double,
  val source: BudgetSource,
  val inheritedFromPeriodKey: String?,
  val expectedIncomeCents: Long,
  val actualIncomeCents: Long,
  val savingsGoalCents: Long,
  val globalExpenseLimitCents: Long?,
  val notes: String?,
  val totalBudgetedCents: Long,
  val totalRolloverCents: Long,
  val totalEffectiveBudgetCents: Long,
  val totalSpentCents: Long,
  val totalRemainingCents: Long,
  val totalConsumedPercent: Double,
  val unassignedCents: Long,
  val isOverAllocated: Boolean,
  val pendingScheduledExpenseCents: Long,
  val dailyAllowanceCents: Long,
  val projectedSpentCents: Long,
  val overallAlertStatus: BudgetAlertStatus,
  val overallPaceStatus: BudgetPaceStatus,
  val categories: List<BudgetCategoryEvaluation>,
  val unbudgetedCategories: List<UnbudgetedCategoryEvaluation>,
)

data class BudgetSubLimitImpactPreview(
  val detailId: String,
  val detailName: String,
  val effectiveBudgetCents: Long,
  val projectedSpentCents: Long,
  val remainingCents: Long,
  val consumedPercent: Double,
  val alertStatus: BudgetAlertStatus,
)

data class BudgetImpactPreview(
  val fiscalPeriodKey: String,
  val classificationId: String,
  val classificationName: String,
  val effectiveBudgetCents: Long,
  val currentSpentCents: Long,
  val projectedSpentCents: Long,
  val remainingCents: Long,
  val consumedPercent: Double,
  val alertStatus: BudgetAlertStatus,
  val subLimit: BudgetSubLimitImpactPreview? = null,
)

data class BudgetCategorySuggestion(
  val classificationId: String,
  val classificationName: String,
  val avgCents: Long,
  val minCents: Long,
  val maxCents: Long,
  val periodsWithSpend: Int,
  val suggestedCents: Long,
)

data class BudgetSuggestionsResult(
  val referencePeriodKey: String,
  val lookbackPeriods: Int,
  val analyzedPeriods: List<String>,
  val totalSuggestedCents: Long,
  val categories: List<BudgetCategorySuggestion>,
)

data class SubLimitValidationResult(
  val isValid: Boolean,
  val sumSubLimitsCents: Long,
  val baseAmountCents: Long,
  val excessCents: Long,
)

/**
 * Pure-Kotlin offline-first budget evaluation engine matching backFinanceApp (/api/v1/budgets).
 * Uses integer cents exclusively (no floating-point accumulation) and enforces:
 * - 15th-day fiscal period cutoff
 * - Automatic resolution & inheritance (EXPLICIT -> INHERITED_FROM_PREVIOUS -> CATALOG_DEFAULT)
 * - Positive rollover with optional cap (never carries negative overspend)
 * - Sub-limits by Detail without double counting
 * - Exclusion of internal withdrawal transfers (Ingreso -> Otro -> Retiro)
 * - Pace / burn rate & pending scheduled expenses (informational only)
 */
object BudgetEngine {

  fun budgetIdForPeriod(fiscalPeriodKey: String): String =
    UuidV5.fromName("budgets:$fiscalPeriodKey")

  fun budgetItemId(
    budgetId: String,
    classificationId: String,
    detailId: String? = null,
  ): String = UuidV5.fromName("budget_items:$budgetId|$classificationId|${detailId ?: "ROOT"}")

  fun validateSubLimits(
    baseAmountCents: Long,
    subLimitAmounts: Collection<Long>,
  ): SubLimitValidationResult {
    val sum = subLimitAmounts.filter { it > 0L }.sum()
    val excess = (sum - baseAmountCents).coerceAtLeast(0L)
    return SubLimitValidationResult(
      isValid = sum <= baseAmountCents,
      sumSubLimitsCents = sum,
      baseAmountCents = baseAmountCents,
      excessCents = excess,
    )
  }

  fun roundUpTo50MxnCents(cents: Long): Long {
    if (cents <= 0L) return 0L
    val step = 5_000L // $50.00 MXN
    return ((cents + step - 1L) / step) * step
  }

  fun evaluatePeriod(
    period: FiscalPeriod,
    budgets: List<BudgetEntity>,
    budgetItems: List<BudgetItemEntity>,
    transactions: List<FinanceTransactionEntity>,
    classifications: List<ClassificationEntity>,
    details: List<DetailItemEntity>,
    scheduledRules: List<ScheduledRuleEntity>,
    todayEpochDay: Long = FiscalPeriodCalculator.todayEpochDay(),
    allowRolloverLookup: Boolean = true,
  ): BudgetPeriodSummary {
    val activeBudgets = budgets.filter { it.deletedAtMillis == null }
    val activeItems = budgetItems.filter { it.deletedAtMillis == null }
    val classificationsById = classifications.associateBy { it.id }
    val detailsById = details.associateBy { it.id }

    val (startEpochDay, endEpochDay) = FiscalPeriodCalculator.periodDateRange(period)
    val totalDays = (endEpochDay - startEpochDay + 1L).toInt().coerceAtLeast(1)
    val periodStatus =
      when {
        todayEpochDay > endEpochDay -> BudgetPeriodStatus.PAST
        todayEpochDay < startEpochDay -> BudgetPeriodStatus.FUTURE
        else -> BudgetPeriodStatus.CURRENT
      }
    val daysElapsed =
      when (periodStatus) {
        BudgetPeriodStatus.PAST -> totalDays
        BudgetPeriodStatus.FUTURE -> 0
        BudgetPeriodStatus.CURRENT -> (todayEpochDay - startEpochDay + 1L).toInt().coerceIn(1, totalDays)
      }
    val daysRemaining =
      when (periodStatus) {
        BudgetPeriodStatus.PAST -> 0
        BudgetPeriodStatus.FUTURE -> totalDays
        BudgetPeriodStatus.CURRENT -> (endEpochDay - todayEpochDay + 1L).toInt().coerceAtLeast(1)
      }
    val periodProgressPercent = round2((daysElapsed.toDouble() / totalDays.toDouble()) * 100.0)

    // 1. Resolve budget source (EXPLICIT -> INHERITED_FROM_PREVIOUS -> CATALOG_DEFAULT)
    val explicitBudget = activeBudgets.firstOrNull { it.fiscalPeriodKey == period.key }
    val inheritedBudget =
      if (explicitBudget == null) {
        activeBudgets
          .filter { it.fiscalPeriodKey < period.key }
          .maxByOrNull { it.fiscalPeriodKey }
      } else null

    val source: BudgetSource
    val inheritedFromKey: String?
    val resolvedBudget: BudgetEntity?
    val resolvedItems: List<BudgetItemEntity>

    when {
      explicitBudget != null -> {
        source = BudgetSource.EXPLICIT
        inheritedFromKey = null
        resolvedBudget = explicitBudget
        resolvedItems = activeItems.filter { it.budgetId == explicitBudget.id }
      }
      inheritedBudget != null -> {
        source = BudgetSource.INHERITED_FROM_PREVIOUS
        inheritedFromKey = inheritedBudget.fiscalPeriodKey
        resolvedBudget = inheritedBudget
        resolvedItems = activeItems.filter { it.budgetId == inheritedBudget.id }
      }
      else -> {
        source = BudgetSource.CATALOG_DEFAULT
        inheritedFromKey = null
        resolvedBudget = null
        resolvedItems =
          classifications
            .filter {
              it.deletedAtMillis == null &&
                it.isActive &&
                TransactionTypes.isExpense(it.typeId) &&
                (it.monthlyBudgetCents ?: 0L) > 0L
            }
            .map { cls ->
              BudgetItemEntity(
                id = budgetItemId("catalog_default", cls.id, null),
                budgetId = "catalog_default",
                fiscalPeriodKey = period.key,
                classificationId = cls.id,
                detailId = null,
                amountCents = cls.monthlyBudgetCents ?: 0L,
                warningThresholdPercent = 80,
                rolloverEnabled = false,
                rolloverCapCents = null,
                isDefault = true,
              )
            }
      }
    }

    // 2. Aggregate real transactions for this period (excluding internal withdrawals)
    val periodTxs =
      transactions.filter { tx ->
        tx.deletedAtMillis == null &&
          tx.fiscalPeriodKey == period.key &&
          !WithdrawalTransferRule.isWithdrawalTransaction(tx, classificationsById, detailsById)
      }

    var actualIncomeCents = 0L
    val spentByClassification = HashMap<String, Long>()
    val spentByDetail = HashMap<String, Long>()
    var totalExpenseSpentCents = 0L

    periodTxs.forEach { tx ->
      if (TransactionTypes.isIncome(tx.typeId)) {
        actualIncomeCents += tx.amountCents
      } else if (TransactionTypes.isExpense(tx.typeId)) {
        totalExpenseSpentCents += tx.amountCents
        spentByClassification[tx.classificationId] =
          (spentByClassification[tx.classificationId] ?: 0L) + tx.amountCents
        spentByDetail[tx.detailId] = (spentByDetail[tx.detailId] ?: 0L) + tx.amountCents
      }
    }

    // 3. Pending scheduled expenses & auto expected income from active ScheduledRules
    val activeRules = scheduledRules.filter { it.deletedAtMillis == null && it.isActive }
    val pendingScheduledExpenseCents =
      activeRules
        .filter {
          TransactionTypes.isExpense(it.typeId) && it.lastConfirmedPeriodKey != period.key
        }
        .sumOf { it.amountCents }

    val autoExpectedIncomeCents =
      activeRules
        .filter { TransactionTypes.isIncome(it.typeId) }
        .sumOf { it.amountCents }

    val expectedIncomeCents = resolvedBudget?.expectedIncomeCents ?: autoExpectedIncomeCents
    val savingsGoalCents = resolvedBudget?.savingsGoalCents ?: 0L

    // 4. Optional previous period evaluation for positive rollover (1-step lookback to avoid recursion)
    val hasAnyRollover = allowRolloverLookup && resolvedItems.any { it.rolloverEnabled }
    val prevSummary =
      if (hasAnyRollover) {
        evaluatePeriod(
          period = period.previous(),
          budgets = activeBudgets,
          budgetItems = activeItems,
          transactions = transactions,
          classifications = classifications,
          details = details,
          scheduledRules = scheduledRules,
          todayEpochDay = todayEpochDay,
          allowRolloverLookup = false,
        )
      } else null
    val prevCategoryByClassId = prevSummary?.categories?.associateBy { it.classificationId }.orEmpty()

    // 5. Evaluate categories and sub-limits
    val rootItems =
      resolvedItems.filter { item ->
        item.detailId == null &&
          classificationsById[item.classificationId]?.let { TransactionTypes.isExpense(it.typeId) } != false
      }
    val subItemsByClassId = resolvedItems.filter { it.detailId != null }.groupBy { it.classificationId }

    val categoryEvaluations =
      rootItems
        .map { root ->
          val clsName = classificationsById[root.classificationId]?.name ?: "Categoría"
          val prevCat = prevCategoryByClassId[root.classificationId]
          val rawRollover =
            if (root.rolloverEnabled && prevCat != null) {
              max(0L, prevCat.effectiveBudgetCents - prevCat.spentCents)
            } else 0L
          val rolloverCents =
            if (root.rolloverCapCents != null) min(rawRollover, root.rolloverCapCents.coerceAtLeast(0L))
            else rawRollover

          val effectiveBudgetCents = root.amountCents + rolloverCents
          val spentCents = spentByClassification[root.classificationId] ?: 0L
          val remainingCents = effectiveBudgetCents - spentCents
          val consumedPercent = computeConsumedPercent(spentCents, effectiveBudgetCents)
          val threshold = root.warningThresholdPercent.coerceIn(1, 99)
          val alertStatus = computeAlertStatus(spentCents, effectiveBudgetCents, consumedPercent, threshold)
          val projectedSpentCents = computeProjectedSpent(spentCents, periodStatus, daysElapsed, totalDays)
          val paceStatus = computePaceStatus(spentCents, effectiveBudgetCents, projectedSpentCents)
          val dailyAllowanceCents =
            if (daysRemaining > 0 && remainingCents > 0L) remainingCents / daysRemaining else 0L

          val subLimitEvals =
            subItemsByClassId[root.classificationId]
              .orEmpty()
              .map { sub ->
                val dId = sub.detailId!!
                val detName = detailsById[dId]?.name ?: "Detalle"
                val subEffective = sub.amountCents
                val subSpent = spentByDetail[dId] ?: 0L
                val subRemaining = subEffective - subSpent
                val subPercent = computeConsumedPercent(subSpent, subEffective)
                val subThreshold = sub.warningThresholdPercent.coerceIn(1, 99)
                val subAlert = computeAlertStatus(subSpent, subEffective, subPercent, subThreshold)
                val subProjected = computeProjectedSpent(subSpent, periodStatus, daysElapsed, totalDays)
                val subPace = computePaceStatus(subSpent, subEffective, subProjected)
                BudgetSubLimitEvaluation(
                  itemId = sub.id,
                  detailId = dId,
                  detailName = detName,
                  budgetedCents = sub.amountCents,
                  rolloverCents = 0L,
                  effectiveBudgetCents = subEffective,
                  spentCents = subSpent,
                  remainingCents = subRemaining,
                  consumedPercent = subPercent,
                  warningThresholdPercent = subThreshold,
                  alertStatus = subAlert,
                  paceStatus = subPace,
                )
              }
              .sortedByDescending { it.consumedPercent }

          BudgetCategoryEvaluation(
            itemId = root.id,
            classificationId = root.classificationId,
            classificationName = clsName,
            budgetedCents = root.amountCents,
            rolloverCents = rolloverCents,
            effectiveBudgetCents = effectiveBudgetCents,
            spentCents = spentCents,
            remainingCents = remainingCents,
            consumedPercent = consumedPercent,
            warningThresholdPercent = threshold,
            rolloverEnabled = root.rolloverEnabled,
            rolloverCapCents = root.rolloverCapCents,
            alertStatus = alertStatus,
            paceStatus = paceStatus,
            dailyAllowanceCents = dailyAllowanceCents,
            projectedSpentCents = projectedSpentCents,
            subLimits = subLimitEvals,
          )
        }
        .sortedWith(
          compareByDescending<BudgetCategoryEvaluation> { it.alertStatus == BudgetAlertStatus.EXCEEDED }
            .thenByDescending { it.alertStatus == BudgetAlertStatus.WARNING }
            .thenByDescending { it.consumedPercent }
            .thenBy { it.classificationName }
        )

    val budgetedClassIds = categoryEvaluations.map { it.classificationId }.toSet()
    val unbudgetedCategories =
      spentByClassification
        .filter { (clsId, spent) -> clsId !in budgetedClassIds && spent > 0L }
        .map { (clsId, spent) ->
          UnbudgetedCategoryEvaluation(
            classificationId = clsId,
            classificationName = classificationsById[clsId]?.name ?: "Sin categoría",
            spentCents = spent,
          )
        }
        .sortedByDescending { it.spentCents }

    val totalBudgetedCents = categoryEvaluations.sumOf { it.budgetedCents }
    val totalRolloverCents = categoryEvaluations.sumOf { it.rolloverCents }
    val totalEffectiveBudgetCents =
      resolvedBudget?.globalExpenseLimitCents?.takeIf { it > 0L }
        ?: categoryEvaluations.sumOf { it.effectiveBudgetCents }
    val totalSpentCents = totalExpenseSpentCents
    val totalRemainingCents = totalEffectiveBudgetCents - totalSpentCents
    val totalConsumedPercent = computeConsumedPercent(totalSpentCents, totalEffectiveBudgetCents)
    val unassignedCents = expectedIncomeCents - savingsGoalCents - totalBudgetedCents
    val isOverAllocated = expectedIncomeCents > 0L && unassignedCents < 0L

    val dailyAllowanceCents =
      if (daysRemaining > 0 && totalRemainingCents > 0L) totalRemainingCents / daysRemaining else 0L
    val projectedSpentCents =
      computeProjectedSpent(totalSpentCents, periodStatus, daysElapsed, totalDays)

    val overallAlertStatus =
      computeAlertStatus(totalSpentCents, totalEffectiveBudgetCents, totalConsumedPercent, 80)
    val overallPaceStatus =
      computePaceStatus(totalSpentCents, totalEffectiveBudgetCents, projectedSpentCents)

    return BudgetPeriodSummary(
      fiscalPeriodKey = period.key,
      fiscalPeriodLabel = period.label,
      periodStartDate = FiscalPeriodCalculator.formatShortDate(startEpochDay),
      periodEndDate = FiscalPeriodCalculator.formatShortDate(endEpochDay),
      periodStatus = periodStatus,
      totalDays = totalDays,
      daysElapsed = daysElapsed,
      daysRemaining = daysRemaining,
      periodProgressPercent = periodProgressPercent,
      source = source,
      inheritedFromPeriodKey = inheritedFromKey,
      expectedIncomeCents = expectedIncomeCents,
      actualIncomeCents = actualIncomeCents,
      savingsGoalCents = savingsGoalCents,
      globalExpenseLimitCents = resolvedBudget?.globalExpenseLimitCents,
      notes = resolvedBudget?.notes,
      totalBudgetedCents = totalBudgetedCents,
      totalRolloverCents = totalRolloverCents,
      totalEffectiveBudgetCents = totalEffectiveBudgetCents,
      totalSpentCents = totalSpentCents,
      totalRemainingCents = totalRemainingCents,
      totalConsumedPercent = totalConsumedPercent,
      unassignedCents = unassignedCents,
      isOverAllocated = isOverAllocated,
      pendingScheduledExpenseCents = pendingScheduledExpenseCents,
      dailyAllowanceCents = dailyAllowanceCents,
      projectedSpentCents = projectedSpentCents,
      overallAlertStatus = overallAlertStatus,
      overallPaceStatus = overallPaceStatus,
      categories = categoryEvaluations,
      unbudgetedCategories = unbudgetedCategories,
    )
  }

  /**
   * Computes live budget impact for CaptureScreen before the user saves a transaction.
   * Returns null if not an expense, amount <= 0, internal withdrawal, or category is unbudgeted.
   */
  fun computeImpactPreview(
    typeId: String,
    classificationId: String?,
    detailId: String?,
    amountCents: Long,
    summary: BudgetPeriodSummary,
  ): BudgetImpactPreview? {
    if (!TransactionTypes.isExpense(typeId) || amountCents <= 0L || classificationId.isNullOrBlank()) {
      return null
    }
    val category = summary.categories.firstOrNull { it.classificationId == classificationId } ?: return null
    val projectedSpent = category.spentCents + amountCents
    val remaining = category.effectiveBudgetCents - projectedSpent
    val percent = computeConsumedPercent(projectedSpent, category.effectiveBudgetCents)
    val alert =
      computeAlertStatus(
        spentCents = projectedSpent,
        effectiveBudgetCents = category.effectiveBudgetCents,
        consumedPercent = percent,
        warningThresholdPercent = category.warningThresholdPercent,
      )

    val subImpact =
      if (!detailId.isNullOrBlank()) {
        category.subLimits.firstOrNull { it.detailId == detailId }?.let { sub ->
          val subProjected = sub.spentCents + amountCents
          val subRemaining = sub.effectiveBudgetCents - subProjected
          val subPercent = computeConsumedPercent(subProjected, sub.effectiveBudgetCents)
          val subAlert =
            computeAlertStatus(
              spentCents = subProjected,
              effectiveBudgetCents = sub.effectiveBudgetCents,
              consumedPercent = subPercent,
              warningThresholdPercent = sub.warningThresholdPercent,
            )
          BudgetSubLimitImpactPreview(
            detailId = sub.detailId,
            detailName = sub.detailName,
            effectiveBudgetCents = sub.effectiveBudgetCents,
            projectedSpentCents = subProjected,
            remainingCents = subRemaining,
            consumedPercent = subPercent,
            alertStatus = subAlert,
          )
        }
      } else null

    return BudgetImpactPreview(
      fiscalPeriodKey = summary.fiscalPeriodKey,
      classificationId = category.classificationId,
      classificationName = category.classificationName,
      effectiveBudgetCents = category.effectiveBudgetCents,
      currentSpentCents = category.spentCents,
      projectedSpentCents = projectedSpent,
      remainingCents = remaining,
      consumedPercent = percent,
      alertStatus = alert,
      subLimit = subImpact,
    )
  }

  /**
   * Analyzes the last [lookbackPeriods] closed fiscal periods before [referencePeriod]
   * and suggests category budgets rounded up to $50 MXN (5000 cents).
   */
  fun computeSuggestions(
    referencePeriod: FiscalPeriod,
    transactions: List<FinanceTransactionEntity>,
    classifications: List<ClassificationEntity>,
    details: List<DetailItemEntity>,
    lookbackPeriods: Int = 3,
  ): BudgetSuggestionsResult {
    val count = lookbackPeriods.coerceIn(1, 12)
    val analyzedPeriods = mutableListOf<String>()
    var cursor = referencePeriod.previous()
    repeat(count) {
      analyzedPeriods.add(cursor.key)
      cursor = cursor.previous()
    }
    analyzedPeriods.reverse()
    val periodSet = analyzedPeriods.toSet()

    val classificationsById = classifications.associateBy { it.id }
    val detailsById = details.associateBy { it.id }

    // Map<classificationId, Map<periodKey, spentCents>>
    val spendByClassAndPeriod = HashMap<String, HashMap<String, Long>>()

    transactions
      .filter { tx ->
        tx.deletedAtMillis == null &&
          tx.fiscalPeriodKey in periodSet &&
          TransactionTypes.isExpense(tx.typeId) &&
          !WithdrawalTransferRule.isWithdrawalTransaction(tx, classificationsById, detailsById)
      }
      .forEach { tx ->
        val perPeriod = spendByClassAndPeriod.getOrPut(tx.classificationId) { HashMap() }
        perPeriod[tx.fiscalPeriodKey] = (perPeriod[tx.fiscalPeriodKey] ?: 0L) + tx.amountCents
      }

    val suggestions =
      spendByClassAndPeriod
        .mapNotNull { (clsId, perPeriod) ->
          val cls = classificationsById[clsId] ?: return@mapNotNull null
          if (!TransactionTypes.isExpense(cls.typeId)) return@mapNotNull null
          val nonZeroSpends = perPeriod.values.filter { it > 0L }
          if (nonZeroSpends.isEmpty()) return@mapNotNull null
          val totalAcrossPeriods = nonZeroSpends.sum()
          val avgCents = (totalAcrossPeriods.toDouble() / count.toDouble()).roundToLong()
          val suggestedCents = roundUpTo50MxnCents(avgCents)
          BudgetCategorySuggestion(
            classificationId = clsId,
            classificationName = cls.name,
            avgCents = avgCents,
            minCents = nonZeroSpends.min(),
            maxCents = nonZeroSpends.max(),
            periodsWithSpend = nonZeroSpends.size,
            suggestedCents = suggestedCents,
          )
        }
        .sortedByDescending { it.suggestedCents }

    return BudgetSuggestionsResult(
      referencePeriodKey = referencePeriod.key,
      lookbackPeriods = count,
      analyzedPeriods = analyzedPeriods,
      totalSuggestedCents = suggestions.sumOf { it.suggestedCents },
      categories = suggestions,
    )
  }

  private fun computeConsumedPercent(spentCents: Long, effectiveBudgetCents: Long): Double {
    if (effectiveBudgetCents <= 0L) return if (spentCents > 0L) 100.0 else 0.0
    return round2((spentCents.toDouble() / effectiveBudgetCents.toDouble()) * 100.0)
  }

  private fun computeAlertStatus(
    spentCents: Long,
    effectiveBudgetCents: Long,
    consumedPercent: Double,
    warningThresholdPercent: Int,
  ): BudgetAlertStatus {
    return when {
      effectiveBudgetCents <= 0L && spentCents > 0L -> BudgetAlertStatus.EXCEEDED
      spentCents > effectiveBudgetCents -> BudgetAlertStatus.EXCEEDED
      consumedPercent >= warningThresholdPercent.toDouble() -> BudgetAlertStatus.WARNING
      else -> BudgetAlertStatus.OK
    }
  }

  private fun computeProjectedSpent(
    spentCents: Long,
    status: BudgetPeriodStatus,
    daysElapsed: Int,
    totalDays: Int,
  ): Long {
    return when (status) {
      BudgetPeriodStatus.PAST -> spentCents
      BudgetPeriodStatus.FUTURE -> 0L
      BudgetPeriodStatus.CURRENT -> {
        if (daysElapsed <= 0 || spentCents <= 0L) 0L
        else ceil((spentCents.toDouble() / daysElapsed.toDouble()) * totalDays.toDouble()).toLong()
      }
    }
  }

  private fun computePaceStatus(
    spentCents: Long,
    effectiveBudgetCents: Long,
    projectedSpentCents: Long,
  ): BudgetPaceStatus {
    return when {
      effectiveBudgetCents <= 0L -> if (spentCents > 0L) BudgetPaceStatus.EXCEEDED else BudgetPaceStatus.ON_TRACK
      spentCents > effectiveBudgetCents -> BudgetPaceStatus.EXCEEDED
      projectedSpentCents > effectiveBudgetCents -> BudgetPaceStatus.AT_RISK
      else -> BudgetPaceStatus.ON_TRACK
    }
  }

  private fun round2(value: Double): Double = kotlin.math.round(value * 100.0) / 100.0
}
