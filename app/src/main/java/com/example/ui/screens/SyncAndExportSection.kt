package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.SyncQueueItemEntity
import com.example.data.local.SyncStatus
import com.example.data.sync.SyncPhase
import com.example.ui.FinanceUiState
import com.example.ui.theme.LocalFinanceColors
import com.example.ui.theme.MonoFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SyncAndExportView(
  state: FinanceUiState,
  onBack: () -> Unit,
  onSaveSyncConfig: (endpointUrl: String, authToken: String, autoSyncOnWifi: Boolean, deviceLabel: String) -> Unit,
  onTriggerManualSync: () -> Unit,
  onLoadSyncBatchPreview: ((String) -> Unit) -> Unit,
  onGenerateAdminConfigJson: () -> String,
  onGenerateCapturedDataJson: () -> String,
  onGenerateCsv: () -> String,
  onGenerateJson: () -> String,
  onResetAllData: () -> Unit,
  onNotify: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val financeColors = LocalFinanceColors.current

  var apiUrlInput by remember(state.syncSettings.apiEndpointUrl) {
    mutableStateOf(state.syncSettings.apiEndpointUrl)
  }
  var authTokenInput by remember(state.syncSettings.apiAuthToken) {
    mutableStateOf(state.syncSettings.apiAuthToken)
  }
  var deviceLabelInput by remember(state.syncSettings.deviceLabel) {
    mutableStateOf(state.syncSettings.deviceLabel)
  }
  var confirmReset by remember { mutableStateOf(false) }
  var jsonPreviewTitle by remember { mutableStateOf<String?>(null) }
  var jsonPreviewContent by remember { mutableStateOf("") }

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

  val fullJsonLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
      if (uri != null) {
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
              stream.write(onGenerateJson().toByteArray(Charsets.UTF_8))
            }
          }
          .onSuccess { onNotify("Respaldo completo JSON exportado") }
          .onFailure { onNotify("No se pudo guardar el archivo JSON") }
      }
    }

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

  Column(modifier = modifier.fillMaxSize()) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = onBack, modifier = Modifier.testTag("admin_sub_back_btn")) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver a Administración")
      }
      Text(
        text = "Sincronización Wi-Fi y JSON",
        style = MaterialTheme.typography.titleMedium,
      )
    }

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // 1. Live Wi-Fi Sync Status Card
      item {
        val isWifi = state.wifiState.isWifiConnected
        val isSyncing = state.syncRuntimeStatus.phase == SyncPhase.SYNCING
        val statusColor =
          when {
            state.syncRuntimeStatus.phase == SyncPhase.ERROR -> financeColors.expenseAccent
            isWifi && state.pendingSyncCount == 0 -> financeColors.incomeAccent
            isWifi -> financeColors.periodAccent
            else -> financeColors.pendingAccent
          }
        val containerColor =
          when {
            state.syncRuntimeStatus.phase == SyncPhase.ERROR -> financeColors.expenseContainer
            isWifi && state.pendingSyncCount == 0 -> financeColors.incomeContainer
            isWifi -> financeColors.periodContainer
            else -> financeColors.pendingContainer
          }

        Surface(
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.surface,
          border = BorderStroke(1.dp, financeColors.cardBorder),
          modifier = Modifier.fillMaxWidth().testTag("sync_status_card"),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Surface(
                shape = RoundedCornerShape(10.dp),
                color = containerColor,
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Icon(
                    imageVector = if (isWifi) Icons.Rounded.Wifi else Icons.Rounded.WifiOff,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(16.dp),
                  )
                  Text(
                    text = state.wifiState.statusLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                  )
                }
              }

              Surface(
                shape = RoundedCornerShape(10.dp),
                color =
                  if (state.pendingSyncCount > 0) financeColors.pendingContainer
                  else financeColors.incomeContainer,
              ) {
                Text(
                  text =
                    if (state.pendingSyncCount > 0) "${state.pendingSyncCount} pendientes"
                    else "Al día",
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.Bold,
                  color =
                    if (state.pendingSyncCount > 0) financeColors.pendingAccent
                    else financeColors.incomeAccent,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
              }
            }

            Text(
              text = state.syncRuntimeStatus.message,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )

            state.syncSettings.lastSuccessfulSyncMillis?.let { lastMs ->
              Text(
                text = "Última sincronización exitosa: ${formatDateTime(lastMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                  text = "Sincronización automática por Wi-Fi",
                  style = MaterialTheme.typography.titleSmall,
                )
                Text(
                  text = "Envía los registros pendientes al detectar red Wi-Fi",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Switch(
                checked = state.syncSettings.autoSyncOnWifi,
                onCheckedChange = { enabled ->
                  onSaveSyncConfig(apiUrlInput, authTokenInput, enabled, deviceLabelInput)
                },
                modifier = Modifier.testTag("auto_wifi_sync_switch"),
              )
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Button(
                onClick = onTriggerManualSync,
                enabled = !isSyncing,
                modifier = Modifier.weight(1f).height(48.dp).testTag("sync_now_btn"),
                shape = RoundedCornerShape(12.dp),
              ) {
                if (isSyncing) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = Color.White,
                  )
                } else {
                  Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text("Sincronizar ahora", maxLines = 1, overflow = TextOverflow.Ellipsis)
              }

              OutlinedButton(
                onClick = {
                  onLoadSyncBatchPreview { json ->
                    jsonPreviewTitle = "Payload JSON de Sincronización Wi-Fi"
                    jsonPreviewContent = json
                  }
                },
                modifier = Modifier.height(48.dp).testTag("preview_sync_batch_btn"),
                shape = RoundedCornerShape(12.dp),
              ) {
                Icon(Icons.Rounded.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Ver Payload")
              }
            }
          }
        }
      }

      // 2. API Endpoint Configuration Card
      item {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.surface,
          border = BorderStroke(1.dp, financeColors.cardBorder),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.CloudSync,
                contentDescription = null,
                tint = financeColors.periodAccent,
              )
              Text(
                text = "Configuración del API REST (JSON)",
                style = MaterialTheme.typography.titleSmall,
              )
            }
            Text(
              text =
                "Cuando desarrolles tu backend, coloca aquí la URL POST que recibirá el JSON de sincronización automática por Wi-Fi.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
              value = apiUrlInput,
              onValueChange = { apiUrlInput = it },
              label = { Text("URL Endpoint POST (ej. https://api.ejemplo.com/v1/sync)") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("api_endpoint_input"),
              shape = RoundedCornerShape(12.dp),
            )
            OutlinedTextField(
              value = authTokenInput,
              onValueChange = { authTokenInput = it },
              label = { Text("Bearer Token / API Key (opcional)") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("api_token_input"),
              shape = RoundedCornerShape(12.dp),
            )
            OutlinedTextField(
              value = deviceLabelInput,
              onValueChange = { deviceLabelInput = it },
              label = { Text("Etiqueta del dispositivo") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("device_label_input"),
              shape = RoundedCornerShape(12.dp),
            )
            Button(
              onClick = {
                onSaveSyncConfig(
                  apiUrlInput,
                  authTokenInput,
                  state.syncSettings.autoSyncOnWifi,
                  deviceLabelInput,
                )
              },
              modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_api_config_btn"),
              shape = RoundedCornerShape(12.dp),
            ) {
              Text("Guardar configuración del API")
            }
          }
        }
      }

      // 3. Manual JSON Export Section (Admin Config & Captured Data)
      item {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.surface,
          border = BorderStroke(1.dp, financeColors.cardBorder),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text(
              text = "Extracción Manual en JSON (Base para API)",
              style = MaterialTheme.typography.titleSmall,
            )
            Text(
              text =
                "Extrae por separado la configuración del administrador y los movimientos capturados para diseñar y probar el contrato JSON de tu API.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            JsonExportActionRow(
              title = "Exportar Admin configurado (JSON)",
              subtitle =
                "${state.allClassifications.size} clasif. • ${state.allAccounts.size} cuentas • ${state.allMerchants.size} comercios • ${state.allScheduledRules.size} reglas",
              isPrimary = true,
              exportTag = "export_admin_json_btn",
              previewTag = "preview_admin_json_btn",
              onExport = { adminJsonLauncher.launch("mis_finanzas_admin_config.json") },
              onPreview = {
                jsonPreviewTitle = "JSON Configuración Admin"
                jsonPreviewContent = onGenerateAdminConfigJson()
              },
            )

            JsonExportActionRow(
              title = "Exportar Data capturada (JSON)",
              subtitle =
                "${state.allTransactions.size} movimientos • ${state.pendingSyncCount} pendientes de envío",
              isPrimary = true,
              exportTag = "export_captured_json_btn",
              previewTag = "preview_captured_json_btn",
              onExport = { capturedJsonLauncher.launch("mis_finanzas_data_capturada.json") },
              onPreview = {
                jsonPreviewTitle = "JSON Data Capturada"
                jsonPreviewContent = onGenerateCapturedDataJson()
              },
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              OutlinedButton(
                onClick = { fullJsonLauncher.launch("mis_finanzas_respaldo_completo.json") },
                modifier = Modifier.weight(1f).height(48.dp).testTag("export_json_btn"),
                shape = RoundedCornerShape(12.dp),
              ) {
                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Respaldo JSON", maxLines = 1, overflow = TextOverflow.Ellipsis)
              }
              OutlinedButton(
                onClick = { csvLauncher.launch("mis_finanzas_movimientos.csv") },
                modifier = Modifier.weight(1f).height(48.dp).testTag("export_csv_btn"),
                shape = RoundedCornerShape(12.dp),
              ) {
                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("CSV", maxLines = 1, overflow = TextOverflow.Ellipsis)
              }
            }
          }
        }
      }

      // 4. Outbox Queue History
      item {
        Text(
          text = "Cola de Sincronización (${state.syncQueueHistory.size})",
          style = MaterialTheme.typography.titleSmall,
        )
      }

      if (state.syncQueueHistory.isEmpty()) {
        item {
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, financeColors.cardBorder),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(16.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.CloudDone,
                contentDescription = null,
                tint = financeColors.incomeAccent,
              )
              Text(
                text =
                  "Aún no hay cambios locales en cola. Cada movimiento o edición offline se registrará aquí automáticamente.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      } else {
        items(state.syncQueueHistory.take(25), key = { it.id }) { qItem ->
          SyncQueueItemCard(item = qItem)
        }
      }

      // 5. Reset Local Data
      item {
        Spacer(modifier = Modifier.height(6.dp))
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
  }

  jsonPreviewTitle?.let { title ->
    JsonPreviewDialog(
      title = title,
      jsonContent = jsonPreviewContent,
      onDismiss = { jsonPreviewTitle = null },
      onNotify = onNotify,
    )
  }

  if (confirmReset) {
    AlertDialog(
      onDismissRequest = { confirmReset = false },
      title = { Text("Restablecer datos locales") },
      text = {
        Text(
          "Se borrarán todas las transacciones registradas, la cola de sincronización y se restaurarán los catálogos semilla originales."
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

@Composable
fun JsonExportActionRow(
  title: String,
  subtitle: String,
  isPrimary: Boolean,
  exportTag: String,
  previewTag: String,
  onExport: () -> Unit,
  onPreview: () -> Unit,
) {
  val financeColors = LocalFinanceColors.current
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (isPrimary) {
      Button(
        onClick = onExport,
        modifier = Modifier.weight(1f).height(52.dp).testTag(exportTag),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
      ) {
        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    } else {
      OutlinedButton(
        onClick = onExport,
        modifier = Modifier.weight(1f).height(52.dp).testTag(exportTag),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
      ) {
        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
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
      }
    }

    Surface(
      onClick = onPreview,
      modifier = Modifier.size(52.dp).testTag(previewTag),
      shape = RoundedCornerShape(12.dp),
      color = financeColors.subtleSurface,
      border = BorderStroke(1.dp, financeColors.cardBorder),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
      ) {
        Icon(
          imageVector = Icons.Rounded.Code,
          contentDescription = "Ver y copiar JSON",
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(20.dp),
        )
      }
    }
  }
}

@Composable
fun JsonPreviewDialog(
  title: String,
  jsonContent: String,
  onDismiss: () -> Unit,
  onNotify: (String) -> Unit,
) {
  @Suppress("DEPRECATION")
  val clipboard = LocalClipboardManager.current

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title, style = MaterialTheme.typography.titleMedium) },
    text = {
      Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 360.dp),
      ) {
        Text(
          text = jsonContent,
          fontFamily = MonoFontFamily,
          fontSize = 11.sp,
          lineHeight = 15.sp,
          modifier =
            Modifier.padding(10.dp)
              .verticalScroll(rememberScrollState())
              .horizontalScroll(rememberScrollState()),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          clipboard.setText(AnnotatedString(jsonContent))
          onNotify("JSON copiado al portapapeles")
          onDismiss()
        },
        modifier = Modifier.testTag("copy_json_dialog_btn"),
      ) {
        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Copiar JSON")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cerrar") }
    },
  )
}

@Composable
private fun SyncQueueItemCard(item: SyncQueueItemEntity) {
  val financeColors = LocalFinanceColors.current
  val (badgeBg, badgeText, statusLabel) =
    when (item.syncStatus) {
      SyncStatus.SYNCED -> Triple(financeColors.incomeContainer, financeColors.incomeAccent, "SINCRONIZADO")
      SyncStatus.FAILED -> Triple(financeColors.expenseContainer, financeColors.expenseAccent, "ERROR")
      else -> Triple(financeColors.pendingContainer, financeColors.pendingAccent, "PENDIENTE")
    }

  Surface(
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, financeColors.cardBorder),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
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
            imageVector =
              if (item.syncStatus == SyncStatus.SYNCED) Icons.Rounded.CloudDone
              else Icons.Rounded.CloudQueue,
            contentDescription = null,
            tint = badgeText,
            modifier = Modifier.size(16.dp),
          )
          Text(
            text = "${item.entityType} • ${item.operation}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
          )
        }
        Surface(shape = RoundedCornerShape(8.dp), color = badgeBg) {
          Text(
            text = statusLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = badgeText,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
          )
        }
      }
      Text(
        text = "ID: ${item.entityId} • ${formatDateTime(item.createdAtMillis)}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (!item.lastError.isNullOrBlank()) {
        Text(
          text = "Último error (intento #${item.retryCount}): ${item.lastError}",
          style = MaterialTheme.typography.bodySmall,
          color = financeColors.expenseAccent,
        )
      }
    }
  }
}

private fun formatDateTime(millis: Long): String {
  val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
  return sdf.format(Date(millis))
}
