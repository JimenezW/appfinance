package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.AccountEntity
import com.example.data.local.ClassificationEntity
import com.example.data.local.DetailItemEntity
import com.example.data.local.MerchantEntity
import com.example.data.local.TransactionTypes
import com.example.domain.FiscalPeriodCalculator
import com.example.ui.AdminSubSection
import com.example.ui.FinanceUiState
import com.example.ui.theme.LocalFinanceColors
import com.example.ui.theme.MonoFontFamily

@Composable
fun AdminScreen(
  state: FinanceUiState,
  onSelectSubSection: (AdminSubSection) -> Unit,
  onOpenScheduledTab: () -> Unit,
  onSaveClassification: (ClassificationEntity) -> Unit,
  onDeleteClassification: (ClassificationEntity) -> Unit,
  onSaveDetail: (DetailItemEntity) -> Unit,
  onDeleteDetail: (DetailItemEntity) -> Unit,
  onSaveAccount: (AccountEntity) -> Unit,
  onDeleteAccount: (AccountEntity) -> Unit,
  onSaveMerchant: (MerchantEntity) -> Unit,
  onDeleteMerchant: (MerchantEntity) -> Unit,
  onGenerateCsv: () -> String,
  onGenerateJson: () -> String,
  onResetAllData: () -> Unit,
  onNotify: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (state.adminSubSection != AdminSubSection.HUB) {
    BackHandler { onSelectSubSection(AdminSubSection.HUB) }
  }

  when (state.adminSubSection) {
    AdminSubSection.HUB ->
      AdminHubView(
        state = state,
        onOpenSection = { section ->
          if (section == AdminSubSection.SCHEDULED) onOpenScheduledTab()
          else onSelectSubSection(section)
        },
        modifier = modifier,
      )
    AdminSubSection.CLASSIFICATIONS ->
      ClassificationsCrudView(
        state = state,
        onBack = { onSelectSubSection(AdminSubSection.HUB) },
        onSaveClassification = onSaveClassification,
        onDeleteClassification = onDeleteClassification,
        onSaveDetail = onSaveDetail,
        onDeleteDetail = onDeleteDetail,
        modifier = modifier,
      )
    AdminSubSection.ACCOUNTS ->
      AccountsCrudView(
        state = state,
        onBack = { onSelectSubSection(AdminSubSection.HUB) },
        onSaveAccount = onSaveAccount,
        onDeleteAccount = onDeleteAccount,
        modifier = modifier,
      )
    AdminSubSection.MERCHANTS ->
      MerchantsCrudView(
        state = state,
        onBack = { onSelectSubSection(AdminSubSection.HUB) },
        onSaveMerchant = onSaveMerchant,
        onDeleteMerchant = onDeleteMerchant,
        modifier = modifier,
      )
    AdminSubSection.SCHEDULED -> onOpenScheduledTab()
    AdminSubSection.BACKUP ->
      BackupExportView(
        state = state,
        onBack = { onSelectSubSection(AdminSubSection.HUB) },
        onGenerateCsv = onGenerateCsv,
        onGenerateJson = onGenerateJson,
        onResetAllData = onResetAllData,
        onNotify = onNotify,
        modifier = modifier,
      )
  }
}

@Composable
private fun AdminHubView(
  state: FinanceUiState,
  onOpenSection: (AdminSubSection) -> Unit,
  modifier: Modifier = Modifier,
) {
  val financeColors = LocalFinanceColors.current
  val gastoCount = state.allClassifications.count { it.typeId == TransactionTypes.GASTO && it.isActive }
  val ingresoCount = state.allClassifications.count { it.typeId == TransactionTypes.INGRESO && it.isActive }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = "Administración y Catálogos",
          style = MaterialTheme.typography.titleLarge,
        )
        Text(
          text = "Alimenta dinámicamente la Captura Inmediata",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    item {
      AdminHubCard(
        number = "1",
        badgeBg = financeColors.incomeContainer,
        badgeText = financeColors.incomeAccent,
        title = "Clasificación y Detalles",
        subtitle = "$gastoCount rubros Gasto • $ingresoCount rubros Ingreso • Presupuestos mensuales",
        onClick = { onOpenSection(AdminSubSection.CLASSIFICATIONS) },
        testTag = "admin_card_classifications",
      )
    }

    item {
      AdminHubCard(
        number = "2",
        badgeBg = financeColors.periodContainer,
        badgeText = financeColors.periodAccent,
        title = "Cuentas y Métodos de Pago",
        subtitle = "${state.activeAccounts.size} cuentas activas • Ajustar saldos y tarjetas",
        onClick = { onOpenSection(AdminSubSection.ACCOUNTS) },
        testTag = "admin_card_accounts",
      )
    }

    item {
      AdminHubCard(
        number = "3",
        badgeBg = financeColors.pendingContainer,
        badgeText = financeColors.pendingAccent,
        title = "Comercios y Proveedores",
        subtitle = "${state.activeMerchants.size} comercios frecuentes • Normalizar autocompletado",
        onClick = { onOpenSection(AdminSubSection.MERCHANTS) },
        testTag = "admin_card_merchants",
      )
    }

    item {
      AdminHubCard(
        number = "4",
        badgeBg = financeColors.subtleSurface,
        badgeText = MaterialTheme.colorScheme.onSurface,
        title = "Reglas Recurrentes",
        subtitle = "${state.allScheduledRules.size} reglas configuradas • Días de corte y avisos",
        onClick = { onOpenSection(AdminSubSection.SCHEDULED) },
        testTag = "admin_card_scheduled",
      )
    }

    item {
      AdminHubCard(
        number = "5",
        badgeBg = financeColors.expenseContainer,
        badgeText = financeColors.expenseAccent,
        title = "Respaldo y Exportación",
        subtitle = "Exportar CSV / JSON local • ${state.allTransactions.size} movimientos guardados",
        onClick = { onOpenSection(AdminSubSection.BACKUP) },
        testTag = "admin_card_backup",
      )
    }
  }
}

@Composable
private fun AdminHubCard(
  number: String,
  badgeBg: Color,
  badgeText: Color,
  title: String,
  subtitle: String,
  onClick: () -> Unit,
  testTag: String,
) {
  val financeColors = LocalFinanceColors.current
  Surface(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth().testTag(testTag),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, financeColors.cardBorder),
  ) {
    Row(
      modifier = Modifier.padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Surface(
        shape = RoundedCornerShape(10.dp),
        color = badgeBg,
        modifier = Modifier.size(40.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Text(
            text = number,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = badgeText,
          )
        }
      }
      Column(modifier = Modifier.weight(1f)) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Icon(
        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun SubSectionTopBar(
  title: String,
  onBack: () -> Unit,
  onAdd: (() -> Unit)? = null,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onBack, modifier = Modifier.testTag("admin_sub_back_btn")) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver a Administración")
      }
      Text(text = title, style = MaterialTheme.typography.titleMedium)
    }
    if (onAdd != null) {
      Button(
        onClick = onAdd,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.testTag("admin_add_item_btn"),
      ) {
        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Agregar", style = MaterialTheme.typography.labelMedium)
      }
    }
  }
}

@Composable
private fun ClassificationsCrudView(
  state: FinanceUiState,
  onBack: () -> Unit,
  onSaveClassification: (ClassificationEntity) -> Unit,
  onDeleteClassification: (ClassificationEntity) -> Unit,
  onSaveDetail: (DetailItemEntity) -> Unit,
  onDeleteDetail: (DetailItemEntity) -> Unit,
  modifier: Modifier = Modifier,
) {
  var selectedType by remember { mutableStateOf(TransactionTypes.GASTO) }
  var editingClass by remember { mutableStateOf<ClassificationEntity?>(null) }
  var addingNewClass by remember { mutableStateOf(false) }
  var managingDetailsFor by remember { mutableStateOf<ClassificationEntity?>(null) }

  val filtered = state.allClassifications.filter { it.typeId == selectedType }

  Column(modifier = modifier.fillMaxSize()) {
    SubSectionTopBar(
      title = "Clasificación y Detalles",
      onBack = onBack,
      onAdd = { addingNewClass = true },
    )

    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      listOf(TransactionTypes.GASTO to "Gasto", TransactionTypes.INGRESO to "Ingreso").forEach {
        (tId, label) ->
        val sel = selectedType == tId
        Surface(
          onClick = { selectedType = tId },
          shape = RoundedCornerShape(10.dp),
          color =
            if (sel) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
          modifier = Modifier.weight(1f).height(40.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Text(label, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
          }
        }
      }
    }

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      items(filtered, key = { it.id }) { cls ->
        val detCount = state.allDetails.count { it.classificationId == cls.id && it.isActive }
        Surface(
          onClick = { managingDetailsFor = cls },
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.surface,
          border = BorderStroke(1.dp, LocalFinanceColors.current.cardBorder),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(cls.name, style = MaterialTheme.typography.titleSmall)
              val budgetText =
                cls.monthlyBudgetCents?.let {
                  " • Presupuesto: ${FiscalPeriodCalculator.formatMxn(it)}"
                } ?: ""
              Text(
                text = "$detCount detalles activos$budgetText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
              IconButton(onClick = { editingClass = cls }) {
                Icon(Icons.Rounded.Edit, contentDescription = "Editar clasificación")
              }
              Switch(
                checked = cls.isActive,
                onCheckedChange = { onSaveClassification(cls.copy(isActive = it)) },
              )
            }
          }
        }
      }
    }
  }

  if (addingNewClass || editingClass != null) {
    ClassificationEditorDialog(
      typeId = selectedType,
      initial = editingClass,
      onDismiss = {
        addingNewClass = false
        editingClass = null
      },
      onSave = { item ->
        onSaveClassification(item)
        addingNewClass = false
        editingClass = null
      },
      onDelete =
        editingClass?.let { existing ->
          {
            onDeleteClassification(existing)
            editingClass = null
          }
        },
    )
  }

  managingDetailsFor?.let { cls ->
    DetailsManagerDialog(
      classification = cls,
      details = state.allDetails.filter { it.classificationId == cls.id },
      onDismiss = { managingDetailsFor = null },
      onSaveDetail = onSaveDetail,
      onDeleteDetail = onDeleteDetail,
    )
  }
}

@Composable
private fun ClassificationEditorDialog(
  typeId: String,
  initial: ClassificationEntity?,
  onDismiss: () -> Unit,
  onSave: (ClassificationEntity) -> Unit,
  onDelete: (() -> Unit)?,
) {
  var name by remember { mutableStateOf(initial?.name ?: "") }
  var budgetText by remember {
    mutableStateOf(
      initial?.monthlyBudgetCents?.let { "%.2f".format(java.util.Locale.US, it / 100.0) } ?: ""
    )
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (initial == null) "Nueva Clasificación" else "Editar Clasificación") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Nombre de clasificación") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        if ((initial?.typeId ?: typeId) == TransactionTypes.GASTO) {
          OutlinedTextField(
            value = budgetText,
            onValueChange = { budgetText = it },
            label = { Text("Presupuesto mensual opcional (MXN)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        }
        if (onDelete != null) {
          TextButton(
            onClick = onDelete,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
          ) {
            Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text("Eliminar clasificación")
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val budgetCents =
            if (budgetText.isBlank()) null
            else FiscalPeriodCalculator.parseAmountToCents(budgetText).takeIf { it > 0L }
          onSave(
            ClassificationEntity(
              id = initial?.id ?: 0L,
              typeId = initial?.typeId ?: typeId,
              name = name.trim(),
              sortOrder = initial?.sortOrder ?: 99,
              isActive = initial?.isActive ?: true,
              monthlyBudgetCents = budgetCents,
            )
          )
        },
        enabled = name.isNotBlank(),
      ) {
        Text("Guardar")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
  )
}

@Composable
private fun DetailsManagerDialog(
  classification: ClassificationEntity,
  details: List<DetailItemEntity>,
  onDismiss: () -> Unit,
  onSaveDetail: (DetailItemEntity) -> Unit,
  onDeleteDetail: (DetailItemEntity) -> Unit,
) {
  var newDetailName by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Detalles de ${classification.name}") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OutlinedTextField(
            value = newDetailName,
            onValueChange = { newDetailName = it },
            label = { Text("Nuevo detalle") },
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
          Button(
            onClick = {
              onSaveDetail(
                DetailItemEntity(
                  classificationId = classification.id,
                  name = newDetailName.trim(),
                  sortOrder = details.size + 1,
                )
              )
              newDetailName = ""
            },
            enabled = newDetailName.isNotBlank(),
          ) {
            Icon(Icons.Rounded.Add, contentDescription = "Agregar detalle")
          }
        }
        LazyColumn(modifier = Modifier.height(240.dp)) {
          items(details, key = { it.id }) { det ->
            Row(
              modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(det.name, modifier = Modifier.weight(1f))
              Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onDeleteDetail(det) }) {
                  Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = "Eliminar detalle",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                  )
                }
                Switch(
                  checked = det.isActive,
                  onCheckedChange = { onSaveDetail(det.copy(isActive = it)) },
                )
              }
            }
          }
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("Listo") } },
  )
}

@Composable
private fun AccountsCrudView(
  state: FinanceUiState,
  onBack: () -> Unit,
  onSaveAccount: (AccountEntity) -> Unit,
  onDeleteAccount: (AccountEntity) -> Unit,
  modifier: Modifier = Modifier,
) {
  var editingAccount by remember { mutableStateOf<AccountEntity?>(null) }
  var creatingNew by remember { mutableStateOf(false) }

  Column(modifier = modifier.fillMaxSize()) {
    SubSectionTopBar(
      title = "Cuentas y Métodos de Pago",
      onBack = onBack,
      onAdd = { creatingNew = true },
    )
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      items(state.allAccounts, key = { it.id }) { acc ->
        Surface(
          onClick = { editingAccount = acc },
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.surface,
          border = BorderStroke(1.dp, LocalFinanceColors.current.cardBorder),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(acc.name, style = MaterialTheme.typography.titleSmall)
              Text(
                text = "Saldo: ${FiscalPeriodCalculator.formatMxn(acc.balanceCents)}",
                fontFamily = MonoFontFamily,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
              )
            }
            Switch(
              checked = acc.isActive,
              onCheckedChange = { onSaveAccount(acc.copy(isActive = it)) },
            )
          }
        }
      }
    }
  }

  if (creatingNew || editingAccount != null) {
    AccountEditorDialog(
      initial = editingAccount,
      onDismiss = {
        creatingNew = false
        editingAccount = null
      },
      onSave = { acc ->
        onSaveAccount(acc)
        creatingNew = false
        editingAccount = null
      },
      onDelete =
        editingAccount?.let { existing ->
          {
            onDeleteAccount(existing)
            editingAccount = null
          }
        },
    )
  }
}

@Composable
private fun AccountEditorDialog(
  initial: AccountEntity?,
  onDismiss: () -> Unit,
  onSave: (AccountEntity) -> Unit,
  onDelete: (() -> Unit)?,
) {
  var name by remember { mutableStateOf(initial?.name ?: "") }
  var shortLabel by remember { mutableStateOf(initial?.shortLabel ?: "") }
  var balanceText by remember {
    mutableStateOf(
      if (initial != null) "%.2f".format(java.util.Locale.US, initial.balanceCents / 100.0)
      else "0.00"
    )
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (initial == null) "Nueva Cuenta" else "Editar Cuenta") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Nombre completo") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = shortLabel,
          onValueChange = { shortLabel = it },
          label = { Text("Etiqueta corta (chips)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = balanceText,
          onValueChange = { balanceText = it },
          label = { Text("Saldo actual (MXN)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        if (onDelete != null) {
          TextButton(
            onClick = onDelete,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
          ) {
            Text("Eliminar cuenta")
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val isNegative = balanceText.trim().startsWith("-")
          val absCents =
            FiscalPeriodCalculator.parseAmountToCents(balanceText.trim().removePrefix("-"))
          val finalCents = if (isNegative) -absCents else absCents
          onSave(
            AccountEntity(
              id = initial?.id ?: 0L,
              name = name.trim(),
              shortLabel = shortLabel.trim().ifEmpty { name.trim().take(12) },
              balanceCents = finalCents,
              accountKind = initial?.accountKind ?: "DEBIT",
              sortOrder = initial?.sortOrder ?: 99,
              isActive = initial?.isActive ?: true,
            )
          )
        },
        enabled = name.isNotBlank(),
      ) {
        Text("Guardar")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
  )
}

@Composable
private fun MerchantsCrudView(
  state: FinanceUiState,
  onBack: () -> Unit,
  onSaveMerchant: (MerchantEntity) -> Unit,
  onDeleteMerchant: (MerchantEntity) -> Unit,
  modifier: Modifier = Modifier,
) {
  var editing by remember { mutableStateOf<MerchantEntity?>(null) }
  var adding by remember { mutableStateOf(false) }

  Column(modifier = modifier.fillMaxSize()) {
    SubSectionTopBar(
      title = "Comercios y Proveedores",
      onBack = onBack,
      onAdd = { adding = true },
    )
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      items(state.allMerchants, key = { it.id }) { m ->
        Surface(
          onClick = { editing = m },
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.surface,
          border = BorderStroke(1.dp, LocalFinanceColors.current.cardBorder),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(m.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Switch(
              checked = m.isActive,
              onCheckedChange = { onSaveMerchant(m.copy(isActive = it)) },
            )
          }
        }
      }
    }
  }

  if (adding || editing != null) {
    var name by remember(adding, editing) { mutableStateOf(editing?.name ?: "") }
    AlertDialog(
      onDismissRequest = {
        adding = false
        editing = null
      },
      title = { Text(if (editing == null) "Nuevo Comercio" else "Editar Comercio") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Nombre del comercio o proveedor") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          editing?.let { existing ->
            TextButton(
              onClick = {
                onDeleteMerchant(existing)
                editing = null
              },
              colors =
                ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
              Text("Eliminar comercio")
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            onSaveMerchant(
              MerchantEntity(
                id = editing?.id ?: 0L,
                name = name.trim(),
                sortOrder = editing?.sortOrder ?: 99,
                isActive = editing?.isActive ?: true,
              )
            )
            adding = false
            editing = null
          },
          enabled = name.isNotBlank(),
        ) {
          Text("Guardar")
        }
      },
      dismissButton = {
        TextButton(
          onClick = {
            adding = false
            editing = null
          }
        ) {
          Text("Cancelar")
        }
      },
    )
  }
}

@Composable
private fun BackupExportView(
  state: FinanceUiState,
  onBack: () -> Unit,
  onGenerateCsv: () -> String,
  onGenerateJson: () -> String,
  onResetAllData: () -> Unit,
  onNotify: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var confirmReset by remember { mutableStateOf(false) }

  val csvLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
      if (uri != null) {
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
              stream.write(onGenerateCsv().toByteArray(Charsets.UTF_8))
            }
          }
          .onSuccess { onNotify("Respaldo CSV exportado correctamente") }
          .onFailure { onNotify("No se pudo guardar el archivo CSV") }
      }
    }

  val jsonLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
      uri ->
      if (uri != null) {
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
              stream.write(onGenerateJson().toByteArray(Charsets.UTF_8))
            }
          }
          .onSuccess { onNotify("Respaldo JSON exportado correctamente") }
          .onFailure { onNotify("No se pudo guardar el archivo JSON") }
      }
    }

  Column(modifier = modifier.fillMaxSize()) {
    SubSectionTopBar(title = "Respaldo y Exportación", onBack = onBack)

    Column(
      modifier = Modifier.fillMaxSize().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, LocalFinanceColors.current.cardBorder),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text("Resumen de Datos Locales (Room SQLite)", style = MaterialTheme.typography.titleSmall)
          Text(
            text =
              "Movimientos: ${state.allTransactions.size} • Cuentas: ${state.allAccounts.size} • Clasificaciones: ${state.allClassifications.size} • Reglas: ${state.allScheduledRules.size}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Button(
        onClick = { csvLauncher.launch("mis_finanzas_movimientos.csv") },
        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("export_csv_btn"),
        shape = RoundedCornerShape(12.dp),
      ) {
        Icon(Icons.Rounded.Download, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Exportar Movimientos a CSV")
      }

      OutlinedButton(
        onClick = { jsonLauncher.launch("mis_finanzas_respaldo.json") },
        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("export_json_btn"),
        shape = RoundedCornerShape(12.dp),
      ) {
        Icon(Icons.Rounded.Download, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Exportar Respaldo Completo JSON")
      }

      Spacer(modifier = Modifier.weight(1f))

      OutlinedButton(
        onClick = { confirmReset = true },
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
      ) {
        Icon(Icons.Rounded.RestartAlt, contentDescription = null)
        Spacer(modifier = Modifier.width(6.dp))
        Text("Restablecer catálogos y limpiar movimientos")
      }
    }
  }

  if (confirmReset) {
    AlertDialog(
      onDismissRequest = { confirmReset = false },
      title = { Text("Restablecer datos locales") },
      text = {
        Text(
          "Se borrarán todas las transacciones registradas, se pondrán los saldos en $0.00 y se restaurarán los catálogos semilla originales."
        )
      },
      confirmButton = {
        Button(
          onClick = {
            confirmReset = false
            onResetAllData()
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) {
          Text("Restablecer")
        }
      },
      dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancelar") } },
    )
  }
}
