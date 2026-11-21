package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.TransactionTypes
import com.example.domain.FiscalPeriodCalculator
import com.example.ui.MainTab
import com.example.ui.PendingRuleDisplay
import com.example.ui.theme.LocalFinanceColors
import com.example.ui.theme.MonoFontFamily

@Composable
fun FinanceBottomNavigationBar(
  currentTab: MainTab,
  pendingCount: Int,
  onSelectTab: (MainTab) -> Unit,
) {
  val financeColors = LocalFinanceColors.current
  NavigationBar(
    containerColor = MaterialTheme.colorScheme.surface,
    tonalElevation = 2.dp,
    windowInsets = WindowInsets.navigationBars,
  ) {
    NavigationBarItem(
      selected = currentTab == MainTab.CAPTURAR,
      onClick = { onSelectTab(MainTab.CAPTURAR) },
      modifier = Modifier.testTag("nav_capturar"),
      icon = {
        Icon(
          imageVector =
            if (currentTab == MainTab.CAPTURAR) Icons.Rounded.AddCircle
            else Icons.Outlined.AddCircleOutline,
          contentDescription = stringResource(R.string.nav_capturar),
        )
      },
      label = {
        Text(
          text = stringResource(R.string.nav_capturar),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      },
      colors =
        NavigationBarItemDefaults.colors(
          selectedIconColor = financeColors.incomeAccent,
          selectedTextColor = financeColors.incomeAccent,
          indicatorColor = financeColors.incomeContainer,
        ),
    )
    NavigationBarItem(
      selected = currentTab == MainTab.SALDOS,
      onClick = { onSelectTab(MainTab.SALDOS) },
      modifier = Modifier.testTag("nav_saldos"),
      icon = {
        Icon(
          imageVector =
            if (currentTab == MainTab.SALDOS) Icons.Rounded.AccountBalanceWallet
            else Icons.Outlined.AccountBalanceWallet,
          contentDescription = stringResource(R.string.nav_saldos),
        )
      },
      label = {
        Text(
          text = stringResource(R.string.nav_saldos),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      },
      colors =
        NavigationBarItemDefaults.colors(
          selectedIconColor = financeColors.incomeAccent,
          selectedTextColor = financeColors.incomeAccent,
          indicatorColor = financeColors.incomeContainer,
        ),
    )
    NavigationBarItem(
      selected = currentTab == MainTab.PROGRAMADOS,
      onClick = { onSelectTab(MainTab.PROGRAMADOS) },
      modifier = Modifier.testTag("nav_programados"),
      icon = {
        BadgedBox(
          badge = {
            if (pendingCount > 0) {
              Badge(containerColor = financeColors.pendingAccent, contentColor = Color.White) {
                Text(pendingCount.toString())
              }
            }
          }
        ) {
          Icon(
            imageVector =
              if (currentTab == MainTab.PROGRAMADOS) Icons.Rounded.EventRepeat
              else Icons.Outlined.EventRepeat,
            contentDescription = stringResource(R.string.nav_programados),
          )
        }
      },
      label = {
        Text(
          text = stringResource(R.string.nav_programados),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      },
      colors =
        NavigationBarItemDefaults.colors(
          selectedIconColor = financeColors.incomeAccent,
          selectedTextColor = financeColors.incomeAccent,
          indicatorColor = financeColors.incomeContainer,
        ),
    )
    NavigationBarItem(
      selected = currentTab == MainTab.ADMIN,
      onClick = { onSelectTab(MainTab.ADMIN) },
      modifier = Modifier.testTag("nav_admin"),
      icon = {
        Icon(
          imageVector =
            if (currentTab == MainTab.ADMIN) Icons.Rounded.Tune else Icons.Outlined.Tune,
          contentDescription = stringResource(R.string.nav_admin),
        )
      },
      label = {
        Text(
          text = stringResource(R.string.nav_admin),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      },
      colors =
        NavigationBarItemDefaults.colors(
          selectedIconColor = financeColors.incomeAccent,
          selectedTextColor = financeColors.incomeAccent,
          indicatorColor = financeColors.incomeContainer,
        ),
    )
  }
}

@Composable
fun FinanceNavigationRail(
  currentTab: MainTab,
  pendingCount: Int,
  onSelectTab: (MainTab) -> Unit,
  modifier: Modifier = Modifier,
) {
  val financeColors = LocalFinanceColors.current
  NavigationRail(
    containerColor = MaterialTheme.colorScheme.surface,
    modifier = modifier.fillMaxHeight().testTag("finance_navigation_rail"),
  ) {
    Spacer(modifier = Modifier.height(12.dp))
    val itemColors =
      NavigationRailItemDefaults.colors(
        selectedIconColor = financeColors.incomeAccent,
        selectedTextColor = financeColors.incomeAccent,
        indicatorColor = financeColors.incomeContainer,
      )
    NavigationRailItem(
      selected = currentTab == MainTab.CAPTURAR,
      onClick = { onSelectTab(MainTab.CAPTURAR) },
      modifier = Modifier.testTag("rail_capturar"),
      icon = {
        Icon(
          imageVector =
            if (currentTab == MainTab.CAPTURAR) Icons.Rounded.AddCircle
            else Icons.Outlined.AddCircleOutline,
          contentDescription = stringResource(R.string.nav_capturar),
        )
      },
      label = { Text(stringResource(R.string.nav_capturar), maxLines = 1) },
      colors = itemColors,
    )
    NavigationRailItem(
      selected = currentTab == MainTab.SALDOS,
      onClick = { onSelectTab(MainTab.SALDOS) },
      modifier = Modifier.testTag("rail_saldos"),
      icon = {
        Icon(
          imageVector =
            if (currentTab == MainTab.SALDOS) Icons.Rounded.AccountBalanceWallet
            else Icons.Outlined.AccountBalanceWallet,
          contentDescription = stringResource(R.string.nav_saldos),
        )
      },
      label = { Text(stringResource(R.string.nav_saldos), maxLines = 1) },
      colors = itemColors,
    )
    NavigationRailItem(
      selected = currentTab == MainTab.PROGRAMADOS,
      onClick = { onSelectTab(MainTab.PROGRAMADOS) },
      modifier = Modifier.testTag("rail_programados"),
      icon = {
        BadgedBox(
          badge = {
            if (pendingCount > 0) {
              Badge(containerColor = financeColors.pendingAccent, contentColor = Color.White) {
                Text(pendingCount.toString())
              }
            }
          }
        ) {
          Icon(
            imageVector =
              if (currentTab == MainTab.PROGRAMADOS) Icons.Rounded.EventRepeat
              else Icons.Outlined.EventRepeat,
            contentDescription = stringResource(R.string.nav_programados),
          )
        }
      },
      label = { Text(stringResource(R.string.nav_programados), maxLines = 1) },
      colors = itemColors,
    )
    NavigationRailItem(
      selected = currentTab == MainTab.ADMIN,
      onClick = { onSelectTab(MainTab.ADMIN) },
      modifier = Modifier.testTag("rail_admin"),
      icon = {
        Icon(
          imageVector =
            if (currentTab == MainTab.ADMIN) Icons.Rounded.Tune else Icons.Outlined.Tune,
          contentDescription = stringResource(R.string.nav_admin),
        )
      },
      label = { Text(stringResource(R.string.nav_admin), maxLines = 1) },
      colors = itemColors,
    )
  }
}

@Composable
fun PendingRuleTopBanner(
  pending: PendingRuleDisplay,
  onConfirm: () -> Unit,
  onBannerClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val financeColors = LocalFinanceColors.current
  val isIncome = TransactionTypes.isIncome(pending.rule.typeId)
  val bgColor by
    animateColorAsState(
      targetValue = if (isIncome) financeColors.incomeContainer else financeColors.pendingContainer,
      label = "banner_bg",
    )
  val borderColor by
    animateColorAsState(
      targetValue = if (isIncome) financeColors.incomeAccent else financeColors.pendingAccent,
      label = "banner_border",
    )
  val titleColor = if (isIncome) financeColors.onIncomeContainer else financeColors.onPendingContainer
  val buttonColor = if (isIncome) financeColors.incomeAccent else financeColors.pendingAccent

  Surface(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .clickable { onBannerClick() }
        .testTag("pending_banner_card"),
    shape = RoundedCornerShape(12.dp),
    color = bgColor,
    border = BorderStroke(1.dp, borderColor.copy(alpha = 0.65f)),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
        Text(
          text =
            "Pendiente: ${pending.rule.title} (${FiscalPeriodCalculator.formatMxn(pending.rule.amountCents)})",
          style = MaterialTheme.typography.titleSmall,
          color = titleColor,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "${pending.accountShortLabel} • ${pending.scheduleDescription}",
          style = MaterialTheme.typography.bodySmall,
          color = titleColor.copy(alpha = 0.8f),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Button(
        onClick = onConfirm,
        modifier = Modifier.testTag("pending_banner_confirm_button"),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
      ) {
        Text(
          text = "Realizado",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
        )
      }
    }
  }
}

@Composable
fun NumericKeypad(
  onDigit: (Char) -> Unit,
  onDecimal: () -> Unit,
  onBackspace: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val financeColors = LocalFinanceColors.current
  val haptics = LocalHapticFeedback.current

  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .background(financeColors.subtleSurface, RoundedCornerShape(14.dp))
        .border(1.dp, financeColors.cardBorder, RoundedCornerShape(14.dp))
        .padding(8.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    val rows =
      listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9'),
      )
    rows.forEach { row ->
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        row.forEach { digit ->
          KeypadButton(
            label = digit.toString(),
            onClick = {
              haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              onDigit(digit)
            },
            modifier = Modifier.weight(1f).testTag("keypad_btn_$digit"),
          )
        }
      }
    }
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      KeypadButton(
        label = ".",
        onClick = {
          haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
          onDecimal()
        },
        modifier = Modifier.weight(1f).testTag("keypad_btn_dot"),
      )
      KeypadButton(
        label = "0",
        onClick = {
          haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
          onDigit('0')
        },
        modifier = Modifier.weight(1f).testTag("keypad_btn_0"),
      )
      Surface(
        onClick = {
          haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
          onBackspace()
        },
        modifier = Modifier.weight(1f).height(48.dp).testTag("keypad_btn_backspace"),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, financeColors.cardBorder),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.Backspace,
            contentDescription = "Borrar dígito",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun KeypadButton(
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val financeColors = LocalFinanceColors.current
  Surface(
    onClick = onClick,
    modifier = modifier.height(48.dp),
    shape = RoundedCornerShape(10.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, financeColors.cardBorder),
  ) {
    Box(contentAlignment = Alignment.Center) {
      Text(
        text = label,
        fontFamily = MonoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperationDatePickerDialog(
  initialEpochDay: Long,
  onDismiss: () -> Unit,
  onDateSelected: (Long) -> Unit,
) {
  val state =
    rememberDatePickerState(
      initialSelectedDateMillis = FiscalPeriodCalculator.epochDayToUtcMillis(initialEpochDay)
    )
  DatePickerDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      TextButton(
        onClick = {
          state.selectedDateMillis?.let { millis ->
            onDateSelected(FiscalPeriodCalculator.utcMillisToEpochDay(millis))
          }
          onDismiss()
        }
      ) {
        Text("Aceptar")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
  ) {
    DatePicker(state = state)
  }
}

@Composable
fun SectionHeader(
  title: String,
  trailingText: String? = null,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.onSurface,
    )
    if (trailingText != null) {
      Text(
        text = trailingText,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

data class PickerOption<T>(
  val id: T,
  val title: String,
  val subtitle: String? = null,
  val isPinned: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SearchableOptionBottomSheet(
  title: String,
  options: List<PickerOption<T>>,
  selectedId: T?,
  accentColor: Color,
  containerColor: Color,
  searchPlaceholder: String = "Buscar opción...",
  createButtonPrefix: String = "Crear",
  onCreateNew: ((String) -> Unit)? = null,
  onTogglePin: ((T) -> Unit)? = null,
  onDismiss: () -> Unit,
  onSelect: (T) -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var query by remember { mutableStateOf("") }
  val trimmedQuery = query.trim()
  val filtered =
    remember(options, query) {
      val q = query.trim().lowercase()
      if (q.isEmpty()) options
      else {
        options.filter {
          it.title.lowercase().contains(q) || (it.subtitle?.lowercase()?.contains(q) == true)
        }
      }
    }
  val showCreateNew =
    onCreateNew != null &&
      trimmedQuery.isNotEmpty() &&
      options.none { it.title.equals(trimmedQuery, ignoreCase = true) }

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
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Surface(shape = RoundedCornerShape(8.dp), color = containerColor) {
          Text(
            text = "${options.size} disponibles",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = accentColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          )
        }
      }

      if (options.size > 5 || onCreateNew != null) {
        OutlinedTextField(
          value = query,
          onValueChange = { query = it },
          label = { Text(searchPlaceholder) },
          leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
          trailingIcon = {
            if (query.isNotEmpty()) {
              IconButton(onClick = { query = "" }) {
                Icon(Icons.Rounded.Close, contentDescription = "Limpiar búsqueda")
              }
            }
          },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("picker_search_input"),
          shape = RoundedCornerShape(12.dp),
        )
      }

      if (showCreateNew) {
        Button(
          onClick = { onCreateNew?.invoke(trimmedQuery) },
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("picker_create_new_btn"),
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color.White),
        ) {
          Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "$createButtonPrefix \"$trimmedQuery\"",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      if (filtered.isEmpty() && !showCreateNew) {
        Text(
          text = "No se encontraron coincidencias para \"$query\"",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(vertical = 20.dp),
        )
      } else if (filtered.isNotEmpty()) {
        LazyColumn(
          modifier = Modifier.fillMaxWidth().height(300.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(filtered, key = { it.id.toString() }) { option ->
            val selected = option.id == selectedId
            Surface(
              onClick = { onSelect(option.id) },
              shape = RoundedCornerShape(12.dp),
              color =
                if (selected) containerColor
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
              border =
                if (selected) BorderStroke(1.5.dp, accentColor)
                else BorderStroke(1.dp, LocalFinanceColors.current.cardBorder),
              modifier = Modifier.fillMaxWidth().testTag("picker_option_${option.id}"),
            ) {
              Row(
                modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    if (option.isPinned && onTogglePin == null) {
                      Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = "Fijado",
                        tint = LocalFinanceColors.current.pendingAccent,
                        modifier = Modifier.size(15.dp),
                      )
                    }
                    Text(
                      text = option.title,
                      style = MaterialTheme.typography.bodyLarge,
                      fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                      color = if (selected) accentColor else MaterialTheme.colorScheme.onSurface,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                  if (!option.subtitle.isNullOrBlank()) {
                    Text(
                      text = option.subtitle,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                  if (onTogglePin != null) {
                    IconButton(
                      onClick = { onTogglePin(option.id) },
                      modifier = Modifier.testTag("picker_pin_${option.id}"),
                    ) {
                      Icon(
                        imageVector =
                          if (option.isPinned) Icons.Rounded.Star else Icons.Outlined.StarOutline,
                        contentDescription =
                          if (option.isPinned) "Quitar de favoritos" else "Fijar favorito",
                        tint =
                          if (option.isPinned) LocalFinanceColors.current.pendingAccent
                          else MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                  }
                  if (selected) {
                    Icon(
                      imageVector = Icons.Rounded.Check,
                      contentDescription = "Seleccionado",
                      tint = accentColor,
                      modifier = Modifier.padding(end = 6.dp),
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
