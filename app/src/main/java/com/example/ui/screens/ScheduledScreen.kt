package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.sp
import com.example.data.local.ScheduledRuleEntity
import com.example.data.local.TransactionTypes
import com.example.domain.FiscalPeriodCalculator
import com.example.ui.FinanceUiState
import com.example.ui.PendingRuleDisplay
import com.example.ui.theme.LocalFinanceColors
import com.example.ui.theme.MonoFontFamily

@Composable
fun ScheduledScreen(
  state: FinanceUiState,
  onConfirmPending: (PendingRuleDisplay, Long?) -> Unit,
  onSnoozePending: (ScheduledRuleEntity) -> Unit,
  onConfirmRuleNow: (ScheduledRuleEntity) -> Unit,
  onToggleRuleActive: (ScheduledRuleEntity) -> Unit,
  onSaveRule: (ScheduledRuleEntity) -> Unit,
  onDeleteRule: (ScheduledRuleEntity) -> Unit,
  modifier: Modifier = Modifier,
) {
  val financeColors = LocalFinanceColors.current
  var editAmountPending by remember { mutableStateOf<PendingRuleDisplay?>(null) }
  var editingRule by remember { mutableStateOf<ScheduledRuleEntity?>(null) }
  var creatingNewRule by remember { mutableStateOf(false) }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Text(
        text = "Movimientos Programados",
        style = MaterialTheme.typography.titleLarge,
      )
    }

    item {
      Text(
        text = "Por Confirmar Ahora (${state.pendingScheduledRules.size})",
        style = MaterialTheme.typography.titleSmall,
        color = financeColors.pendingAccent,
      )
    }

    if (state.pendingScheduledRules.isEmpty()) {
      item {
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(14.dp),
          color = financeColors.incomeContainer,
          border = BorderStroke(1.dp, financeColors.incomeAccent.copy(alpha = 0.4f)),
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Check,
              contentDescription = null,
              tint = financeColors.incomeAccent,
            )
            Text(
              text = "Todos tus movimientos programados hasta hoy están al día.",
              style = MaterialTheme.typography.bodyMedium,
              color = financeColors.onIncomeContainer,
            )
          }
        }
      }
    } else {
      items(state.pendingScheduledRules, key = { "pending_${it.rule.id}" }) { pending ->
        PendingActionCard(
          pending = pending,
          onConfirm = { onConfirmPending(pending, null) },
          onEditAmount = { editAmountPending = pending },
          onSnooze = { onSnoozePending(pending.rule) },
        )
      }
    }

    item {
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = "Reglas Programadas del Mes (${state.allScheduledRules.size})",
        style = MaterialTheme.typography.titleSmall,
      )
    }

    items(state.allScheduledRules, key = { "rule_${it.id}" }) { rule ->
      val accLabel = state.accountsById[rule.accountId]?.shortLabel ?: "Cuenta"
      val clsLabel = state.classificationsById[rule.classificationId]?.name ?: ""
      val isIncome = rule.typeId == TransactionTypes.INGRESO
      val scheduleText =
        if (rule.isEndOfMonth) "Fin de mes" else "Día ${rule.dayOfMonth}"

      Surface(
        onClick = { editingRule = rule },
        modifier = Modifier.fillMaxWidth().testTag("scheduled_rule_${rule.id}"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, financeColors.cardBorder),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
              text = rule.title,
              style = MaterialTheme.typography.titleSmall,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = "$scheduleText • $clsLabel • $accLabel",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Column(horizontalAlignment = Alignment.End) {
              Text(
                text =
                  FiscalPeriodCalculator.formatMxn(
                    cents = rule.amountCents,
                    includeSign = true,
                    isExpense = !isIncome,
                  ),
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = if (isIncome) financeColors.incomeAccent else financeColors.expenseAccent,
              )
              TextButton(
                onClick = { onConfirmRuleNow(rule) },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                modifier = Modifier.height(28.dp),
              ) {
                Text("Registrar hoy", style = MaterialTheme.typography.labelSmall)
              }
            }
            Switch(
              checked = rule.isActive,
              onCheckedChange = { onToggleRuleActive(rule) },
            )
          }
        }
      }
    }

    item {
      OutlinedButton(
        onClick = { creatingNewRule = true },
        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("new_scheduled_rule_btn"),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.2.dp, financeColors.incomeAccent),
      ) {
        Icon(
          imageVector = Icons.Rounded.Add,
          contentDescription = null,
          tint = financeColors.incomeAccent,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = "+ Nueva Regla Recurrente",
          color = financeColors.incomeAccent,
          fontWeight = FontWeight.Bold,
        )
      }
    }
  }

  editAmountPending?.let { pending ->
    EditAmountConfirmDialog(
      pending = pending,
      onDismiss = { editAmountPending = null },
      onConfirm = { customCents ->
        onConfirmPending(pending, customCents)
        editAmountPending = null
      },
    )
  }

  if (creatingNewRule || editingRule != null) {
    ScheduledRuleEditorSheet(
      initialRule = editingRule,
      state = state,
      onDismiss = {
        creatingNewRule = false
        editingRule = null
      },
      onSave = { rule ->
        onSaveRule(rule)
        creatingNewRule = false
        editingRule = null
      },
      onDelete =
        editingRule?.let { existing ->
          {
            onDeleteRule(existing)
            editingRule = null
          }
        },
    )
  }
}

@Composable
private fun PendingActionCard(
  pending: PendingRuleDisplay,
  onConfirm: () -> Unit,
  onEditAmount: () -> Unit,
  onSnooze: () -> Unit,
) {
  val financeColors = LocalFinanceColors.current
  val isIncome = pending.rule.typeId == TransactionTypes.INGRESO
  val bg = if (isIncome) financeColors.incomeContainer else financeColors.pendingContainer
  val border = if (isIncome) financeColors.incomeAccent else financeColors.pendingAccent
  val amountColor = if (isIncome) financeColors.incomeAccent else financeColors.expenseAccent
  val metaColor = if (isIncome) financeColors.onIncomeContainer else financeColors.onPendingContainer

  Surface(
    modifier = Modifier.fillMaxWidth().testTag("pending_card_${pending.rule.id}"),
    shape = RoundedCornerShape(16.dp),
    color = bg,
    border = BorderStroke(1.3.dp, border.copy(alpha = 0.7f)),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "${pending.rule.title} (${pending.classificationName})",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = metaColor,
          modifier = Modifier.weight(1f).padding(end = 8.dp),
        )
        Text(
          text =
            FiscalPeriodCalculator.formatMxn(
              cents = pending.rule.amountCents,
              includeSign = true,
              isExpense = !isIncome,
            ),
          fontFamily = MonoFontFamily,
          fontWeight = FontWeight.Bold,
          fontSize = 16.sp,
          color = amountColor,
        )
      }

      Text(
        text = "${pending.scheduleDescription} • Cuenta: ${pending.accountShortLabel}",
        style = MaterialTheme.typography.bodySmall,
        color = metaColor.copy(alpha = 0.85f),
      )
      Text(
        text =
          if (pending.rule.dayOfMonth >= 15 || pending.rule.isEndOfMonth) {
            "Periodo asignado: ${pending.targetFiscalPeriod.label} (corte día 15)"
          } else {
            "Periodo asignado: ${pending.targetFiscalPeriod.label}"
          },
        style = MaterialTheme.typography.labelSmall,
        color = financeColors.incomeAccent,
        fontWeight = FontWeight.SemiBold,
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Button(
          onClick = onConfirm,
          modifier = Modifier.weight(1.35f).height(44.dp).testTag("confirm_rule_${pending.rule.id}"),
          shape = RoundedCornerShape(10.dp),
          colors =
            ButtonDefaults.buttonColors(
              containerColor = financeColors.incomeAccent,
              contentColor = Color.White,
            ),
        ) {
          Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = "Confirmar / Realizado",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
          )
        }

        if (isIncome) {
          OutlinedButton(
            onClick = onSnooze,
            modifier = Modifier.weight(0.95f).height(44.dp),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, border),
          ) {
            Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Posponer 24h", style = MaterialTheme.typography.labelSmall, maxLines = 1)
          }
        } else {
          OutlinedButton(
            onClick = onEditAmount,
            modifier = Modifier.weight(0.95f).height(44.dp),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, border),
          ) {
            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Editar monto", style = MaterialTheme.typography.labelSmall, maxLines = 1)
          }
        }
      }
    }
  }
}

@Composable
private fun EditAmountConfirmDialog(
  pending: PendingRuleDisplay,
  onDismiss: () -> Unit,
  onConfirm: (Long) -> Unit,
) {
  var amountText by remember {
    mutableStateOf("%.2f".format(java.util.Locale.US, pending.rule.amountCents / 100.0))
  }
  val cents = FiscalPeriodCalculator.parseAmountToCents(amountText)

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Confirmar ${pending.rule.title}") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = "Ajusta el importe para registrar este movimiento en ${pending.accountShortLabel}.",
          style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
          value = amountText,
          onValueChange = { amountText = it },
          label = { Text("Importe (MXN)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      Button(onClick = { onConfirm(cents) }, enabled = cents > 0L) {
        Text("Confirmar")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
  )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ScheduledRuleEditorSheet(
  initialRule: ScheduledRuleEntity?,
  state: FinanceUiState,
  onDismiss: () -> Unit,
  onSave: (ScheduledRuleEntity) -> Unit,
  onDelete: (() -> Unit)?,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val scope = rememberCoroutineScope()
  val dayFieldBringIntoView = remember { BringIntoViewRequester() }

  var title by remember { mutableStateOf(initialRule?.title ?: "") }
  var typeId by remember { mutableStateOf(initialRule?.typeId ?: TransactionTypes.GASTO) }
  val availableClasses = state.allClassifications.filter { it.isActive && it.typeId == typeId }
  var classId by remember(typeId) {
    mutableStateOf(
      availableClasses.firstOrNull { it.id == initialRule?.classificationId }?.id
        ?: availableClasses.firstOrNull()?.id
        ?: 0L
    )
  }
  val availableDetails = state.allDetails.filter { it.isActive && it.classificationId == classId }
  var detailId by remember(classId) {
    mutableStateOf(
      availableDetails.firstOrNull { it.id == initialRule?.detailId }?.id
        ?: availableDetails.firstOrNull()?.id
        ?: 0L
    )
  }
  var accountId by remember {
    mutableStateOf(
      state.activeAccounts.firstOrNull { it.id == initialRule?.accountId }?.id
        ?: state.activeAccounts.firstOrNull()?.id
        ?: 0L
    )
  }
  var amountText by remember {
    mutableStateOf(
      if (initialRule != null) "%.2f".format(java.util.Locale.US, initialRule.amountCents / 100.0)
      else ""
    )
  }
  var isEndOfMonth by remember { mutableStateOf(initialRule?.isEndOfMonth ?: false) }
  var dayText by remember {
    mutableStateOf(
      if (initialRule != null && !initialRule.isEndOfMonth) initialRule.dayOfMonth.toString()
      else "15"
    )
  }

  val amountCents = FiscalPeriodCalculator.parseAmountToCents(amountText)
  val rawDayInt = dayText.toIntOrNull()
  val isDayValid = isEndOfMonth || (rawDayInt != null && rawDayInt in 1..28)
  val parsedDay = (rawDayInt ?: 1).coerceIn(1, 28)

  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .navigationBarsPadding()
          .imePadding()
          .padding(horizontal = 16.dp)
          .padding(bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = if (initialRule == null) "Nueva Regla Recurrente" else "Editar Regla Recurrente",
          style = MaterialTheme.typography.titleMedium,
        )
        if (onDelete != null) {
          IconButton(onClick = onDelete) {
            Icon(
              Icons.Rounded.DeleteOutline,
              contentDescription = "Eliminar regla",
              tint = MaterialTheme.colorScheme.error,
            )
          }
        }
      }

      OutlinedTextField(
        value = title,
        onValueChange = { title = it },
        label = { Text("Nombre (ej. Internet, Renta, Nómina)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
      )

      OutlinedTextField(
        value = amountText,
        onValueChange = { amountText = it },
        label = { Text("Monto fijo (MXN)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
      )

      // Schedule day placed near text inputs & protected with BringIntoViewRequester + imePadding
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().bringIntoViewRequester(dayFieldBringIntoView),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(checked = isEndOfMonth, onCheckedChange = { isEndOfMonth = it })
          Text("Fin de mes", style = MaterialTheme.typography.bodyMedium)
        }
        if (!isEndOfMonth) {
          OutlinedTextField(
            value = dayText,
            onValueChange = { input ->
              dayText = input.filter { ch -> ch.isDigit() }.take(2)
            },
            label = { Text("Día programación (1–28)") },
            isError = dayText.isNotEmpty() && !isDayValid,
            supportingText =
              if (dayText.isNotEmpty() && !isDayValid) {
                { Text("Usa un día del 1 al 28 o marca Fin de mes") }
              } else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier =
              Modifier.weight(1f)
                .testTag("scheduled_day_input")
                .onFocusChanged { focusState ->
                  if (focusState.isFocused) {
                    scope.launch { dayFieldBringIntoView.bringIntoView() }
                  }
                },
          )
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(TransactionTypes.GASTO to "Gasto", TransactionTypes.INGRESO to "Ingreso").forEach {
          (tId, label) ->
          val sel = typeId == tId
          Surface(
            onClick = { typeId = tId },
            shape = RoundedCornerShape(10.dp),
            color =
              if (sel) MaterialTheme.colorScheme.primaryContainer
              else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.weight(1f).height(42.dp),
          ) {
            Row(
              horizontalArrangement = Arrangement.Center,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(label, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
            }
          }
        }
      }

      Text("Clasificación", style = MaterialTheme.typography.labelSmall)
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        availableClasses.forEach { cls ->
          val sel = cls.id == classId
          Surface(
            onClick = { classId = cls.id },
            shape = RoundedCornerShape(8.dp),
            color =
              if (sel) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.surfaceVariant,
          ) {
            Text(
              text = cls.name,
              color = if (sel) Color.White else MaterialTheme.colorScheme.onSurface,
              style = MaterialTheme.typography.labelMedium,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
          }
        }
      }

      Text("Detalle", style = MaterialTheme.typography.labelSmall)
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        availableDetails.forEach { det ->
          val sel = det.id == detailId
          Surface(
            onClick = { detailId = det.id },
            shape = RoundedCornerShape(8.dp),
            color =
              if (sel) MaterialTheme.colorScheme.primaryContainer
              else MaterialTheme.colorScheme.surfaceVariant,
          ) {
            Text(
              text = det.name,
              style = MaterialTheme.typography.labelMedium,
              fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
          }
        }
      }

      Text("Cuenta", style = MaterialTheme.typography.labelSmall)
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        state.activeAccounts.forEach { acc ->
          val sel = acc.id == accountId
          Surface(
            onClick = { accountId = acc.id },
            shape = RoundedCornerShape(8.dp),
            color =
              if (sel) MaterialTheme.colorScheme.primaryContainer
              else MaterialTheme.colorScheme.surfaceVariant,
          ) {
            Text(
              text = acc.shortLabel,
              style = MaterialTheme.typography.labelMedium,
              fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
          }
        }
      }

      Button(
        onClick = {
          onSave(
            ScheduledRuleEntity(
              id = initialRule?.id ?: 0L,
              title = title.trim(),
              typeId = typeId,
              classificationId = classId,
              detailId = detailId,
              accountId = accountId,
              merchantName = initialRule?.merchantName,
              amountCents = amountCents,
              dayOfMonth = if (isEndOfMonth) 31 else parsedDay,
              isEndOfMonth = isEndOfMonth,
              isActive = initialRule?.isActive ?: true,
              lastConfirmedPeriodKey = initialRule?.lastConfirmedPeriodKey,
              snoozedUntilEpochDay = initialRule?.snoozedUntilEpochDay,
            )
          )
        },
        enabled =
          title.isNotBlank() &&
            amountCents > 0L &&
            isDayValid &&
            classId > 0L &&
            detailId > 0L &&
            accountId > 0L,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(12.dp),
      ) {
        Text("Guardar Regla")
      }
    }
  }
}
