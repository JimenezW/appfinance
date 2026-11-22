package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.AccountEntity
import com.example.data.local.ClassificationEntity
import com.example.data.local.DetailItemEntity
import com.example.data.local.MerchantEntity
import com.example.data.local.TransactionTypes
import com.example.ui.AdminSubSection
import com.example.ui.FinanceUiState
import com.example.ui.theme.LocalFinanceColors

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
  onSaveSyncConfig: (endpointUrl: String, authToken: String, autoSyncOnWifi: Boolean, deviceLabel: String) -> Unit,
  onToggleAutoWifiSync: (Boolean) -> Unit = {},
  onTriggerManualSync: () -> Unit,
  onTriggerFullHistorySync: () -> Unit = {},
  onLoadSyncBatchPreview: ((String) -> Unit) -> Unit,
  onGenerateAdminConfigJson: () -> String,
  onGenerateCapturedDataJson: () -> String,
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
        onToggleAutoWifiSync = onToggleAutoWifiSync,
        onTriggerManualSync = onTriggerManualSync,
        onGenerateAdminConfigJson = onGenerateAdminConfigJson,
        onGenerateCapturedDataJson = onGenerateCapturedDataJson,
        onNotify = onNotify,
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
      SyncAndExportView(
        state = state,
        onBack = { onSelectSubSection(AdminSubSection.HUB) },
        onSaveSyncConfig = onSaveSyncConfig,
        onTriggerManualSync = onTriggerManualSync,
        onTriggerFullHistorySync = onTriggerFullHistorySync,
        onLoadSyncBatchPreview = onLoadSyncBatchPreview,
        onGenerateAdminConfigJson = onGenerateAdminConfigJson,
        onGenerateCapturedDataJson = onGenerateCapturedDataJson,
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
  onToggleAutoWifiSync: (Boolean) -> Unit,
  onTriggerManualSync: () -> Unit,
  onGenerateAdminConfigJson: () -> String,
  onGenerateCapturedDataJson: () -> String,
  onNotify: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val financeColors = LocalFinanceColors.current
  val gastoCount = state.allClassifications.count { TransactionTypes.isExpense(it.typeId) && it.isActive }
  val ingresoCount = state.allClassifications.count { TransactionTypes.isIncome(it.typeId) && it.isActive }
  var previewTitle by remember { mutableStateOf<String?>(null) }
  var previewContent by remember { mutableStateOf("") }

  val adminJsonLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
      if (uri != null) {
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
              stream.write(onGenerateAdminConfigJson().toByteArray(Charsets.UTF_8))
            }
          }
          .onSuccess { onNotify("JSON de configuración Admin exportado") }
          .onFailure { onNotify("No se pudo guardar el JSON de Admin") }
      }
    }

  val capturedJsonLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
      if (uri != null) {
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
              stream.write(onGenerateCapturedDataJson().toByteArray(Charsets.UTF_8))
            }
          }
          .onSuccess { onNotify("JSON de data capturada exportado") }
          .onFailure { onNotify("No se pudo guardar el JSON de captura") }
      }
    }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = "Administración",
          style = MaterialTheme.typography.titleLarge,
        )
        Text(
          text = "Catálogos organizados, sincronización Wi-Fi y extracción JSON",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    // Compact Wi-Fi Sync & Side-by-Side Manual JSON Export Panel
    item {
      val isWifi = state.wifiState.isWifiConnected
      val autoWifiEnabled = state.syncSettings.autoSyncOnWifi
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, financeColors.cardBorder),
        modifier = Modifier.fillMaxWidth().testTag("admin_sync_export_panel"),
      ) {
        Column(
          modifier = Modifier.padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = if (isWifi) Icons.Rounded.Wifi else Icons.Rounded.WifiOff,
                contentDescription = null,
                tint = if (isWifi) financeColors.incomeAccent else financeColors.pendingAccent,
                modifier = Modifier.size(18.dp),
              )
              Text(
                text = state.wifiState.statusLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (isWifi) financeColors.incomeAccent else financeColors.pendingAccent,
              )
            }
            Surface(
              shape = RoundedCornerShape(8.dp),
              color =
                if (state.pendingSyncCount > 0) financeColors.pendingContainer
                else financeColors.incomeContainer,
            ) {
              Text(
                text =
                  if (state.pendingSyncCount > 0) "${state.pendingSyncCount} pendientes"
                  else "Sincronizado",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color =
                  if (state.pendingSyncCount > 0) financeColors.pendingAccent
                  else financeColors.incomeAccent,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              )
            }
          }

          Text(
            text = state.syncRuntimeStatus.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )

          Surface(
            onClick = { onToggleAutoWifiSync(!autoWifiEnabled) },
            shape = RoundedCornerShape(12.dp),
            color =
              if (autoWifiEnabled) financeColors.incomeContainer
              else financeColors.subtleSurface,
            border =
              BorderStroke(
                1.dp,
                if (autoWifiEnabled) financeColors.incomeAccent.copy(alpha = 0.45f)
                else financeColors.cardBorder,
              ),
            modifier = Modifier.fillMaxWidth().testTag("admin_hub_auto_wifi_sync_btn"),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Wifi,
                  contentDescription = null,
                  tint =
                    if (autoWifiEnabled) financeColors.incomeAccent
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(18.dp),
                )
                Column {
                  Text(
                    text = "Sincronización automática al detectar Wi-Fi",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color =
                      if (autoWifiEnabled) financeColors.onIncomeContainer
                      else MaterialTheme.colorScheme.onSurface,
                  )
                  Text(
                    text =
                      if (autoWifiEnabled) "Activa • sincroniza y notifica al conectar a Wi-Fi"
                      else "Inactiva • toca para activar sincronización automática",
                    style = MaterialTheme.typography.labelSmall,
                    color =
                      if (autoWifiEnabled) financeColors.incomeAccent
                      else MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
              Text(
                text = if (autoWifiEnabled) "ACTIVO" else "ACTIVAR",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color =
                  if (autoWifiEnabled) financeColors.incomeAccent
                  else MaterialTheme.colorScheme.primary,
              )
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OutlinedButton(
              onClick = onTriggerManualSync,
              modifier = Modifier.weight(1f).height(48.dp).testTag("admin_hub_sync_now_btn"),
              shape = RoundedCornerShape(12.dp),
              contentPadding = PaddingValues(horizontal = 10.dp),
            ) {
              Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Sincronizar", style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
            OutlinedButton(
              onClick = { onOpenSection(AdminSubSection.BACKUP) },
              modifier = Modifier.weight(1f).height(48.dp).testTag("admin_hub_api_config_btn"),
              shape = RoundedCornerShape(12.dp),
              contentPadding = PaddingValues(horizontal = 10.dp),
            ) {
              Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Configurar API", style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
          }

          Box(
            modifier = Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 2.dp),
          )

          Text(
            text = "Exportar JSON manual (base para API)",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
          )

          Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            CompactJsonExportTile(
              title = "Admin (JSON)",
              subtitle = "Catálogos y reglas",
              isPrimary = true,
              exportTag = "admin_hub_export_admin_json_btn",
              previewTag = "admin_hub_preview_admin_json_btn",
              onExport = { adminJsonLauncher.launch("mis_finanzas_admin_config.json") },
              onPreview = {
                previewTitle = "JSON Configuración Admin"
                previewContent = onGenerateAdminConfigJson()
              },
              modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            CompactJsonExportTile(
              title = "Captura (JSON)",
              subtitle = "${state.allTransactions.size} movimientos",
              isPrimary = false,
              exportTag = "admin_hub_export_captured_json_btn",
              previewTag = "admin_hub_preview_captured_json_btn",
              onExport = { capturedJsonLauncher.launch("mis_finanzas_data_capturada.json") },
              onPreview = {
                previewTitle = "JSON Data Capturada"
                previewContent = onGenerateCapturedDataJson()
              },
              modifier = Modifier.weight(1f).fillMaxHeight(),
            )
          }
        }
      }
    }

    item {
      Text(
        text = "Catálogos y Reglas",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    // 2x2 Ergonomic Grid for Catalog Management
    item {
      Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        AdminGridTile(
          icon = Icons.Rounded.Category,
          badgeText = "$gastoCount G · $ingresoCount I",
          badgeBg = financeColors.incomeContainer,
          badgeTint = financeColors.incomeAccent,
          title = "Clasificación y Detalles",
          subtitle = "Rubros, sub-detalles y topes",
          onClick = { onOpenSection(AdminSubSection.CLASSIFICATIONS) },
          testTag = "admin_card_classifications",
          modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        AdminGridTile(
          icon = Icons.Rounded.AccountBalanceWallet,
          badgeText = "${state.activeAccounts.size} activas",
          badgeBg = financeColors.periodContainer,
          badgeTint = financeColors.periodAccent,
          title = "Cuentas y Métodos",
          subtitle = "Tarjetas, efectivo y saldos",
          onClick = { onOpenSection(AdminSubSection.ACCOUNTS) },
          testTag = "admin_card_accounts",
          modifier = Modifier.weight(1f).fillMaxHeight(),
        )
      }
    }

    item {
      Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        AdminGridTile(
          icon = Icons.Rounded.Storefront,
          badgeText = "${state.activeMerchants.size} activos",
          badgeBg = financeColors.pendingContainer,
          badgeTint = financeColors.pendingAccent,
          title = "Comercios",
          subtitle = "Proveedores y búsqueda rápida",
          onClick = { onOpenSection(AdminSubSection.MERCHANTS) },
          testTag = "admin_card_merchants",
          modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        AdminGridTile(
          icon = Icons.Rounded.EventRepeat,
          badgeText = "${state.allScheduledRules.size} reglas",
          badgeBg = financeColors.subtleSurface,
          badgeTint = MaterialTheme.colorScheme.onSurface,
          title = "Reglas Recurrentes",
          subtitle = "Cortes y avisos automáticos",
          onClick = { onOpenSection(AdminSubSection.SCHEDULED) },
          testTag = "admin_card_scheduled",
          modifier = Modifier.weight(1f).fillMaxHeight(),
        )
      }
    }

    // Compact utility card for full API queue & CSV/JSON backups
    item {
      Surface(
        onClick = { onOpenSection(AdminSubSection.BACKUP) },
        modifier = Modifier.fillMaxWidth().testTag("admin_card_backup"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, financeColors.cardBorder),
      ) {
        Row(
          modifier = Modifier.padding(14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = financeColors.expenseContainer,
            modifier = Modifier.size(42.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Rounded.CloudSync,
                contentDescription = null,
                tint = financeColors.expenseAccent,
                modifier = Modifier.size(22.dp),
              )
            }
          }
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Cola de Sincronización y Respaldos",
              style = MaterialTheme.typography.titleSmall,
            )
            Text(
              text = "${state.pendingSyncCount} en cola · Endpoint REST, respaldo CSV y JSON",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
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
  }

  previewTitle?.let { title ->
    JsonPreviewDialog(
      title = title,
      jsonContent = previewContent,
      onDismiss = { previewTitle = null },
      onNotify = onNotify,
    )
  }
}

@Composable
private fun CompactJsonExportTile(
  title: String,
  subtitle: String,
  isPrimary: Boolean,
  exportTag: String,
  previewTag: String,
  onExport: () -> Unit,
  onPreview: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val financeColors = LocalFinanceColors.current
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = financeColors.subtleSurface,
    border = BorderStroke(1.dp, financeColors.cardBorder),
    modifier = modifier,
  ) {
    Column(
      modifier = Modifier.padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Column {
        Text(
          text = title,
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (isPrimary) {
          Button(
            onClick = onExport,
            modifier = Modifier.weight(1f).height(48.dp).testTag(exportTag),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
          ) {
            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Exportar", style = MaterialTheme.typography.labelMedium, maxLines = 1)
          }
        } else {
          OutlinedButton(
            onClick = onExport,
            modifier = Modifier.weight(1f).height(48.dp).testTag(exportTag),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
          ) {
            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Exportar", style = MaterialTheme.typography.labelMedium, maxLines = 1)
          }
        }

        Surface(
          onClick = onPreview,
          modifier = Modifier.size(48.dp).testTag(previewTag),
          shape = RoundedCornerShape(10.dp),
          color = MaterialTheme.colorScheme.surface,
          border = BorderStroke(1.dp, financeColors.cardBorder),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Rounded.Code,
              contentDescription = "Ver JSON de $title",
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun AdminGridTile(
  icon: ImageVector,
  badgeText: String,
  badgeBg: Color,
  badgeTint: Color,
  title: String,
  subtitle: String,
  onClick: () -> Unit,
  testTag: String,
  modifier: Modifier = Modifier,
) {
  val financeColors = LocalFinanceColors.current
  Surface(
    onClick = onClick,
    modifier = modifier.testTag(testTag),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, financeColors.cardBorder),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = badgeBg,
          modifier = Modifier.size(38.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = icon,
              contentDescription = null,
              tint = badgeTint,
              modifier = Modifier.size(20.dp),
            )
          }
        }
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = financeColors.subtleSurface,
        ) {
          Text(
            text = badgeText,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            maxLines = 1,
          )
        }
      }

      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}
