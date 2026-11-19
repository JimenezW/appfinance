package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AccountEntity
import com.example.data.local.ClassificationEntity
import com.example.data.local.DetailItemEntity
import com.example.data.local.MerchantEntity
import com.example.data.local.TransactionTypes
import com.example.domain.FiscalPeriodCalculator
import com.example.ui.FinanceUiState
import com.example.ui.PendingRuleDisplay
import com.example.ui.components.NumericKeypad
import com.example.ui.components.OperationDatePickerDialog
import com.example.ui.components.PendingRuleTopBanner
import com.example.ui.components.SectionHeader
import com.example.ui.theme.LocalFinanceColors
import com.example.ui.theme.MonoFontFamily

@Composable
fun CaptureScreen(
  state: FinanceUiState,
  onSelectType: (String) -> Unit,
  onSelectClassification: (Long) -> Unit,
  onSelectDetail: (Long) -> Unit,
  onSelectAccount: (Long) -> Unit,
  onSelectDate: (Long) -> Unit,
  onSelectMerchant: (String) -> Unit,
  onToggleNoteField: (Boolean) -> Unit,
  onNoteChange: (String) -> Unit,
  onDigit: (Char) -> Unit,
  onDecimal: () -> Unit,
  onBackspace: () -> Unit,
  onClearAmount: () -> Unit,
  onApplyIncomeShortcut: (String, String, String, String?) -> Unit,
  onSaveTransaction: () -> Unit,
  onConfirmPendingRule: (PendingRuleDisplay) -> Unit,
  onOpenScheduledTab: () -> Unit,
  onOpenAccountsAdmin: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var showDatePicker by remember { mutableStateOf(false) }
  var showMerchantSheet by remember { mutableStateOf(false) }
  var keypadExpanded by remember { mutableStateOf(false) }

  BackHandler(enabled = keypadExpanded) { keypadExpanded = false }

  val firstPending = state.pendingScheduledRules.firstOrNull()

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val isWideScreen = maxWidth >= 700.dp

    if (isWideScreen) {
      Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Column(
          modifier = Modifier.weight(1.25f).fillMaxHeight().verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          if (firstPending != null) {
            PendingRuleTopBanner(
              pending = firstPending,
              onConfirm = { onConfirmPendingRule(firstPending) },
              onBannerClick = onOpenScheduledTab,
            )
          }
          TypeSegmentedControl(
            selectedType = state.capture.typeId,
            onSelectType = onSelectType,
          )
          DateAndFiscalPeriodRow(
            epochDay = state.capture.operationEpochDay,
            fiscalLabel = state.capture.fiscalPeriod.label,
            isCutoff = state.capture.isCutoffApplied,
            onOpenDatePicker = { showDatePicker = true },
          )
          CascadingClassificationsAndDetails(
            state = state,
            onSelectClassification = onSelectClassification,
            onSelectDetail = onSelectDetail,
            onApplyIncomeShortcut = onApplyIncomeShortcut,
          )
          AccountSelectorStrip(
            accounts = state.activeAccounts,
            selectedAccountId = state.effectiveAccountId,
            isIncome = state.capture.typeId == TransactionTypes.INGRESO,
            onSelectAccount = onSelectAccount,
            onOpenAccountsAdmin = onOpenAccountsAdmin,
          )
          MerchantAndNoteBar(
            isIncome = state.capture.typeId == TransactionTypes.INGRESO,
            merchantName = state.capture.merchantName,
            showNoteField = state.capture.showNoteField,
            note = state.capture.note,
            onOpenMerchantSheet = { showMerchantSheet = true },
            onToggleNoteField = { show ->
              if (show) keypadExpanded = false
              onToggleNoteField(show)
            },
            onNoteChange = onNoteChange,
          )
          Spacer(modifier = Modifier.height(12.dp))
        }

        Column(
          modifier = Modifier.weight(0.95f).fillMaxHeight(),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          AmountDisplayCard(
            typeId = state.capture.typeId,
            rawAmountInput = state.capture.amountInput,
            amountCents = state.capture.amountCents,
            isKeypadActive = keypadExpanded,
            onCardClick = { keypadExpanded = true },
            onClear = onClearAmount,
          )
          SaveTransactionButton(
            typeId = state.capture.typeId,
            amountCents = state.capture.amountCents,
            enabled = state.canSaveCapture,
            onClick = {
              keypadExpanded = false
              onSaveTransaction()
            },
          )
          AnimatedVisibility(visible = keypadExpanded) {
            NumericKeypad(
              onDigit = onDigit,
              onDecimal = onDecimal,
              onBackspace = onBackspace,
            )
          }
        }
      }
    } else {
      Column(modifier = Modifier.fillMaxSize()) {
        Column(
          modifier =
            Modifier.weight(1f)
              .fillMaxWidth()
              .verticalScroll(rememberScrollState())
              .padding(horizontal = 14.dp, vertical = 8.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          if (firstPending != null) {
            PendingRuleTopBanner(
              pending = firstPending,
              onConfirm = { onConfirmPendingRule(firstPending) },
              onBannerClick = onOpenScheduledTab,
            )
          }

          TypeSegmentedControl(
            selectedType = state.capture.typeId,
            onSelectType = onSelectType,
          )

          AmountDisplayCard(
            typeId = state.capture.typeId,
            rawAmountInput = state.capture.amountInput,
            amountCents = state.capture.amountCents,
            isKeypadActive = keypadExpanded,
            onCardClick = { keypadExpanded = true },
            onClear = onClearAmount,
          )

          DateAndFiscalPeriodRow(
            epochDay = state.capture.operationEpochDay,
            fiscalLabel = state.capture.fiscalPeriod.label,
            isCutoff = state.capture.isCutoffApplied,
            onOpenDatePicker = { showDatePicker = true },
          )

          CascadingClassificationsAndDetails(
            state = state,
            onSelectClassification = onSelectClassification,
            onSelectDetail = onSelectDetail,
            onApplyIncomeShortcut = onApplyIncomeShortcut,
          )

          AccountSelectorStrip(
            accounts = state.activeAccounts,
            selectedAccountId = state.effectiveAccountId,
            isIncome = state.capture.typeId == TransactionTypes.INGRESO,
            onSelectAccount = onSelectAccount,
            onOpenAccountsAdmin = onOpenAccountsAdmin,
          )

          MerchantAndNoteBar(
            isIncome = state.capture.typeId == TransactionTypes.INGRESO,
            merchantName = state.capture.merchantName,
            showNoteField = state.capture.showNoteField,
            note = state.capture.note,
            onOpenMerchantSheet = { showMerchantSheet = true },
            onToggleNoteField = { show ->
              if (show) keypadExpanded = false
              onToggleNoteField(show)
            },
            onNoteChange = onNoteChange,
          )

          Spacer(modifier = Modifier.height(4.dp))
        }

        // Fixed bottom thumb dock: Save CTA + Collapsible Integrated Keypad
        Surface(
          color = MaterialTheme.colorScheme.background,
          tonalElevation = 1.dp,
          shadowElevation = 6.dp,
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            SaveTransactionButton(
              typeId = state.capture.typeId,
              amountCents = state.capture.amountCents,
              enabled = state.canSaveCapture,
              onClick = {
                keypadExpanded = false
                onSaveTransaction()
              },
            )

            if (keypadExpanded) {
              Row(
                modifier =
                  Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { keypadExpanded = false }
                    .padding(vertical = 2.dp, horizontal = 4.dp)
                    .testTag("collapse_keypad_btn"),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = "Ocultar teclado numérico",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                  imageVector = Icons.Rounded.KeyboardArrowDown,
                  contentDescription = "Ocultar teclado numérico",
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(16.dp),
                )
              }
            }

            AnimatedVisibility(visible = keypadExpanded) {
              NumericKeypad(
                onDigit = onDigit,
                onDecimal = onDecimal,
                onBackspace = onBackspace,
              )
            }
          }
        }
      }
    }
  }

  if (showDatePicker) {
    OperationDatePickerDialog(
      initialEpochDay = state.capture.operationEpochDay,
      onDismiss = { showDatePicker = false },
      onDateSelected = onSelectDate,
    )
  }

  if (showMerchantSheet) {
    MerchantPickerBottomSheet(
      isIncome = state.capture.typeId == TransactionTypes.INGRESO,
      merchants = state.activeMerchants,
      currentValue = state.capture.merchantName,
      onDismiss = { showMerchantSheet = false },
      onSelectMerchant = {
        onSelectMerchant(it)
        showMerchantSheet = false
      },
    )
  }
}

@Composable
private fun TypeSegmentedControl(
  selectedType: String,
  onSelectType: (String) -> Unit,
) {
  val financeColors = LocalFinanceColors.current
  Surface(
    shape = RoundedCornerShape(14.dp),
    color = financeColors.subtleSurface,
    border = BorderStroke(1.dp, financeColors.cardBorder),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(modifier = Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
      TypeSegmentOption(
        label = "Gasto",
        selected = selectedType == TransactionTypes.GASTO,
        accentColor = financeColors.expenseAccent,
        onClick = { onSelectType(TransactionTypes.GASTO) },
        modifier = Modifier.weight(1f).testTag("type_toggle_gasto"),
      )
      TypeSegmentOption(
        label = "Ingreso",
        selected = selectedType == TransactionTypes.INGRESO,
        accentColor = financeColors.incomeAccent,
        onClick = { onSelectType(TransactionTypes.INGRESO) },
        modifier = Modifier.weight(1f).testTag("type_toggle_ingreso"),
      )
    }
  }
}

@Composable
private fun TypeSegmentOption(
  label: String,
  selected: Boolean,
  accentColor: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val bg by
    animateColorAsState(
      targetValue = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
      label = "segment_bg",
    )
  Surface(
    onClick = onClick,
    modifier = modifier.height(48.dp),
    shape = RoundedCornerShape(11.dp),
    color = bg,
    border = if (selected) BorderStroke(1.2.dp, accentColor.copy(alpha = 0.65f)) else null,
    shadowElevation = if (selected) 1.dp else 0.dp,
  ) {
    Row(
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (selected) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(accentColor))
        Spacer(modifier = Modifier.width(7.dp))
      }
      Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        color = if (selected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun AmountDisplayCard(
  typeId: String,
  rawAmountInput: String,
  amountCents: Long,
  isKeypadActive: Boolean,
  onCardClick: () -> Unit,
  onClear: () -> Unit,
) {
  val financeColors = LocalFinanceColors.current
  val isExpense = typeId == TransactionTypes.GASTO
  val accentColor by
    animateColorAsState(
      targetValue = if (isExpense) financeColors.expenseAccent else financeColors.incomeAccent,
      label = "amount_accent",
    )
  val bgColor by
    animateColorAsState(
      targetValue = if (isExpense) financeColors.expenseContainer else financeColors.incomeContainer,
      label = "amount_bg",
    )

  val formattedDisplay =
    if (rawAmountInput.isEmpty()) {
      if (isExpense) "- $ 0.00" else "+ $ 0.00"
    } else {
      val sign = if (isExpense) "- " else "+ "
      val numberText =
        if (rawAmountInput.endsWith(".")) "$rawAmountInput"
        else FiscalPeriodCalculator.formatMxn(amountCents)
      if (numberText.startsWith("$")) "$sign$ ${numberText.drop(1)}" else "$sign$ $numberText"
    }

  Surface(
    onClick = onCardClick,
    modifier = Modifier.fillMaxWidth().testTag("amount_display_card"),
    shape = RoundedCornerShape(16.dp),
    color = bgColor,
    border =
      BorderStroke(
        width = if (isKeypadActive) 2.2.dp else 1.2.dp,
        color = if (isKeypadActive) accentColor else accentColor.copy(alpha = 0.45f),
      ),
  ) {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
          text =
            when {
              isExpense && isKeypadActive -> "IMPORTE DEL GASTO (TECLADO ACTIVO)"
              isExpense -> "IMPORTE DEL GASTO (TOCA PARA INGRESAR)"
              isKeypadActive -> "IMPORTE DEL INGRESO (TECLADO ACTIVO)"
              else -> "IMPORTE DEL INGRESO (TOCA PARA INGRESAR)"
            },
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
          color = accentColor,
          textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = formattedDisplay,
          fontFamily = MonoFontFamily,
          fontWeight = FontWeight.Bold,
          fontSize = 28.sp,
          color =
            if (amountCents > 0L) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      if (rawAmountInput.isNotEmpty()) {
        IconButton(
          onClick = onClear,
          modifier = Modifier.align(Alignment.CenterEnd).size(40.dp).testTag("clear_amount_btn"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = "Limpiar importe",
            tint = accentColor,
            modifier = Modifier.size(18.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun DateAndFiscalPeriodRow(
  epochDay: Long,
  fiscalLabel: String,
  isCutoff: Boolean,
  onOpenDatePicker: () -> Unit,
) {
  val financeColors = LocalFinanceColors.current
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Surface(
      onClick = onOpenDatePicker,
      modifier = Modifier.weight(1f).height(48.dp).testTag("date_picker_button"),
      shape = RoundedCornerShape(12.dp),
      color = MaterialTheme.colorScheme.surface,
      border = BorderStroke(1.dp, financeColors.cardBorder),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
      ) {
        Icon(
          imageVector = Icons.Rounded.CalendarToday,
          contentDescription = "Cambiar fecha de operación",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = FiscalPeriodCalculator.formatOperationDateChip(epochDay),
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }

    Surface(
      modifier = Modifier.weight(1f).height(48.dp).testTag("fiscal_period_badge"),
      shape = RoundedCornerShape(12.dp),
      color = financeColors.periodContainer,
      border = BorderStroke(1.dp, financeColors.periodAccent.copy(alpha = 0.4f)),
    ) {
      Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
        Text(
          text = if (isCutoff) "Periodo: $fiscalLabel (corte 15)" else "Periodo: $fiscalLabel",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
          color = financeColors.periodAccent,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CascadingClassificationsAndDetails(
  state: FinanceUiState,
  onSelectClassification: (Long) -> Unit,
  onSelectDetail: (Long) -> Unit,
  onApplyIncomeShortcut: (String, String, String, String?) -> Unit,
) {
  val financeColors = LocalFinanceColors.current
  val isExpense = state.capture.typeId == TransactionTypes.GASTO
  val accentColor = if (isExpense) financeColors.expenseAccent else financeColors.incomeAccent
  val containerColor = if (isExpense) financeColors.expenseContainer else financeColors.incomeContainer

  val classifications = state.activeClassificationsForCapture
  val selectedClassId = state.effectiveClassificationId
  val selectedClassName =
    classifications.firstOrNull { it.id == selectedClassId }?.name ?: "Clasificación"
  val details = state.activeDetailsForCapture
  val selectedDetailId = state.effectiveDetailId

  // 1. Classifications Grid (all directly visible in thumb zone)
  SectionHeader(
    title =
      if (isExpense) "Clasificación — Gasto (${classifications.size} disponibles)"
      else "Clasificación — Ingreso (${classifications.size} disponibles)"
  )
  FlowRow(
    modifier = Modifier.fillMaxWidth().testTag("classification_chip_grid"),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    classifications.forEach { item ->
      val selected = item.id == selectedClassId
      CascadeChip(
        label = item.name,
        selected = selected,
        selectedBackground = accentColor,
        selectedTextColor = Color.White,
        selectedBorder = accentColor,
        onClick = { onSelectClassification(item.id) },
        modifier = Modifier.testTag("class_chip_${item.id}"),
      )
    }
  }

  // 2. Dependent Details Grid
  SectionHeader(
    title = "Detalle dependiente de '$selectedClassName' (${details.size} visibles)"
  )
  FlowRow(
    modifier = Modifier.fillMaxWidth().testTag("detail_chip_grid"),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    details.forEach { det ->
      val selected = det.id == selectedDetailId
      CascadeChip(
        label = det.name,
        selected = selected,
        selectedBackground = containerColor,
        selectedTextColor = accentColor,
        selectedBorder = accentColor,
        onClick = { onSelectDetail(det.id) },
        modifier = Modifier.testTag("detail_chip_${det.id}"),
      )
    }
  }

  // 3. Income quick-fill amount shortcuts (matching mockup 1 right side)
  AnimatedVisibility(visible = !isExpense) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      SectionHeader(title = "Atajos de importe frecuente (1 toque)")
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        IncomeShortcutChip(
          label = "$6,600 Anticipo",
          onClick = { onApplyIncomeShortcut("6600", "Nómina", "Anticipo", "DEBIT") },
        )
        IncomeShortcutChip(
          label = "$16,200 Nómina",
          onClick = { onApplyIncomeShortcut("16200", "Nómina", "Nómina", "DEBIT") },
        )
        IncomeShortcutChip(
          label = "$689.40 Vales",
          onClick = { onApplyIncomeShortcut("689.40", "Otro", "Vales de despensa", "VALES") },
        )
      }
    }
  }
}

@Composable
private fun CascadeChip(
  label: String,
  selected: Boolean,
  selectedBackground: Color,
  selectedTextColor: Color,
  selectedBorder: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val financeColors = LocalFinanceColors.current
  Surface(
    onClick = onClick,
    modifier = modifier.height(38.dp),
    shape = RoundedCornerShape(10.dp),
    color = if (selected) selectedBackground else MaterialTheme.colorScheme.surface,
    border =
      BorderStroke(
        width = if (selected) 1.4.dp else 1.dp,
        color = if (selected) selectedBorder else financeColors.cardBorder,
      ),
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier.padding(horizontal = 12.dp),
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        color = if (selected) selectedTextColor else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun IncomeShortcutChip(
  label: String,
  onClick: () -> Unit,
) {
  val financeColors = LocalFinanceColors.current
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(10.dp),
    color = financeColors.incomeContainer,
    border = BorderStroke(1.dp, financeColors.incomeAccent.copy(alpha = 0.45f)),
    modifier = Modifier.height(38.dp),
  ) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = financeColors.incomeAccent,
      )
    }
  }
}

@Composable
private fun AccountSelectorStrip(
  accounts: List<AccountEntity>,
  selectedAccountId: Long?,
  isIncome: Boolean,
  onSelectAccount: (Long) -> Unit,
  onOpenAccountsAdmin: () -> Unit,
) {
  val financeColors = LocalFinanceColors.current
  val accentColor = if (isIncome) financeColors.incomeAccent else financeColors.expenseAccent
  val containerColor =
    if (isIncome) financeColors.incomeContainer else financeColors.expenseContainer

  SectionHeader(
    title = if (isIncome) "Cuenta Destino (Acredita Saldo)" else "Método de Pago / Cuenta Origen"
  )

  Row(
    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    accounts.forEach { acc ->
      val selected = acc.id == selectedAccountId
      Surface(
        onClick = { onSelectAccount(acc.id) },
        modifier = Modifier.testTag("account_chip_${acc.id}"),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) containerColor else MaterialTheme.colorScheme.surface,
        border =
          BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) accentColor else financeColors.cardBorder,
          ),
      ) {
        Column(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text(
            text = acc.shortLabel,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (selected) accentColor else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
          )
          Text(
            text = FiscalPeriodCalculator.formatMxn(acc.balanceCents),
            fontFamily = MonoFontFamily,
            fontSize = 11.sp,
            color =
              if (selected) accentColor.copy(alpha = 0.85f)
              else MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    Surface(
      onClick = onOpenAccountsAdmin,
      shape = RoundedCornerShape(12.dp),
      color = financeColors.subtleSurface,
      border = BorderStroke(1.dp, financeColors.cardBorder),
    ) {
      Column(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
          text = "Otra",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = "+ CRUD",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun MerchantAndNoteBar(
  isIncome: Boolean,
  merchantName: String,
  showNoteField: Boolean,
  note: String,
  onOpenMerchantSheet: () -> Unit,
  onToggleNoteField: (Boolean) -> Unit,
  onNoteChange: (String) -> Unit,
) {
  val financeColors = LocalFinanceColors.current
  val labelPrefix = if (isIncome) "Origen / Pagador:" else "Comercio:"
  val merchantDisplay = merchantName.ifBlank { "Opcional (seleccionar)" }

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Surface(
      shape = RoundedCornerShape(12.dp),
      color = MaterialTheme.colorScheme.surface,
      border = BorderStroke(1.dp, financeColors.cardBorder),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          modifier =
            Modifier.weight(1f)
              .clip(RoundedCornerShape(8.dp))
              .clickable { onOpenMerchantSheet() }
              .padding(vertical = 10.dp, horizontal = 4.dp)
              .testTag("merchant_selector_btn"),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            imageVector = Icons.Rounded.Storefront,
            contentDescription = "Seleccionar comercio",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "$labelPrefix $merchantDisplay",
            style = MaterialTheme.typography.bodyMedium,
            color =
              if (merchantName.isNotBlank()) MaterialTheme.colorScheme.onSurface
              else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }

        Box(
          modifier =
            Modifier.height(24.dp).width(1.dp).background(financeColors.cardBorder)
        )

        TextButton(
          onClick = { onToggleNoteField(!showNoteField) },
          modifier = Modifier.testTag("toggle_note_btn"),
        ) {
          Icon(
            imageVector = Icons.Rounded.EditNote,
            contentDescription = "Agregar nota",
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = if (note.isNotBlank()) "Nota ✓" else "+ Nota opcional",
            style = MaterialTheme.typography.labelMedium,
          )
        }
      }
    }

    AnimatedVisibility(visible = showNoteField) {
      OutlinedTextField(
        value = note,
        onValueChange = onNoteChange,
        label = { Text("Nota o descripción libre") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("note_input_field"),
        shape = RoundedCornerShape(12.dp),
      )
    }
  }
}

@Composable
private fun SaveTransactionButton(
  typeId: String,
  amountCents: Long,
  enabled: Boolean,
  onClick: () -> Unit,
) {
  val financeColors = LocalFinanceColors.current
  val isExpense = typeId == TransactionTypes.GASTO
  val containerColor by
    animateColorAsState(
      targetValue = if (isExpense) financeColors.expenseAccent else financeColors.incomeAccent,
      label = "save_btn_color",
    )
  val formatted = FiscalPeriodCalculator.formatMxn(amountCents)
  val buttonLabel =
    if (amountCents <= 0L) {
      if (isExpense) "INGRESA IMPORTE PARA GUARDAR GASTO" else "INGRESA IMPORTE PARA GUARDAR INGRESO"
    } else {
      if (isExpense) "GUARDAR GASTO (-$formatted)" else "GUARDAR INGRESO (+$formatted)"
    }

  Button(
    onClick = onClick,
    enabled = enabled,
    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("save_transaction_button"),
    shape = RoundedCornerShape(14.dp),
    colors =
      ButtonDefaults.buttonColors(
        containerColor = containerColor,
        contentColor = Color.White,
        disabledContainerColor = containerColor.copy(alpha = 0.28f),
        disabledContentColor = Color.White.copy(alpha = 0.7f),
      ),
  ) {
    Text(
      text = buttonLabel,
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.Bold,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MerchantPickerBottomSheet(
  isIncome: Boolean,
  merchants: List<MerchantEntity>,
  currentValue: String,
  onDismiss: () -> Unit,
  onSelectMerchant: (String) -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var query by remember { mutableStateOf(currentValue) }
  val filtered =
    remember(merchants, query) {
      val q = query.trim().lowercase()
      if (q.isEmpty()) merchants else merchants.filter { it.name.lowercase().contains(q) }
    }

  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .navigationBarsPadding()
          .imePadding()
          .padding(horizontal = 16.dp)
          .padding(bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = if (isIncome) "Seleccionar Origen / Pagador" else "Seleccionar Comercio o Proveedor",
        style = MaterialTheme.typography.titleMedium,
      )
      OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        label = { Text("Buscar o escribir nuevo nombre") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
          if (query.isNotEmpty()) {
            IconButton(onClick = { query = "" }) {
              Icon(Icons.Rounded.Close, contentDescription = "Limpiar")
            }
          }
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("merchant_search_input"),
        shape = RoundedCornerShape(12.dp),
      )

      if (query.trim().isNotEmpty() &&
          merchants.none { it.name.equals(query.trim(), ignoreCase = true) }
      ) {
        Button(
          onClick = { onSelectMerchant(query.trim()) },
          modifier = Modifier.fillMaxWidth().height(48.dp),
          shape = RoundedCornerShape(12.dp),
        ) {
          Text("Usar y guardar \"${query.trim()}\"")
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Frecuentes (${filtered.size})",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { onSelectMerchant("") }) { Text("Dejar sin comercio") }
      }

      LazyColumn(
        modifier = Modifier.fillMaxWidth().height(260.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        items(filtered, key = { it.id }) { m ->
          val selected = m.name.equals(currentValue, ignoreCase = true)
          Surface(
            onClick = { onSelectMerchant(m.name) },
            shape = RoundedCornerShape(10.dp),
            color =
              if (selected) MaterialTheme.colorScheme.primaryContainer
              else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = m.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
              )
              if (selected) {
                Icon(
                  imageVector = Icons.Rounded.Check,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                )
              }
            }
          }
        }
      }
    }
  }
}
