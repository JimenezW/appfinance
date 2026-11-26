package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AccountEntity
import com.example.data.local.FinanceTransactionEntity
import com.example.data.local.SyncStatus
import com.example.data.local.TransactionTypes
import com.example.domain.FiscalPeriodCalculator
import com.example.ui.CategoryStat
import com.example.ui.FinanceUiState
import com.example.ui.TransactionDisplay
import com.example.ui.components.OperationDatePickerDialog
import com.example.ui.theme.LocalFinanceColors
import com.example.ui.theme.MonoFontFamily

@Composable
fun BalancesScreen(
  state: FinanceUiState,
  onPrevPeriod: () -> Unit,
  onNextPeriod: () -> Unit,
  onToggleAccountFilter: (String) -> Unit,
  onSearchQueryChange: (String) -> Unit,
  onToggleSortDateOrder: () -> Unit = {},
  isDownloadingStatement: Boolean = false,
  onDownloadStatement: () -> Unit = {},
  onToggleStatsIncome: (Boolean) -> Unit,
  onUpdateTransaction: (FinanceTransactionEntity, FinanceTransactionEntity) -> Unit,
  onDeleteTransaction: (FinanceTransactionEntity) -> Unit,
  onNavigateToCapture: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val financeColors = LocalFinanceColors.current
  var showAllBreakdown by remember { mutableStateOf(false) }
  var showSearchBar by remember { mutableStateOf(false) }
  var editingItem by remember { mutableStateOf<TransactionDisplay?>(null) }
  var visibleMovementsCount by
    remember(state.selectedPeriod.key, state.selectedAccountFilterId, state.searchQuery) {
      mutableStateOf(5)
    }

  Box(modifier = modifier.fillMaxSize()) {
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      // Header + Fiscal Period Selector
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Resumen de Cuentas",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
          )
          FiscalPeriodSelectorPill(
            label = state.selectedPeriod.label,
            canNext = state.canNavigateNextPeriod,
            onPrev = onPrevPeriod,
            onNext = onNextPeriod,
          )
        }
      }

      // Hero Total Balance Card (updates dynamically with selectedPeriod)
      item {
        HeroTotalBalanceCard(
          periodNetBalanceCents = state.periodNetBalanceCents,
          totalAvailableCents = state.totalBalanceCents,
          periodIncomeCents = state.periodIncomeCents,
          periodExpenseCents = state.periodExpenseCents,
        )
      }

      // Download Financial Statement PDF button
      item {
        OutlinedButton(
          onClick = onDownloadStatement,
          enabled = !isDownloadingStatement,
          modifier =
            Modifier.fillMaxWidth()
              .height(46.dp)
              .testTag("download_statement_pdf_btn"),
          shape = RoundedCornerShape(12.dp),
          border = BorderStroke(1.dp, financeColors.periodAccent.copy(alpha = 0.45f)),
        ) {
          if (isDownloadingStatement) {
            CircularProgressIndicator(
              modifier = Modifier.size(18.dp),
              strokeWidth = 2.dp,
              color = financeColors.periodAccent,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Generando estado de cuenta...",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.SemiBold,
            )
          } else {
            Icon(
              imageVector = Icons.Rounded.Download,
              contentDescription = "Descargar estado de cuenta",
              modifier = Modifier.size(18.dp),
              tint = financeColors.periodAccent,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Descargar estado de cuenta (${state.selectedPeriod.label})",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.SemiBold,
              color = financeColors.periodAccent,
            )
          }
        }
      }

      // Balances by Account Horizontal Cards
      item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "Saldos por Cuenta",
              style = MaterialTheme.typography.titleSmall,
            )
            if (state.selectedAccountFilterId != null) {
              TextButton(onClick = { onToggleAccountFilter(state.selectedAccountFilterId) }) {
                Text("Quitar filtro", style = MaterialTheme.typography.labelSmall)
              }
            }
          }
          Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            val orderedAccounts =
              remember(state.activeAccounts) {
                state.activeAccounts.sortedWith(
                  compareByDescending<AccountEntity> { it.isPinned }
                    .thenBy { it.sortOrder }
                    .thenBy { it.name }
                )
              }
            orderedAccounts.forEach { acc ->
              AccountBalanceTile(
                account = acc,
                periodBalanceCents = state.periodAccountBalanceCents(acc.id),
                selected = state.selectedAccountFilterId == acc.id,
                onClick = { onToggleAccountFilter(acc.id) },
              )
            }
          }
        }
      }

      // Statistics & Mayor Rubro Card (with expandable % breakdown & budget warnings)
      item {
        StatisticsOverviewCard(
          state = state,
          expanded = showAllBreakdown,
          onToggleExpanded = { showAllBreakdown = !showAllBreakdown },
          onToggleStatsIncome = onToggleStatsIncome,
        )
      }

      // Recent Movements Header + Date Sort Toggle + Search Toggle
      item {
        val totalInPeriod = state.periodTransactions.size
        val shownCount = minOf(visibleMovementsCount, totalInPeriod)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text =
                if (totalInPeriod > 0) {
                  "Movimientos ($shownCount de $totalInPeriod)"
                } else {
                  "Movimientos (0)"
                },
              style = MaterialTheme.typography.titleSmall,
            )
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Surface(
                onClick = onToggleSortDateOrder,
                shape = RoundedCornerShape(10.dp),
                color = financeColors.subtleSurface,
                border = BorderStroke(1.dp, financeColors.cardBorder),
                modifier = Modifier.testTag("toggle_sort_tx_date_btn"),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Icon(
                    imageVector =
                      if (state.sortTransactionsAscending) Icons.Rounded.ArrowUpward
                      else Icons.Rounded.ArrowDownward,
                    contentDescription =
                      if (state.sortTransactionsAscending) {
                        "Ordenar por fecha: menor a mayor"
                      } else {
                        "Ordenar por fecha: mayor a menor"
                      },
                    tint = financeColors.periodAccent,
                    modifier = Modifier.size(16.dp),
                  )
                  Text(
                    text = if (state.sortTransactionsAscending) "Fecha ↑" else "Fecha ↓",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                }
              }
              IconButton(
                onClick = {
                  showSearchBar = !showSearchBar
                  if (!showSearchBar) onSearchQueryChange("")
                },
                modifier = Modifier.size(40.dp).testTag("toggle_search_tx_btn"),
              ) {
                Icon(
                  imageVector = if (showSearchBar) Icons.Rounded.Close else Icons.Rounded.Search,
                  contentDescription = "Buscar movimientos",
                )
              }
            }
          }
          AnimatedVisibility(visible = showSearchBar) {
            OutlinedTextField(
              value = state.searchQuery,
              onValueChange = onSearchQueryChange,
              label = { Text("Filtrar por comercio, rubro o nota") },
              leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("search_tx_input"),
              shape = RoundedCornerShape(12.dp),
            )
          }
        }
      }

      if (state.periodTransactions.isEmpty()) {
        item {
          Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, financeColors.cardBorder),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.ReceiptLong,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp),
              )
              Text(
                text = "Sin movimientos en ${state.selectedPeriod.label}",
                style = MaterialTheme.typography.titleSmall,
              )
              Text(
                text = "Toca + para registrar un gasto o ingreso en segundos.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      } else {
        val visibleItems = state.periodTransactions.take(visibleMovementsCount)
        items(visibleItems, key = { it.entity.id }) { item ->
          TransactionRowCard(item = item, onClick = { editingItem = item })
        }

        if (state.periodTransactions.size > 5) {
          val hasMoreToShow = state.periodTransactions.size > visibleMovementsCount
          item(key = "load_more_or_hide_movements_btn") {
            OutlinedButton(
              onClick = {
                if (hasMoreToShow) {
                  visibleMovementsCount += 5
                } else {
                  visibleMovementsCount = 5
                }
              },
              modifier =
                Modifier.fillMaxWidth()
                  .height(46.dp)
                  .testTag(if (hasMoreToShow) "load_more_movements_btn" else "hide_movements_btn"),
              shape = RoundedCornerShape(12.dp),
            ) {
              Text(
                text = if (hasMoreToShow) "Ver más" else "Ocultar",
                fontWeight = FontWeight.SemiBold,
              )
            }
          }
        }
      }
    }

    // Bottom FAB to jump directly to Capture (with generous bottom padding on LazyColumn above)
    FloatingActionButton(
      onClick = onNavigateToCapture,
      containerColor = financeColors.incomeAccent,
      contentColor = Color.White,
      shape = CircleShape,
      modifier =
        Modifier.align(Alignment.BottomEnd)
          .padding(16.dp)
          .testTag("balances_fab_capture"),
    ) {
      Icon(imageVector = Icons.Rounded.Add, contentDescription = "Capturar movimiento")
    }
  }

  editingItem?.let { item ->
    EditTransactionBottomSheet(
      item = item,
      state = state,
      onDismiss = { editingItem = null },
      onSave = { updated ->
        onUpdateTransaction(item.entity, updated)
        editingItem = null
      },
      onDelete = {
        onDeleteTransaction(item.entity)
        editingItem = null
      },
    )
  }
}

@Composable
private fun FiscalPeriodSelectorPill(
  label: String,
  canNext: Boolean = true,
  onPrev: () -> Unit,
  onNext: () -> Unit,
) {
  val financeColors = LocalFinanceColors.current
  Surface(
    shape = RoundedCornerShape(20.dp),
    color = financeColors.periodContainer,
    border = BorderStroke(1.dp, financeColors.periodAccent.copy(alpha = 0.3f)),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(
        onClick = onPrev,
        modifier = Modifier.size(40.dp).testTag("period_prev_btn"),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
          contentDescription = "Periodo anterior",
          tint = financeColors.periodAccent,
        )
      }
      Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = financeColors.periodAccent,
        modifier = Modifier.padding(horizontal = 4.dp),
      )
      IconButton(
        onClick = onNext,
        enabled = canNext,
        modifier = Modifier.size(40.dp).testTag("period_next_btn"),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
          contentDescription = "Siguiente periodo",
          tint =
            if (canNext) financeColors.periodAccent
            else financeColors.periodAccent.copy(alpha = 0.28f),
        )
      }
    }
  }
}

@Composable
private fun HeroTotalBalanceCard(
  periodNetBalanceCents: Long,
  totalAvailableCents: Long,
  periodIncomeCents: Long,
  periodExpenseCents: Long,
) {
  val financeColors = LocalFinanceColors.current
  val isNegative = periodNetBalanceCents < 0L
  val cardBackground =
    if (isNegative) Color(0xFF7F1D1D) else financeColors.heroCardBackground
  val titleAccent =
    if (isNegative) Color(0xFFFECACA) else Color(0xFFA7F3D0)

  Surface(
    modifier = Modifier.fillMaxWidth().testTag("hero_total_balance_card"),
    shape = RoundedCornerShape(18.dp),
    color = cardBackground,
  ) {
    Column(
      modifier = Modifier.padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "SALDO DISPONIBLE",
          style = MaterialTheme.typography.labelSmall,
          color = titleAccent,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = "Acumulado:${FiscalPeriodCalculator.formatMxn(totalAvailableCents)}",
          style = MaterialTheme.typography.labelSmall,
          color = Color.White.copy(alpha = 0.85f),
        )
      }
      Text(
        text = FiscalPeriodCalculator.formatMxn(periodNetBalanceCents),
        fontFamily = MonoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        color = Color.White,
      )
      Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Ingresos: +${FiscalPeriodCalculator.formatMxn(periodIncomeCents)}",
          style = MaterialTheme.typography.labelMedium,
          color = Color(0xFFA7F3D0),
        )
        Text(
          text = "Gastos: -${FiscalPeriodCalculator.formatMxn(periodExpenseCents)}",
          style = MaterialTheme.typography.labelMedium,
          color = Color(0xFFFECACA),
        )
      }
    }
  }
}

@Composable
private fun AccountBalanceTile(
  account: AccountEntity,
  periodBalanceCents: Long,
  selected: Boolean,
  onClick: () -> Unit,
) {
  val financeColors = LocalFinanceColors.current
  val (bg, accent) =
    when (account.accountKind) {
      "DEBIT" -> financeColors.periodContainer to financeColors.periodAccent
      "CASH" -> financeColors.incomeContainer to financeColors.incomeAccent
      "VALES" -> financeColors.pendingContainer to financeColors.pendingAccent
      "CREDIT" -> financeColors.expenseContainer to financeColors.expenseAccent
      else -> financeColors.subtleSurface to MaterialTheme.colorScheme.onSurface
    }

  Surface(
    onClick = onClick,
    modifier = Modifier.width(128.dp).testTag("balance_account_tile_${account.id}"),
    shape = RoundedCornerShape(14.dp),
    color = bg,
    border =
      BorderStroke(
        width = if (selected) 2.dp else 1.dp,
        color = if (selected) accent else accent.copy(alpha = 0.35f),
      ),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(
        text = account.shortLabel,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = accent,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = FiscalPeriodCalculator.formatMxn(periodBalanceCents),
        fontFamily = MonoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        color =
          if (periodBalanceCents < 0L) financeColors.expenseAccent
          else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
      )
    }
  }
}

@Composable
private fun StatisticsOverviewCard(
  state: FinanceUiState,
  expanded: Boolean,
  onToggleExpanded: () -> Unit,
  onToggleStatsIncome: (Boolean) -> Unit,
) {
  val financeColors = LocalFinanceColors.current
  val mayor = state.mayorRubro
  val activeBreakdown: List<CategoryStat> =
    if (state.statsShowIncome) state.incomeBreakdown else state.expenseBreakdown

  Surface(
    modifier = Modifier.fillMaxWidth().testTag("statistics_overview_card"),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, financeColors.cardBorder),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onToggleExpanded() },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text =
              if (mayor != null) {
                "Mayor Rubro (${state.selectedPeriod.label}): ${mayor.classificationName} — ${"%.1f".format(mayor.percentage)}%"
              } else {
                "Estadísticas (${state.selectedPeriod.label}): Sin gastos aún"
              },
            style = MaterialTheme.typography.titleSmall,
          )
          if (mayor != null) {
            Text(
              text =
                "${FiscalPeriodCalculator.formatMxn(mayor.totalCents)} de ${FiscalPeriodCalculator.formatMxn(state.periodExpenseCents)} gastados",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        Icon(
          imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
          contentDescription = "Ver desglose por rubro",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      if (mayor != null) {
        val ratio = (mayor.percentage / 100f).coerceIn(0f, 1f)
        val barColor =
          if (mayor.budgetRatio != null && mayor.budgetRatio!! >= 1f) financeColors.expenseAccent
          else if (mayor.budgetRatio != null && mayor.budgetRatio!! >= 0.8f)
            financeColors.pendingAccent
          else financeColors.incomeAccent

        LinearProgressIndicator(
          progress = { ratio },
          modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
          color = barColor,
          trackColor = financeColors.subtleSurface,
        )
      }

      // Budget alert banners across any category nearing/exceeding monthly limit
      val budgetAlerts =
        state.expenseBreakdown.filter { it.budgetRatio != null && it.budgetRatio!! >= 0.8f }
      if (budgetAlerts.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          budgetAlerts.forEach { alert ->
            val over = (alert.budgetRatio ?: 0f) >= 1f
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = if (over) financeColors.expenseContainer else financeColors.pendingContainer,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Icon(
                  imageVector = Icons.Rounded.WarningAmber,
                  contentDescription = null,
                  tint = if (over) financeColors.expenseAccent else financeColors.pendingAccent,
                  modifier = Modifier.size(16.dp),
                )
                Text(
                  text =
                    if (over) {
                      "${alert.classificationName}: superó límite (${FiscalPeriodCalculator.formatMxn(alert.totalCents)} / ${FiscalPeriodCalculator.formatMxn(alert.monthlyBudgetCents ?: 0L)})"
                    } else {
                      "${alert.classificationName}: al ${"%.0f".format((alert.budgetRatio ?: 0f) * 100f)}% del presupuesto"
                    },
                  style = MaterialTheme.typography.bodySmall,
                  fontWeight = FontWeight.SemiBold,
                  color =
                    if (over) financeColors.onExpenseContainer else financeColors.onPendingContainer,
                )
              }
            }
          }
        }
      }

      AnimatedVisibility(visible = expanded) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
              onClick = { onToggleStatsIncome(false) },
              shape = RoundedCornerShape(8.dp),
              color =
                if (!state.statsShowIncome) financeColors.expenseContainer
                else financeColors.subtleSurface,
            ) {
              Text(
                text = "Gastos (%)",
                style = MaterialTheme.typography.labelMedium,
                color =
                  if (!state.statsShowIncome) financeColors.expenseAccent
                  else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
              )
            }
            Surface(
              onClick = { onToggleStatsIncome(true) },
              shape = RoundedCornerShape(8.dp),
              color =
                if (state.statsShowIncome) financeColors.incomeContainer
                else financeColors.subtleSurface,
            ) {
              Text(
                text = "Ingresos (%)",
                style = MaterialTheme.typography.labelMedium,
                color =
                  if (state.statsShowIncome) financeColors.incomeAccent
                  else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
              )
            }
          }

          if (activeBreakdown.isEmpty()) {
            Text(
              text = "Sin registros en este periodo.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          } else {
            activeBreakdown.forEach { stat ->
              Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Text(
                    text = "${stat.classificationName} (${"%.1f".format(stat.percentage)}%)",
                    style = MaterialTheme.typography.labelMedium,
                  )
                  Text(
                    text = FiscalPeriodCalculator.formatMxn(stat.totalCents),
                    fontFamily = MonoFontFamily,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                  )
                }
                LinearProgressIndicator(
                  progress = { (stat.percentage / 100f).coerceIn(0f, 1f) },
                  modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                  color =
                    if (state.statsShowIncome) financeColors.incomeAccent
                    else financeColors.expenseAccent,
                  trackColor = financeColors.subtleSurface,
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun TransactionRowCard(
  item: TransactionDisplay,
  onClick: () -> Unit,
) {
  val financeColors = LocalFinanceColors.current
  val isExpense = item.isEffectiveOutflow
  val amountColor = if (isExpense) financeColors.expenseAccent else financeColors.incomeAccent
  val subtitleParts = buildList {
    if (!item.entity.merchantName.isNullOrBlank()) add(item.entity.merchantName)
    add(item.accountShortLabel)
    add(FiscalPeriodCalculator.formatShortDate(item.entity.operationEpochDay))
  }

  Surface(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth().testTag("tx_row_${item.entity.id}"),
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, financeColors.cardBorder),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
        Text(
          text = "${item.classificationName} • ${item.detailName}",
          style = MaterialTheme.typography.titleSmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = subtitleParts.joinToString(" • "),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (!item.entity.note.isNullOrBlank()) {
          Text(
            text = item.entity.note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      Column(horizontalAlignment = Alignment.End) {
        Text(
          text =
            FiscalPeriodCalculator.formatMxn(
              cents = item.entity.amountCents,
              includeSign = true,
              isExpense = isExpense,
            ),
          fontFamily = MonoFontFamily,
          fontWeight = FontWeight.Bold,
          fontSize = 15.sp,
          color = amountColor,
        )
        Text(
          text = item.entity.fiscalPeriodLabel,
          style = MaterialTheme.typography.labelSmall,
          color = financeColors.periodAccent,
        )
        val (syncLabel, syncColor) =
          when (item.entity.syncStatus) {
            SyncStatus.SYNCED -> "Sincronizado ✓" to financeColors.incomeAccent
            SyncStatus.FAILED -> "Reintentar sync" to financeColors.expenseAccent
            else -> "Pendiente Wi-Fi" to financeColors.pendingAccent
          }
        Text(
          text = syncLabel,
          style = MaterialTheme.typography.labelSmall,
          color = syncColor,
          fontWeight = FontWeight.Medium,
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditTransactionBottomSheet(
  item: TransactionDisplay,
  state: FinanceUiState,
  onDismiss: () -> Unit,
  onSave: (FinanceTransactionEntity) -> Unit,
  onDelete: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var amountText by remember {
    mutableStateOf("%.2f".format(java.util.Locale.US, item.entity.amountCents / 100.0))
  }
  var epochDay by remember { mutableStateOf(item.entity.operationEpochDay) }
  var merchantText by remember { mutableStateOf(item.entity.merchantName ?: "") }
  var noteText by remember { mutableStateOf(item.entity.note ?: "") }
  var selectedAccountId by remember { mutableStateOf(item.entity.accountId) }
  var showDatePicker by remember { mutableStateOf(false) }
  var confirmDelete by remember { mutableStateOf(false) }

  val parsedCents = FiscalPeriodCalculator.parseAmountToCents(amountText)
  val computedPeriod = FiscalPeriodCalculator.computeFiscalPeriod(epochDay)
  val isEditable = state.isTransactionEditable(item.entity)
  val financeColors = LocalFinanceColors.current

  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .navigationBarsPadding()
          .imePadding()
          .padding(horizontal = 16.dp)
          .padding(bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "${item.classificationName} • ${item.detailName}",
          style = MaterialTheme.typography.titleMedium,
        )
        if (isEditable) {
          IconButton(onClick = { confirmDelete = true }) {
            Icon(
              imageVector = Icons.Rounded.DeleteOutline,
              contentDescription = "Eliminar movimiento",
              tint = MaterialTheme.colorScheme.error,
            )
          }
        }
      }

      if (!isEditable) {
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = financeColors.pendingContainer,
          border = BorderStroke(1.dp, financeColors.pendingAccent.copy(alpha = 0.4f)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.WarningAmber,
              contentDescription = null,
              tint = financeColors.pendingAccent,
              modifier = Modifier.size(18.dp),
            )
            Text(
              text =
                "Periodo cerrado (${item.entity.fiscalPeriodLabel.ifBlank { state.selectedPeriod.label }}): no se permite modificar ni eliminar movimientos de periodos anteriores al actual (${state.currentFiscalPeriod.label}).",
              style = MaterialTheme.typography.bodySmall,
              color = financeColors.onPendingContainer,
            )
          }
        }
      }

      OutlinedTextField(
        value = amountText,
        onValueChange = { if (isEditable) amountText = it },
        enabled = isEditable,
        label = { Text("Importe (MXN)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedButton(
          onClick = { if (isEditable) showDatePicker = true },
          enabled = isEditable,
          modifier = Modifier.weight(1f),
          shape = RoundedCornerShape(12.dp),
        ) {
          Icon(Icons.Rounded.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(FiscalPeriodCalculator.formatShortDate(epochDay))
        }
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = LocalFinanceColors.current.periodContainer,
          modifier = Modifier.weight(1f).height(40.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Text(
              text = "Periodo: ${computedPeriod.label}",
              style = MaterialTheme.typography.labelMedium,
              color = LocalFinanceColors.current.periodAccent,
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }

      Text("Cuenta", style = MaterialTheme.typography.labelMedium)
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        state.activeAccounts.forEach { acc ->
          val sel = acc.id == selectedAccountId
          Surface(
            onClick = { if (isEditable) selectedAccountId = acc.id },
            shape = RoundedCornerShape(10.dp),
            color =
              if (sel) MaterialTheme.colorScheme.primaryContainer
              else MaterialTheme.colorScheme.surfaceVariant,
          ) {
            Text(
              text = acc.shortLabel,
              style = MaterialTheme.typography.labelMedium,
              fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
          }
        }
      }

      OutlinedTextField(
        value = merchantText,
        onValueChange = { if (isEditable) merchantText = it },
        enabled = isEditable,
        label = { Text("Comercio / Origen") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
      )

      OutlinedTextField(
        value = noteText,
        onValueChange = { if (isEditable) noteText = it },
        enabled = isEditable,
        label = { Text("Nota") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
      )

      if (isEditable) {
        Button(
          onClick = {
            if (parsedCents > 0L) {
              onSave(
                item.entity.copy(
                  amountCents = parsedCents,
                  operationEpochDay = epochDay,
                  fiscalPeriodKey = computedPeriod.key,
                  fiscalPeriodLabel = computedPeriod.label,
                  accountId = selectedAccountId,
                  merchantName = merchantText.trim().ifEmpty { null },
                  note = noteText.trim().ifEmpty { null },
                )
              )
            }
          },
          enabled = parsedCents > 0L,
          modifier = Modifier.fillMaxWidth().height(48.dp),
          shape = RoundedCornerShape(12.dp),
        ) {
          Text("Guardar cambios")
        }
      } else {
        OutlinedButton(
          onClick = onDismiss,
          modifier = Modifier.fillMaxWidth().height(48.dp),
          shape = RoundedCornerShape(12.dp),
        ) {
          Text("Cerrar (solo lectura)")
        }
      }
    }
  }

  if (showDatePicker) {
    OperationDatePickerDialog(
      initialEpochDay = epochDay,
      onDismiss = { showDatePicker = false },
      onDateSelected = { epochDay = it },
    )
  }

  if (confirmDelete) {
    AlertDialog(
      onDismissRequest = { confirmDelete = false },
      title = { Text("Eliminar movimiento") },
      text = { Text("Se eliminará este registro y se ajustará automáticamente el saldo de la cuenta.") },
      confirmButton = {
        Button(
          onClick = {
            confirmDelete = false
            onDelete()
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) {
          Text("Eliminar")
        }
      },
      dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
    )
  }
}
