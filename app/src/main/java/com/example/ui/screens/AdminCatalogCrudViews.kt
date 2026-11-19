package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ListAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.AccountEntity
import com.example.data.local.ClassificationEntity
import com.example.data.local.DetailItemEntity
import com.example.data.local.MerchantEntity
import com.example.data.local.TransactionTypes
import com.example.domain.FiscalPeriodCalculator
import com.example.ui.FinanceUiState
import com.example.ui.theme.LocalFinanceColors
import com.example.ui.theme.MonoFontFamily

private enum class CatalogFilterMode {
  ALL,
  ACTIVE_ONLY,
  PINNED_ONLY,
  WITH_BUDGET,
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
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
      IconButton(onClick = onBack, modifier = Modifier.testTag("admin_sub_back_btn")) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver a Administración")
      }
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    if (onAdd != null) {
      Button(
        onClick = onAdd,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.height(48.dp).testTag("admin_add_item_btn"),
      ) {
        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Agregar", style = MaterialTheme.typography.labelMedium)
      }
    }
  }
}

@Composable
private fun CatalogSearchAndFilterBar(
  query: String,
  onQueryChange: (String) -> Unit,
  filterMode: CatalogFilterMode,
  onSelectFilter: (CatalogFilterMode) -> Unit,
  includeBudgetFilter: Boolean,
  totalCount: Int,
  activeCount: Int,
  pinnedCount: Int,
  searchPlaceholder: String,
) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    OutlinedTextField(
      value = query,
      onValueChange = onQueryChange,
      label = { Text(searchPlaceholder) },
      leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
      trailingIcon = {
        if (query.isNotEmpty()) {
          IconButton(onClick = { onQueryChange("") }) {
            Icon(Icons.Rounded.Close, contentDescription = "Limpiar búsqueda")
          }
        }
      },
      singleLine = true,
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier.fillMaxWidth().testTag("admin_catalog_search_input"),
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "$activeCount activos de $totalCount · $pinnedCount fijados",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    LazyRow(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      item {
        FilterChip(
          selected = filterMode == CatalogFilterMode.ALL,
          onClick = { onSelectFilter(CatalogFilterMode.ALL) },
          label = { Text("Todos") },
          modifier = Modifier.testTag("admin_filter_all_chip"),
        )
      }
      item {
        FilterChip(
          selected = filterMode == CatalogFilterMode.ACTIVE_ONLY,
          onClick = {
            onSelectFilter(
              if (filterMode == CatalogFilterMode.ACTIVE_ONLY) CatalogFilterMode.ALL
              else CatalogFilterMode.ACTIVE_ONLY
            )
          },
          label = { Text("Solo activos") },
          modifier = Modifier.testTag("admin_active_filter_chip"),
        )
      }
      item {
        FilterChip(
          selected = filterMode == CatalogFilterMode.PINNED_ONLY,
          onClick = {
            onSelectFilter(
              if (filterMode == CatalogFilterMode.PINNED_ONLY) CatalogFilterMode.ALL
              else CatalogFilterMode.PINNED_ONLY
            )
          },
          leadingIcon = {
            Icon(
              imageVector = Icons.Rounded.Star,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
          },
          label = { Text("Fijados") },
          modifier = Modifier.testTag("admin_filter_pinned_chip"),
        )
      }
      if (includeBudgetFilter) {
        item {
          FilterChip(
            selected = filterMode == CatalogFilterMode.WITH_BUDGET,
            onClick = {
              onSelectFilter(
                if (filterMode == CatalogFilterMode.WITH_BUDGET) CatalogFilterMode.ALL
                else CatalogFilterMode.WITH_BUDGET
              )
            },
            label = { Text("Con tope") },
            modifier = Modifier.testTag("admin_filter_budget_chip"),
          )
        }
      }
    }
  }
}

@Composable
fun ClassificationsCrudView(
  state: FinanceUiState,
  onBack: () -> Unit,
  onSaveClassification: (ClassificationEntity) -> Unit,
  onDeleteClassification: (ClassificationEntity) -> Unit,
  onSaveDetail: (DetailItemEntity) -> Unit,
  onDeleteDetail: (DetailItemEntity) -> Unit,
  modifier: Modifier = Modifier,
) {
  val financeColors = LocalFinanceColors.current
  var selectedType by remember { mutableStateOf(TransactionTypes.GASTO) }
  var searchQuery by remember { mutableStateOf("") }
  var filterMode by remember { mutableStateOf(CatalogFilterMode.ALL) }
  var editingClass by remember { mutableStateOf<ClassificationEntity?>(null) }
  var addingNewClass by remember { mutableStateOf(false) }
  var managingDetailsFor by remember { mutableStateOf<ClassificationEntity?>(null) }

  val byType = state.allClassifications.filter { it.typeId == selectedType }
  val activeByTypeCount = byType.count { it.isActive }
  val pinnedByTypeCount = byType.count { it.isPinned }
  val filtered =
    remember(byType, searchQuery, filterMode, state.allDetails, state.allTransactions) {
      val q = searchQuery.trim().lowercase()
      byType
        .filter { cls ->
          val matchesMode =
            when (filterMode) {
              CatalogFilterMode.ALL -> true
              CatalogFilterMode.ACTIVE_ONLY -> cls.isActive
              CatalogFilterMode.PINNED_ONLY -> cls.isPinned
              CatalogFilterMode.WITH_BUDGET -> cls.monthlyBudgetCents != null
            }
          val matchesQuery =
            q.isEmpty() ||
              cls.name.lowercase().contains(q) ||
              state.allDetails.any { it.classificationId == cls.id && it.name.lowercase().contains(q) }
          matchesMode && matchesQuery
        }
        .sortedWith(
          compareByDescending<ClassificationEntity> { it.isPinned }
            .thenByDescending { state.classificationUsageCount(it.id) }
            .thenBy { it.sortOrder }
            .thenBy { it.id }
        )
    }

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
        val count = state.allClassifications.count { it.typeId == tId && it.isActive }
        Surface(
          onClick = { selectedType = tId },
          shape = RoundedCornerShape(10.dp),
          color =
            if (sel) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
          modifier = Modifier.weight(1f).height(44.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Text(
              text = "$label ($count)",
              fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
            )
          }
        }
      }
    }

    CatalogSearchAndFilterBar(
      query = searchQuery,
      onQueryChange = { searchQuery = it },
      filterMode = filterMode,
      onSelectFilter = { filterMode = it },
      includeBudgetFilter = selectedType == TransactionTypes.GASTO,
      totalCount = byType.size,
      activeCount = activeByTypeCount,
      pinnedCount = pinnedByTypeCount,
      searchPlaceholder = "Buscar clasificación o detalle...",
    )

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      if (filtered.isEmpty()) {
        item {
          EmptyFilterCard(
            message = "No hay clasificaciones que coincidan con el filtro.",
          )
        }
      } else {
        items(filtered, key = { it.id }) { cls ->
          val detCount = state.allDetails.count { it.classificationId == cls.id && it.isActive }
          val uses = state.classificationUsageCount(cls.id)
          Surface(
            onClick = { managingDetailsFor = cls },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, financeColors.cardBorder),
            modifier = Modifier.fillMaxWidth().alpha(if (cls.isActive) 1f else 0.65f),
          ) {
            Row(
              modifier = Modifier.padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                Text(
                  text = cls.name,
                  style = MaterialTheme.typography.titleSmall,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                val usesText = if (uses > 0) " · $uses usos" else ""
                val budgetText =
                  cls.monthlyBudgetCents?.let {
                    " · Tope ${FiscalPeriodCalculator.formatMxn(it)}"
                  } ?: ""
                Text(
                  text = "$detCount detalles$usesText$budgetText",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onSaveClassification(cls.copy(isPinned = !cls.isPinned)) }) {
                  Icon(
                    imageVector =
                      if (cls.isPinned) Icons.Rounded.Star else Icons.Outlined.StarOutline,
                    contentDescription =
                      if (cls.isPinned) "Quitar de favoritos" else "Fijar favorito",
                    tint =
                      if (cls.isPinned) financeColors.pendingAccent
                      else MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                IconButton(onClick = { managingDetailsFor = cls }) {
                  Icon(
                    imageVector = Icons.Rounded.ListAlt,
                    contentDescription = "Gestionar detalles",
                    tint = MaterialTheme.colorScheme.primary,
                  )
                }
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
              isPinned = initial?.isPinned ?: false,
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
  val financeColors = LocalFinanceColors.current
  var newDetailName by remember { mutableStateOf("") }
  var filterQuery by remember { mutableStateOf("") }
  val visibleDetails =
    remember(details, filterQuery) {
      val q = filterQuery.trim().lowercase()
      val base = if (q.isEmpty()) details else details.filter { it.name.lowercase().contains(q) }
      base.sortedWith(
        compareByDescending<DetailItemEntity> { it.isPinned }
          .thenBy { it.sortOrder }
          .thenBy { it.id }
      )
    }

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
            modifier = Modifier.height(48.dp),
          ) {
            Icon(Icons.Rounded.Add, contentDescription = "Agregar detalle")
          }
        }
        if (details.size > 5) {
          OutlinedTextField(
            value = filterQuery,
            onValueChange = { filterQuery = it },
            label = { Text("Filtrar detalles (${details.size})") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
              if (filterQuery.isNotEmpty()) {
                IconButton(onClick = { filterQuery = "" }) {
                  Icon(Icons.Rounded.Close, contentDescription = "Limpiar filtro")
                }
              }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        }
        LazyColumn(modifier = Modifier.height(240.dp)) {
          if (visibleDetails.isEmpty()) {
            item {
              Text(
                text = "Sin detalles para mostrar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
              )
            }
          } else {
            items(visibleDetails, key = { it.id }) { det ->
              Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(
                  text = det.name,
                  modifier = Modifier.weight(1f).alpha(if (det.isActive) 1f else 0.6f),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                  IconButton(onClick = { onSaveDetail(det.copy(isPinned = !det.isPinned)) }) {
                    Icon(
                      imageVector =
                        if (det.isPinned) Icons.Rounded.Star else Icons.Outlined.StarOutline,
                      contentDescription =
                        if (det.isPinned) "Quitar de favoritos" else "Fijar detalle",
                      tint =
                        if (det.isPinned) financeColors.pendingAccent
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.size(18.dp),
                    )
                  }
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
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("Listo") } },
  )
}

@Composable
fun AccountsCrudView(
  state: FinanceUiState,
  onBack: () -> Unit,
  onSaveAccount: (AccountEntity) -> Unit,
  onDeleteAccount: (AccountEntity) -> Unit,
  modifier: Modifier = Modifier,
) {
  val financeColors = LocalFinanceColors.current
  var editingAccount by remember { mutableStateOf<AccountEntity?>(null) }
  var creatingNew by remember { mutableStateOf(false) }
  var searchQuery by remember { mutableStateOf("") }
  var filterMode by remember { mutableStateOf(CatalogFilterMode.ALL) }

  val filtered =
    remember(state.allAccounts, searchQuery, filterMode, state.allTransactions) {
      val q = searchQuery.trim().lowercase()
      state.allAccounts
        .filter { acc ->
          val matchesMode =
            when (filterMode) {
              CatalogFilterMode.ACTIVE_ONLY -> acc.isActive
              CatalogFilterMode.PINNED_ONLY -> acc.isPinned
              else -> true
            }
          val matchesQuery =
            q.isEmpty() || acc.name.lowercase().contains(q) || acc.shortLabel.lowercase().contains(q)
          matchesMode && matchesQuery
        }
        .sortedWith(
          compareByDescending<AccountEntity> { it.isPinned }
            .thenByDescending { state.accountUsageCount(it.id) }
            .thenBy { it.sortOrder }
            .thenBy { it.id }
        )
    }

  Column(modifier = modifier.fillMaxSize()) {
    SubSectionTopBar(
      title = "Cuentas y Métodos",
      onBack = onBack,
      onAdd = { creatingNew = true },
    )

    CatalogSearchAndFilterBar(
      query = searchQuery,
      onQueryChange = { searchQuery = it },
      filterMode = filterMode,
      onSelectFilter = { filterMode = it },
      includeBudgetFilter = false,
      totalCount = state.allAccounts.size,
      activeCount = state.allAccounts.count { it.isActive },
      pinnedCount = state.allAccounts.count { it.isPinned },
      searchPlaceholder = "Buscar cuenta o etiqueta...",
    )

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      if (filtered.isEmpty()) {
        item { EmptyFilterCard("No se encontraron cuentas con ese criterio.") }
      } else {
        items(filtered, key = { it.id }) { acc ->
          val uses = state.accountUsageCount(acc.id)
          val usesText = if (uses > 0) " · $uses movs" else ""
          Surface(
            onClick = { editingAccount = acc },
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, financeColors.cardBorder),
            modifier = Modifier.fillMaxWidth().alpha(if (acc.isActive) 1f else 0.65f),
          ) {
            Row(
              modifier = Modifier.padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                Text(
                  text = "${acc.name} (${acc.shortLabel})",
                  style = MaterialTheme.typography.titleSmall,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = "Saldo: ${FiscalPeriodCalculator.formatMxn(acc.balanceCents)}$usesText",
                  fontFamily = MonoFontFamily,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.primary,
                  fontWeight = FontWeight.Bold,
                )
              }
              Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onSaveAccount(acc.copy(isPinned = !acc.isPinned)) }) {
                  Icon(
                    imageVector =
                      if (acc.isPinned) Icons.Rounded.Star else Icons.Outlined.StarOutline,
                    contentDescription =
                      if (acc.isPinned) "Quitar cuenta de favoritos" else "Fijar cuenta favorita",
                    tint =
                      if (acc.isPinned) financeColors.pendingAccent
                      else MaterialTheme.colorScheme.onSurfaceVariant,
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
              isPinned = initial?.isPinned ?: false,
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
fun MerchantsCrudView(
  state: FinanceUiState,
  onBack: () -> Unit,
  onSaveMerchant: (MerchantEntity) -> Unit,
  onDeleteMerchant: (MerchantEntity) -> Unit,
  modifier: Modifier = Modifier,
) {
  val financeColors = LocalFinanceColors.current
  var editing by remember { mutableStateOf<MerchantEntity?>(null) }
  var adding by remember { mutableStateOf(false) }
  var searchQuery by remember { mutableStateOf("") }
  var filterMode by remember { mutableStateOf(CatalogFilterMode.ALL) }

  val filtered =
    remember(state.allMerchants, searchQuery, filterMode, state.allTransactions) {
      val q = searchQuery.trim().lowercase()
      state.allMerchants
        .filter { m ->
          val matchesMode =
            when (filterMode) {
              CatalogFilterMode.ACTIVE_ONLY -> m.isActive
              CatalogFilterMode.PINNED_ONLY -> m.isPinned
              else -> true
            }
          matchesMode && (q.isEmpty() || m.name.lowercase().contains(q))
        }
        .sortedWith(
          compareByDescending<MerchantEntity> { it.isPinned }
            .thenByDescending { state.merchantUsageCount(it.name) }
            .thenBy { it.sortOrder }
            .thenBy { it.name }
        )
    }

  Column(modifier = modifier.fillMaxSize()) {
    SubSectionTopBar(
      title = "Comercios y Proveedores",
      onBack = onBack,
      onAdd = { adding = true },
    )

    CatalogSearchAndFilterBar(
      query = searchQuery,
      onQueryChange = { searchQuery = it },
      filterMode = filterMode,
      onSelectFilter = { filterMode = it },
      includeBudgetFilter = false,
      totalCount = state.allMerchants.size,
      activeCount = state.allMerchants.count { it.isActive },
      pinnedCount = state.allMerchants.count { it.isPinned },
      searchPlaceholder = "Buscar comercio o proveedor...",
    )

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      if (filtered.isEmpty()) {
        item { EmptyFilterCard("No se encontraron comercios con ese criterio.") }
      } else {
        items(filtered, key = { it.id }) { m ->
          val uses = state.merchantUsageCount(m.name)
          Surface(
            onClick = { editing = m },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, financeColors.cardBorder),
            modifier = Modifier.fillMaxWidth().alpha(if (m.isActive) 1f else 0.65f),
          ) {
            Row(
              modifier = Modifier.padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                Text(
                  text = m.name,
                  style = MaterialTheme.typography.titleSmall,
                )
                if (uses > 0) {
                  Text(
                    text = "$uses registros previos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
              Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onSaveMerchant(m.copy(isPinned = !m.isPinned)) }) {
                  Icon(
                    imageVector =
                      if (m.isPinned) Icons.Rounded.Star else Icons.Outlined.StarOutline,
                    contentDescription =
                      if (m.isPinned) "Quitar de favoritos" else "Fijar comercio favorito",
                    tint =
                      if (m.isPinned) financeColors.pendingAccent
                      else MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                Switch(
                  checked = m.isActive,
                  onCheckedChange = { onSaveMerchant(m.copy(isActive = it)) },
                )
              }
            }
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
                isPinned = editing?.isPinned ?: false,
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
private fun EmptyFilterCard(message: String) {
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = LocalFinanceColors.current.subtleSurface,
    border = BorderStroke(1.dp, LocalFinanceColors.current.cardBorder),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Text(
      text = message,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(16.dp),
    )
  }
}
