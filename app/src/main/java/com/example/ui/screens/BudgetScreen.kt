package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ClassificationEntity
import com.example.data.local.DetailItemEntity
import com.example.data.local.TransactionTypes
import com.example.domain.BudgetAlertStatus
import com.example.domain.BudgetCategoryEvaluation
import com.example.domain.BudgetEngine
import com.example.domain.BudgetPaceStatus
import com.example.domain.BudgetPeriodSummary
import com.example.domain.BudgetSource
import com.example.domain.BudgetSubLimitEvaluation
import com.example.domain.FiscalPeriodCalculator
import com.example.ui.FinanceUiState
import com.example.ui.theme.LocalFinanceColors
import com.example.ui.theme.MonoFontFamily
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class BudgetFilter(val label: String) {
  ALL("Todos"),
  AT_RISK("En Riesgo"),
  EXCEEDED("Sobregiro"),
  WITH_SUBLIMITS("Con Sub-límites"),
}

@Composable
fun BudgetScreen(
  state: FinanceUiState,
  onPrevPeriod: () -> Unit,
  onNextPeriod: () -> Unit,
  onClonePreviousPeriod: () -> Unit,
  onApplySuggestions: () -> Unit,
  onSaveCategoryConfig: (
    classificationId: String,
    baseAmountCents: Long,
    warningThresholdPercent: Int,
    rolloverEnabled: Boolean,
    rolloverCapCents: Long?,
    subLimitsByDetailId: Map<String, Long>,
  ) -> Unit,
  onDeleteCategory: (classificationId: String) -> Unit,
  onSaveBudgetHeader: (
    expectedIncomeCents: Long?,
    savingsGoalCents: Long?,
    globalExpenseLimitCents: Long?,
    notes: String?,
  ) -> Unit,
  modifier: Modifier = Modifier,
) {
  val financeColors = LocalFinanceColors.current
  val summary = state.selectedPeriodBudgetSummary

  var selectedFilter by remember { mutableStateOf(BudgetFilter.ALL) }
  var expandedCategoryIds by remember { mutableStateOf<Set<String>>(emptySet()) }
  var editingCategoryId by remember { mutableStateOf<String?>(null) }
  var showAddCategoryPicker by remember { mutableStateOf(false) }
  var showGoalsSheet by remember { mutableStateOf(false) }
  var showSuggestionsConfirm by remember { mutableStateOf(false) }
  var showCloneConfirm by remember { mutableStateOf(false) }

  val filteredCategories =
    remember(summary.categories, selectedFilter) {
      when (selectedFilter) {
        BudgetFilter.ALL -> summary.categories
        BudgetFilter.AT_RISK ->
          summary.categories.filter {
            it.alertStatus == BudgetAlertStatus.WARNING || it.paceStatus == BudgetPaceStatus.AT_RISK
          }
        BudgetFilter.EXCEEDED ->
          summary.categories.filter { it.alertStatus == BudgetAlertStatus.EXCEEDED }
        BudgetFilter.WITH_SUBLIMITS -> summary.categories.filter { it.subLimits.isNotEmpty() }
      }
    }

  val expenseClassifications =
    remember(state.allClassifications) {
      state.allClassifications
        .filter { it.isActive && TransactionTypes.isExpense(it.typeId) }
        .sortedWith(compareBy<ClassificationEntity> { it.sortOrder }.thenBy { it.name })
    }
  val budgetedClassIds = remember(summary.categories) { summary.categories.map { it.classificationId }.toSet() }
  val availableToAdd =
    remember(expenseClassifications, budgetedClassIds) {
      expenseClassifications.filter { it.id !in budgetedClassIds }
    }

  LazyColumn(
    modifier = modifier.fillMaxSize().testTag("budget_screen_list"),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    // A. Top Header + Period Selector + Quick Actions (Clonar / Sugerir / Metas)
    item {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Presupuesto",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "${summary.periodStartDate} – ${summary.periodEndDate}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CompactActionChip(
              label = "Clonar",
              icon = Icons.Rounded.ContentCopy,
              accentColor = financeColors.periodAccent,
              containerColor = financeColors.periodContainer,
              onClick = { showCloneConfirm = true },
              modifier = Modifier.testTag("budget_clone_btn"),
            )
            CompactActionChip(
              label = "Sugerir",
              icon = Icons.Rounded.AutoAwesome,
              accentColor = financeColors.incomeAccent,
              containerColor = financeColors.incomeContainer,
              onClick = { showSuggestionsConfirm = true },
              modifier = Modifier.testTag("budget_suggest_btn"),
            )
          }
        }

        // Period Selector Bar
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.surface,
          border = BorderStroke(1.dp, financeColors.cardBorder),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            IconButton(
              onClick = onPrevPeriod,
              modifier = Modifier.size(36.dp).testTag("budget_prev_period"),
            ) {
              Icon(Icons.Rounded.ChevronLeft, contentDescription = "Periodo anterior")
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                text = "${summary.fiscalPeriodLabel} (corte 15)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = financeColors.periodAccent,
              )
              val sourceLabel =
                when (summary.source) {
                  BudgetSource.EXPLICIT -> "Plan guardado"
                  BudgetSource.INHERITED_FROM_PREVIOUS ->
                    "Heredado de ${summary.inheritedFromPeriodKey ?: "anterior"}"
                  BudgetSource.CATALOG_DEFAULT -> "Base del catálogo"
                }
              Text(
                text = sourceLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            IconButton(
              onClick = onNextPeriod,
              modifier = Modifier.size(36.dp).testTag("budget_next_period"),
            ) {
              Icon(Icons.Rounded.ChevronRight, contentDescription = "Siguiente periodo")
            }
          }
        }
      }
    }

    // B. Global KPI Card
    item {
      BudgetGlobalCard(
        summary = summary,
        onEditGoals = { showGoalsSheet = true },
      )
    }

    // Filter Chips Row + Add Category CTA
    item {
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        BudgetFilter.entries.forEach { filter ->
          val selected = selectedFilter == filter
          Surface(
            onClick = { selectedFilter = filter },
            shape = RoundedCornerShape(10.dp),
            color =
              if (selected) financeColors.periodAccent else MaterialTheme.colorScheme.surface,
            border =
              BorderStroke(
                1.dp,
                if (selected) financeColors.periodAccent else financeColors.cardBorder,
              ),
            modifier = Modifier.testTag("budget_filter_${filter.name.lowercase()}"),
          ) {
            Text(
              text = filter.label,
              style = MaterialTheme.typography.labelMedium,
              fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
              color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            )
          }
        }
        if (availableToAdd.isNotEmpty()) {
          Surface(
            onClick = { showAddCategoryPicker = true },
            shape = RoundedCornerShape(10.dp),
            color = financeColors.incomeContainer,
            border = BorderStroke(1.dp, financeColors.incomeAccent.copy(alpha = 0.5f)),
            modifier = Modifier.testTag("budget_add_category_btn"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                tint = financeColors.incomeAccent,
                modifier = Modifier.size(16.dp),
              )
              Text(
                text = "Agregar rubro",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = financeColors.incomeAccent,
              )
            }
          }
        }
      }
    }

    // C. Budgeted Categories List
    if (filteredCategories.isEmpty()) {
      item {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.surface,
          border = BorderStroke(1.dp, financeColors.cardBorder),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text =
                if (summary.categories.isEmpty()) "Sin rubros presupuestados en este periodo"
                else "No hay rubros que coincidan con este filtro",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              text =
                if (summary.categories.isEmpty())
                  "Agrega un rubro manualmente, clona el periodo anterior o aplica sugerencias automáticas."
                else "Cambia el filtro superior para ver todas tus categorías.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (summary.categories.isEmpty() && availableToAdd.isNotEmpty()) {
              Button(
                onClick = { showAddCategoryPicker = true },
                shape = RoundedCornerShape(12.dp),
              ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Configurar primer rubro")
              }
            }
          }
        }
      }
    } else {
      items(filteredCategories, key = { it.classificationId }) { category ->
        val isExpanded = category.classificationId in expandedCategoryIds
        BudgetCategoryCard(
          category = category,
          isExpanded = isExpanded,
          onToggleExpand = {
            expandedCategoryIds =
              if (isExpanded) expandedCategoryIds - category.classificationId
              else expandedCategoryIds + category.classificationId
          },
          onEdit = { editingCategoryId = category.classificationId },
        )
      }
    }

    // Unbudgeted Categories with spending in this period
    if (summary.unbudgetedCategories.isNotEmpty() && selectedFilter == BudgetFilter.ALL) {
      item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "Gastos sin presupuesto asignado",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
          )
          summary.unbudgetedCategories.forEach { unbudgeted ->
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = MaterialTheme.colorScheme.surface,
              border = BorderStroke(1.dp, financeColors.cardBorder),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = unbudgeted.classificationName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                  )
                  Text(
                    text = "Gastado: ${FiscalPeriodCalculator.formatMxn(unbudgeted.spentCents)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = MonoFontFamily,
                    color = financeColors.expenseAccent,
                  )
                }
                TextButton(onClick = { editingCategoryId = unbudgeted.classificationId }) {
                  Text("Asignar límite", fontWeight = FontWeight.SemiBold)
                }
              }
            }
          }
        }
      }
    }
  }

  // Modal: Configure Category Budget + Rollover + Sub-limits
  val activeEditClassId = editingCategoryId
  if (activeEditClassId != null) {
    val cls = state.allClassifications.firstOrNull { it.id == activeEditClassId }
    if (cls != null) {
      val existingEval = summary.categories.firstOrNull { it.classificationId == activeEditClassId }
      val detailsForClass =
        state.allDetails
          .filter { it.isActive && it.classificationId == activeEditClassId }
          .sortedWith(compareBy<DetailItemEntity> { it.sortOrder }.thenBy { it.name })

      BudgetConfigBottomSheet(
        classification = cls,
        existingEvaluation = existingEval,
        availableDetails = detailsForClass,
        onDismiss = { editingCategoryId = null },
        onSave = { baseCents, threshold, rolloverEnabled, rolloverCap, subLimits ->
          onSaveCategoryConfig(
            activeEditClassId,
            baseCents,
            threshold,
            rolloverEnabled,
            rolloverCap,
            subLimits,
          )
          editingCategoryId = null
        },
        onDelete =
          if (existingEval != null) {
            {
              onDeleteCategory(activeEditClassId)
              editingCategoryId = null
            }
          } else null,
      )
    }
  }

  // Picker to add a new expense category to budget
  if (showAddCategoryPicker) {
    AlertDialog(
      onDismissRequest = { showAddCategoryPicker = false },
      title = { Text("Agregar rubro al presupuesto", fontWeight = FontWeight.Bold) },
      text = {
        Column(
          modifier = Modifier.verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          availableToAdd.forEach { cls ->
            Surface(
              onClick = {
                showAddCategoryPicker = false
                editingCategoryId = cls.id
              },
              shape = RoundedCornerShape(10.dp),
              color = financeColors.subtleSurface,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(cls.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showAddCategoryPicker = false }) { Text("Cerrar") }
      },
    )
  }

  // Modal: Global Goals (Expected Income, Savings Goal, Global Cap, Notes)
  if (showGoalsSheet) {
    BudgetGoalsBottomSheet(
      summary = summary,
      onDismiss = { showGoalsSheet = false },
      onSave = { income, savings, globalCap, notes ->
        onSaveBudgetHeader(income, savings, globalCap, notes)
        showGoalsSheet = false
      },
    )
  }

  // Confirm Clone
  if (showCloneConfirm) {
    val prevLabel = state.selectedPeriod.previous().label
    AlertDialog(
      onDismissRequest = { showCloneConfirm = false },
      title = { Text("Clonar presupuesto", fontWeight = FontWeight.Bold) },
      text = {
        Text(
          "Se copiarán los límites, configuración de rollover y sub-límites desde $prevLabel hacia ${summary.fiscalPeriodLabel}."
        )
      },
      confirmButton = {
        Button(
          onClick = {
            showCloneConfirm = false
            onClonePreviousPeriod()
          }
        ) {
          Text("Clonar")
        }
      },
      dismissButton = {
        TextButton(onClick = { showCloneConfirm = false }) { Text("Cancelar") }
      },
    )
  }

  // Confirm Suggestions
  if (showSuggestionsConfirm) {
    val suggestions = state.selectedPeriodBudgetSuggestions
    AlertDialog(
      onDismissRequest = { showSuggestionsConfirm = false },
      title = { Text("Sugerencias automáticas", fontWeight = FontWeight.Bold) },
      text = {
        if (suggestions.categories.isEmpty()) {
          Text(
            "No se encontraron gastos registrados en los últimos 3 periodos cerrados (${suggestions.analyzedPeriods.joinToString(", ")})."
          )
        } else {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
              "Basado en los últimos ${suggestions.lookbackPeriods} periodos cerrados (redondeado a múltiplos de \$50):",
              style = MaterialTheme.typography.bodySmall,
            )
            suggestions.categories.take(6).forEach { cat ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(cat.classificationName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                Text(
                  FiscalPeriodCalculator.formatMxn(cat.suggestedCents),
                  style = MaterialTheme.typography.bodySmall,
                  fontFamily = MonoFontFamily,
                  fontWeight = FontWeight.Bold,
                )
              }
            }
            if (suggestions.categories.size > 6) {
              Text(
                "...y ${suggestions.categories.size - 6} rubros más",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text("Total sugerido", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
              Text(
                FiscalPeriodCalculator.formatMxn(suggestions.totalSuggestedCents),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.Bold,
                color = financeColors.incomeAccent,
              )
            }
          }
        }
      },
      confirmButton = {
        if (suggestions.categories.isNotEmpty()) {
          Button(
            onClick = {
              showSuggestionsConfirm = false
              onApplySuggestions()
            }
          ) {
            Text("Aplicar sugerencias")
          }
        }
      },
      dismissButton = {
        TextButton(onClick = { showSuggestionsConfirm = false }) { Text("Cancelar") }
      },
    )
  }
}

@Composable
private fun CompactActionChip(
  label: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  accentColor: Color,
  containerColor: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(10.dp),
    color = containerColor,
    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.55f)),
    modifier = modifier,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(15.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = accentColor,
      )
    }
  }
}

@Composable
private fun BudgetGlobalCard(
  summary: BudgetPeriodSummary,
  onEditGoals: () -> Unit,
) {
  val financeColors = LocalFinanceColors.current
  val isOverBudget = summary.totalRemainingCents < 0L
  val heroAmountColor =
    if (isOverBudget) financeColors.expenseAccent else financeColors.incomeAccent
  val progressColor =
    when (summary.overallAlertStatus) {
      BudgetAlertStatus.EXCEEDED -> financeColors.expenseAccent
      BudgetAlertStatus.WARNING -> financeColors.pendingAccent
      BudgetAlertStatus.OK -> financeColors.incomeAccent
    }
  val progressFraction by
    animateFloatAsState(
      targetValue = (summary.totalConsumedPercent.toFloat() / 100f).coerceIn(0f, 1f),
      label = "global_budget_progress",
    )

  Surface(
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, financeColors.cardBorder),
    modifier = Modifier.fillMaxWidth().testTag("budget_global_kpi_card"),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = if (isOverBudget) "SOBREGIRO DEL PERIODO" else "DISPONIBLE PARA GASTAR",
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          letterSpacing = 0.6.sp,
        )
        Surface(
          onClick = onEditGoals,
          shape = RoundedCornerShape(8.dp),
          color = financeColors.subtleSurface,
          modifier = Modifier.testTag("budget_edit_goals_btn"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
              Icons.Rounded.Tune,
              contentDescription = "Metas",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(14.dp),
            )
            Text(
              text = "Metas",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
      ) {
        Text(
          text = FiscalPeriodCalculator.formatMxn(summary.totalRemainingCents),
          style = MaterialTheme.typography.headlineMedium,
          fontFamily = MonoFontFamily,
          fontWeight = FontWeight.Bold,
          color = heroAmountColor,
        )
        Text(
          text = "${summary.totalConsumedPercent.roundToInt()}%",
          style = MaterialTheme.typography.titleMedium,
          fontFamily = MonoFontFamily,
          fontWeight = FontWeight.Bold,
          color = progressColor,
        )
      }

      Text(
        text =
          "Meta: ${FiscalPeriodCalculator.formatMxn(summary.totalEffectiveBudgetCents)}  |  Real: ${FiscalPeriodCalculator.formatMxn(summary.totalSpentCents)}",
        style = MaterialTheme.typography.labelMedium,
        fontFamily = MonoFontFamily,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      // Progress Bar
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(financeColors.subtleSurface)
      ) {
        Box(
          modifier =
            Modifier.fillMaxWidth(progressFraction)
              .fillMaxHeight()
              .clip(RoundedCornerShape(5.dp))
              .background(progressColor)
        )
      }

      HorizontalDivider(color = financeColors.cardBorder.copy(alpha = 0.6f))

      // Financial Intelligence Lines
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // 1. Daily pace
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(
            Icons.Rounded.Lightbulb,
            contentDescription = null,
            tint = financeColors.periodAccent,
            modifier = Modifier.size(16.dp),
          )
          Text(
            text =
              if (summary.daysRemaining > 0) {
                "Ritmo sugerido: ${FiscalPeriodCalculator.formatMxn(summary.dailyAllowanceCents)} / día (${summary.daysRemaining} días restantes)"
              } else {
                "Periodo cerrado (${summary.totalDays} días)"
              },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }

        // 2. Burn rate projection
        val paceColor =
          when (summary.overallPaceStatus) {
            BudgetPaceStatus.EXCEEDED -> financeColors.expenseAccent
            BudgetPaceStatus.AT_RISK -> financeColors.pendingAccent
            BudgetPaceStatus.ON_TRACK -> financeColors.incomeAccent
          }
        val paceLabel =
          when (summary.overallPaceStatus) {
            BudgetPaceStatus.EXCEEDED -> "Excedido"
            BudgetPaceStatus.AT_RISK -> "En riesgo"
            BudgetPaceStatus.ON_TRACK -> "En meta"
          }
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(
            Icons.AutoMirrored.Rounded.TrendingUp,
            contentDescription = null,
            tint = paceColor,
            modifier = Modifier.size(16.dp),
          )
          Text(
            text =
              "Proyección: ${FiscalPeriodCalculator.formatMxn(summary.projectedSpentCents)} ($paceLabel)",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = paceColor,
          )
        }

        // 3. Pending scheduled expenses (informational)
        if (summary.pendingScheduledExpenseCents > 0L) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              Icons.Rounded.Schedule,
              contentDescription = null,
              tint = financeColors.pendingAccent,
              modifier = Modifier.size(16.dp),
            )
            Text(
              text =
                "Fijos pendientes: ${FiscalPeriodCalculator.formatMxn(summary.pendingScheduledExpenseCents)} (informativo)",
              style = MaterialTheme.typography.bodySmall,
              color = financeColors.pendingAccent,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun BudgetCategoryCard(
  category: BudgetCategoryEvaluation,
  isExpanded: Boolean,
  onToggleExpand: () -> Unit,
  onEdit: () -> Unit,
) {
  val financeColors = LocalFinanceColors.current
  val statusColor =
    when (category.alertStatus) {
      BudgetAlertStatus.EXCEEDED -> financeColors.expenseAccent
      BudgetAlertStatus.WARNING -> financeColors.pendingAccent
      BudgetAlertStatus.OK -> financeColors.incomeAccent
    }
  val borderColor by
    animateColorAsState(
      targetValue =
        when (category.alertStatus) {
          BudgetAlertStatus.EXCEEDED -> financeColors.expenseAccent.copy(alpha = 0.7f)
          BudgetAlertStatus.WARNING -> financeColors.pendingAccent.copy(alpha = 0.55f)
          BudgetAlertStatus.OK -> financeColors.cardBorder
        },
      label = "category_card_border",
    )
  val progressFraction by
    animateFloatAsState(
      targetValue = (category.consumedPercent.toFloat() / 100f).coerceIn(0f, 1f),
      label = "category_progress_${category.classificationId}",
    )

  Surface(
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, borderColor),
    modifier = Modifier.fillMaxWidth().testTag("budget_category_card_${category.classificationId}"),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clickable {
              if (category.subLimits.isNotEmpty()) onToggleExpand() else onEdit()
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = category.classificationName,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            if (category.rolloverEnabled) {
              val capSuffix =
                category.rolloverCapCents?.let {
                  " (Cap ${FiscalPeriodCalculator.formatMxn(it)})"
                } ?: ""
              val rolloverText =
                if (category.rolloverCents > 0L) {
                  "Rollover +${FiscalPeriodCalculator.formatMxn(category.rolloverCents)}$capSuffix"
                } else {
                  "Rollover$capSuffix"
                }
              Surface(
                shape = RoundedCornerShape(6.dp),
                color = financeColors.incomeContainer,
                border = BorderStroke(1.dp, financeColors.incomeAccent.copy(alpha = 0.4f)),
              ) {
                Text(
                  text = rolloverText,
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.SemiBold,
                  color = financeColors.incomeAccent,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }

          Text(
            text =
              "${FiscalPeriodCalculator.formatMxn(category.spentCents)} / ${FiscalPeriodCalculator.formatMxn(category.effectiveBudgetCents)}",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = MonoFontFamily,
            fontWeight = FontWeight.SemiBold,
            color =
              if (category.alertStatus == BudgetAlertStatus.EXCEEDED) financeColors.expenseAccent
              else MaterialTheme.colorScheme.onSurface,
          )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
            Icon(
              Icons.Rounded.Edit,
              contentDescription = "Configurar ${category.classificationName}",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(18.dp),
            )
          }
          if (category.subLimits.isNotEmpty()) {
            IconButton(onClick = onToggleExpand, modifier = Modifier.size(34.dp)) {
              Icon(
                imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = if (isExpanded) "Colapsar sub-límites" else "Expandir sub-límites",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Progress bar
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(financeColors.subtleSurface)
      ) {
        Box(
          modifier =
            Modifier.fillMaxWidth(progressFraction)
              .fillMaxHeight()
              .clip(RoundedCornerShape(4.dp))
              .background(statusColor)
        )
      }

      // Alert / Overspend caption
      when (category.alertStatus) {
        BudgetAlertStatus.EXCEEDED -> {
          val excess = abs(category.remainingCents)
          val excessPct = (category.consumedPercent - 100.0).coerceAtLeast(0.0).roundToInt()
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "Sobregiro de +${FiscalPeriodCalculator.formatMxn(excess)} (+$excessPct% excedido)",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = financeColors.expenseAccent,
          )
        }
        BudgetAlertStatus.WARNING -> {
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text =
              "Alerta: ${category.consumedPercent.roundToInt()}% consumido — Restan ${FiscalPeriodCalculator.formatMxn(category.remainingCents)}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = financeColors.pendingAccent,
          )
        }
        BudgetAlertStatus.OK -> {
          if (category.subLimits.isNotEmpty() && !isExpanded) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "${category.subLimits.size} sub-límites configurados • Toca para ver detalle",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      // Collapsible Sub-limits Accordion
      AnimatedVisibility(
        visible = isExpanded && category.subLimits.isNotEmpty(),
        enter = expandVertically(),
        exit = shrinkVertically(),
      ) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = financeColors.subtleSurface,
          border = BorderStroke(1.dp, financeColors.cardBorder.copy(alpha = 0.7f)),
          modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text(
              text = "Sub-límites por Detalle",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = financeColors.periodAccent,
            )
            category.subLimits.forEach { sub ->
              BudgetSubLimitRow(sub = sub)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun BudgetSubLimitRow(sub: BudgetSubLimitEvaluation) {
  val financeColors = LocalFinanceColors.current
  val subColor =
    when (sub.alertStatus) {
      BudgetAlertStatus.EXCEEDED -> financeColors.expenseAccent
      BudgetAlertStatus.WARNING -> financeColors.pendingAccent
      BudgetAlertStatus.OK -> financeColors.incomeAccent
    }
  val fraction = (sub.consumedPercent.toFloat() / 100f).coerceIn(0f, 1f)

  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "• ${sub.detailName}",
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.weight(1f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text =
          "${FiscalPeriodCalculator.formatMxn(sub.spentCents)} / ${FiscalPeriodCalculator.formatMxn(sub.effectiveBudgetCents)}",
        style = MaterialTheme.typography.labelSmall,
        fontFamily = MonoFontFamily,
        fontWeight = FontWeight.SemiBold,
        color = if (sub.alertStatus == BudgetAlertStatus.OK) MaterialTheme.colorScheme.onSurface else subColor,
      )
    }
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .height(6.dp)
          .clip(RoundedCornerShape(3.dp))
          .background(MaterialTheme.colorScheme.surface)
    ) {
      Box(
        modifier =
          Modifier.fillMaxWidth(fraction)
            .fillMaxHeight()
            .clip(RoundedCornerShape(3.dp))
            .background(subColor)
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetConfigBottomSheet(
  classification: ClassificationEntity,
  existingEvaluation: BudgetCategoryEvaluation?,
  availableDetails: List<DetailItemEntity>,
  onDismiss: () -> Unit,
  onSave: (
    baseAmountCents: Long,
    warningThresholdPercent: Int,
    rolloverEnabled: Boolean,
    rolloverCapCents: Long?,
    subLimitsByDetailId: Map<String, Long>,
  ) -> Unit,
  onDelete: (() -> Unit)?,
) {
  val financeColors = LocalFinanceColors.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  var baseAmountInput by remember {
    mutableStateOf(
      existingEvaluation?.budgetedCents?.takeIf { it > 0L }?.let { centsToEditableString(it) }
        ?: classification.monthlyBudgetCents?.takeIf { it > 0L }?.let { centsToEditableString(it) }
        ?: ""
    )
  }
  var warningThresholdInput by remember {
    mutableStateOf((existingEvaluation?.warningThresholdPercent ?: 80).toString())
  }
  var rolloverEnabled by remember {
    mutableStateOf(existingEvaluation?.rolloverEnabled ?: false)
  }
  var rolloverCapInput by remember {
    mutableStateOf(
      existingEvaluation?.rolloverCapCents?.takeIf { it > 0L }?.let { centsToEditableString(it) }
        ?: ""
    )
  }

  val subLimitInputs = remember {
    mutableStateMapOf<String, String>().apply {
      existingEvaluation?.subLimits?.forEach { sub ->
        if (sub.budgetedCents > 0L) {
          put(sub.detailId, centsToEditableString(sub.budgetedCents))
        }
      }
    }
  }

  val baseAmountCents = FiscalPeriodCalculator.parseAmountToCents(baseAmountInput)
  val parsedSubLimits =
    availableDetails.associate { detail ->
      detail.id to FiscalPeriodCalculator.parseAmountToCents(subLimitInputs[detail.id].orEmpty())
    }
  val validation = BudgetEngine.validateSubLimits(baseAmountCents, parsedSubLimits.values)
  val canSave = baseAmountCents > 0L && validation.isValid

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .imePadding()
          .navigationBarsPadding()
          .padding(horizontal = 20.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Presupuesto: ${classification.name}",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        if (onDelete != null) {
          IconButton(onClick = onDelete) {
            Icon(
              Icons.Rounded.DeleteOutline,
              contentDescription = "Quitar rubro",
              tint = financeColors.expenseAccent,
            )
          }
        }
      }

      // Field 1: Base monthly limit
      OutlinedTextField(
        value = baseAmountInput,
        onValueChange = { baseAmountInput = sanitizeDecimalInput(it) },
        label = { Text("Límite Base Mensual (MXN)") },
        prefix = { Text("$") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth().testTag("budget_config_base_input"),
      )

      // Warning threshold
      OutlinedTextField(
        value = warningThresholdInput,
        onValueChange = { raw ->
          warningThresholdInput = raw.filter { it.isDigit() }.take(2)
        },
        label = { Text("Umbral de alerta preventiva (%)") },
        suffix = { Text("%") },
        supportingText = { Text("Por defecto 80% (rango 1–99%)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
      )

      HorizontalDivider()

      // Rollover section
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Habilitar Rollover Positivo",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            text = "Acumula el sobrante no gastado del periodo anterior",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = rolloverEnabled,
          onCheckedChange = { rolloverEnabled = it },
          modifier = Modifier.testTag("budget_config_rollover_switch"),
        )
      }

      AnimatedVisibility(visible = rolloverEnabled) {
        OutlinedTextField(
          value = rolloverCapInput,
          onValueChange = { rolloverCapInput = sanitizeDecimalInput(it) },
          label = { Text("Tope Máximo Transferible (Cap opcional)") },
          prefix = { Text("$") },
          placeholder = { Text("Sin tope") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          modifier = Modifier.fillMaxWidth().testTag("budget_config_rollover_cap_input"),
        )
      }

      // Sub-limits section
      if (availableDetails.isNotEmpty()) {
        HorizontalDivider()
        Text(
          text = "Sub-límites opcionales por Detalle",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = financeColors.periodAccent,
        )

        availableDetails.forEach { detail ->
          OutlinedTextField(
            value = subLimitInputs[detail.id].orEmpty(),
            onValueChange = { subLimitInputs[detail.id] = sanitizeDecimalInput(it) },
            label = { Text(detail.name) },
            prefix = { Text("$") },
            placeholder = { Text("Opcional") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
          )
        }

        // Live sum validation feedback
        val hasAnySubLimit = validation.sumSubLimitsCents > 0L
        if (hasAnySubLimit) {
          val valColor =
            if (validation.isValid) financeColors.incomeAccent else financeColors.expenseAccent
          val valText =
            if (validation.isValid) {
              "Suma sub-límites: ${FiscalPeriodCalculator.formatMxn(validation.sumSubLimitsCents)} <= ${FiscalPeriodCalculator.formatMxn(baseAmountCents)} base (Validación OK)"
            } else {
              "La suma de sub-detalles (${FiscalPeriodCalculator.formatMxn(validation.sumSubLimitsCents)}) excede el límite base (${FiscalPeriodCalculator.formatMxn(baseAmountCents)})"
            }
          Text(
            text = valText,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = valColor,
            modifier = Modifier.testTag("budget_sublimit_validation_text"),
          )
        }
      }

      Spacer(modifier = Modifier.height(4.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedButton(
          onClick = onDismiss,
          modifier = Modifier.weight(1f).height(48.dp),
          shape = RoundedCornerShape(12.dp),
        ) {
          Text("Cancelar")
        }
        Button(
          onClick = {
            val threshold = (warningThresholdInput.toIntOrNull() ?: 80).coerceIn(1, 99)
            val capCents =
              if (rolloverEnabled) {
                FiscalPeriodCalculator.parseAmountToCents(rolloverCapInput).takeIf { it > 0L }
              } else null
            onSave(baseAmountCents, threshold, rolloverEnabled, capCents, parsedSubLimits)
          },
          enabled = canSave,
          modifier = Modifier.weight(1.3f).height(48.dp).testTag("budget_config_save_btn"),
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(containerColor = financeColors.incomeAccent),
        ) {
          Text("Guardar Cambios", fontWeight = FontWeight.Bold)
        }
      }
      Spacer(modifier = Modifier.height(12.dp))
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetGoalsBottomSheet(
  summary: BudgetPeriodSummary,
  onDismiss: () -> Unit,
  onSave: (
    expectedIncomeCents: Long?,
    savingsGoalCents: Long?,
    globalExpenseLimitCents: Long?,
    notes: String?,
  ) -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var incomeInput by remember {
    mutableStateOf(
      summary.expectedIncomeCents.takeIf { it > 0L }?.let { centsToEditableString(it) } ?: ""
    )
  }
  var savingsInput by remember {
    mutableStateOf(
      summary.savingsGoalCents.takeIf { it > 0L }?.let { centsToEditableString(it) } ?: ""
    )
  }
  var globalLimitInput by remember {
    mutableStateOf(
      summary.globalExpenseLimitCents?.takeIf { it > 0L }?.let { centsToEditableString(it) } ?: ""
    )
  }
  var notesInput by remember { mutableStateOf(summary.notes.orEmpty()) }

  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .imePadding()
          .navigationBarsPadding()
          .padding(horizontal = 20.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = "Metas globales — ${summary.fiscalPeriodLabel}",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
      OutlinedTextField(
        value = incomeInput,
        onValueChange = { incomeInput = sanitizeDecimalInput(it) },
        label = { Text("Ingreso esperado del periodo (MXN)") },
        prefix = { Text("$") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
      )
      OutlinedTextField(
        value = savingsInput,
        onValueChange = { savingsInput = sanitizeDecimalInput(it) },
        label = { Text("Meta de ahorro (MXN)") },
        prefix = { Text("$") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
      )
      OutlinedTextField(
        value = globalLimitInput,
        onValueChange = { globalLimitInput = sanitizeDecimalInput(it) },
        label = { Text("Límite global de gasto (opcional)") },
        prefix = { Text("$") },
        supportingText = { Text("Si se deja vacío, se usa la suma de tus rubros") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
      )
      OutlinedTextField(
        value = notesInput,
        onValueChange = { notesInput = it },
        label = { Text("Notas del periodo") },
        maxLines = 3,
        modifier = Modifier.fillMaxWidth(),
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedButton(
          onClick = onDismiss,
          modifier = Modifier.weight(1f).height(48.dp),
          shape = RoundedCornerShape(12.dp),
        ) {
          Text("Cancelar")
        }
        Button(
          onClick = {
            onSave(
              FiscalPeriodCalculator.parseAmountToCents(incomeInput).takeIf { it > 0L },
              FiscalPeriodCalculator.parseAmountToCents(savingsInput).takeIf { it > 0L },
              FiscalPeriodCalculator.parseAmountToCents(globalLimitInput).takeIf { it > 0L },
              notesInput.trim().takeIf { it.isNotEmpty() },
            )
          },
          modifier = Modifier.weight(1.2f).height(48.dp),
          shape = RoundedCornerShape(12.dp),
        ) {
          Text("Guardar Metas", fontWeight = FontWeight.Bold)
        }
      }
      Spacer(modifier = Modifier.height(12.dp))
    }
  }
}

private fun sanitizeDecimalInput(raw: String): String {
  val filtered = raw.filter { it.isDigit() || it == '.' }
  val firstDot = filtered.indexOf('.')
  if (firstDot == -1) return filtered.take(9)
  val intPart = filtered.substring(0, firstDot).take(9)
  val fracPart = filtered.substring(firstDot + 1).replace(".", "").take(2)
  return "$intPart.$fracPart"
}

private fun centsToEditableString(cents: Long): String {
  val whole = cents / 100L
  val frac = abs(cents % 100L)
  return if (frac == 0L) whole.toString() else "$whole.${"%02d".format(frac)}"
}
