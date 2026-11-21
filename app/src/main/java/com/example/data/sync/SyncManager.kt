package com.example.data.sync

import com.example.data.local.FinanceDao
import com.example.data.local.SyncSettingsEntity
import com.example.data.local.SyncStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class SyncPhase {
  IDLE,
  WAITING_WIFI,
  WAITING_API_CONFIG,
  SYNCING,
  SYNCED,
  ERROR,
}

data class SyncRuntimeStatus(
  val phase: SyncPhase = SyncPhase.IDLE,
  val message: String = "Listo para sincronizar",
  val lastSentCount: Int = 0,
)

class SyncManager(
  private val dao: FinanceDao,
  private val transport: SyncTransport,
) {
  private val mutex = Mutex()

  private val _runtimeStatus = MutableStateFlow(SyncRuntimeStatus())
  val runtimeStatus: StateFlow<SyncRuntimeStatus> = _runtimeStatus.asStateFlow()

  suspend fun previewPendingBatchPayload(): SyncBatchPayload {
    val queueItems = dao.getPendingSyncQueueOnce()
    val unsyncedTxs = dao.getPendingTransactionsOnce()
    val classifications = dao.observeAllClassifications().first().associateBy { it.id }
    val details = dao.observeAllDetails().first().associateBy { it.id }
    val accounts = dao.observeAllAccounts().first().associateBy { it.id }
    val settings = dao.getSyncSettingsOnce() ?: SyncSettingsEntity()

    return ApiJsonBuilder.buildWifiSyncBatchJson(
      deviceLabel = settings.deviceLabel,
      pendingQueueItems = queueItems,
      unsyncedTransactions = unsyncedTxs,
      classificationsById = classifications,
      detailsById = details,
      accountsById = accounts,
      deviceId = settings.deviceId,
    )
  }

  /**
   * Evaluates current state and sends all pending offline records as JSON to the configured API
   * when connected via Wi-Fi.
   */
  suspend fun syncPendingIfReady(
    isWifiConnected: Boolean,
    forceAttemptWhenNotWifi: Boolean = false,
  ): SyncRuntimeStatus =
    mutex.withLock {
      val settings = dao.getSyncSettingsOnce() ?: SyncSettingsEntity()
      val batch = previewPendingBatchPayload()

      if (batch.totalItems == 0) {
        val status =
          SyncRuntimeStatus(
            phase = SyncPhase.SYNCED,
            message = "Todos los registros están sincronizados",
            lastSentCount = 0,
          )
        _runtimeStatus.value = status
        return status
      }

      if (!isWifiConnected && !forceAttemptWhenNotWifi) {
        val status =
          SyncRuntimeStatus(
            phase = SyncPhase.WAITING_WIFI,
            message = "${batch.totalItems} registros pendientes • Esperando conexión Wi-Fi",
          )
        _runtimeStatus.value = status
        return status
      }

      if (settings.apiEndpointUrl.isBlank()) {
        val status =
          SyncRuntimeStatus(
            phase = SyncPhase.WAITING_API_CONFIG,
            message =
              "Wi-Fi activo • ${batch.totalItems} pendientes • Configura la URL del API para enviar JSON",
          )
        _runtimeStatus.value = status
        return status
      }

      _runtimeStatus.value =
        SyncRuntimeStatus(
          phase = SyncPhase.SYNCING,
          message = "Enviando ${batch.totalItems} registros en JSON vía Wi-Fi...",
        )

      val now = System.currentTimeMillis()
      val result =
        transport.postJson(
          endpointUrl = settings.apiEndpointUrl,
          authToken = settings.apiAuthToken,
          jsonPayload = batch.jsonString,
        )

      return when (result) {
        is SyncTransportResult.Success -> {
          dao.markBatchSyncedAtomic(
            queueIds = batch.includedQueueIds,
            transactionIds = batch.includedTransactionIds,
            syncedAtMillis = now,
          )
          val msg =
            "Sincronizados ${batch.totalItems} registros (HTTP ${result.httpCode})"
          dao.upsertSyncSettings(
            settings.copy(
              lastSuccessfulSyncMillis = now,
              lastAttemptMillis = now,
              lastSyncMessage = msg,
            )
          )
          SyncRuntimeStatus(
            phase = SyncPhase.SYNCED,
            message = msg,
            lastSentCount = batch.totalItems,
          ).also { _runtimeStatus.value = it }
        }

        is SyncTransportResult.Failure -> {
          if (batch.includedQueueIds.isNotEmpty()) {
            batch.includedQueueIds.chunked(500).forEach { chunk ->
              dao.markSyncQueueItemsFailed(chunk, now, result.message)
            }
          }
          if (batch.includedTransactionIds.isNotEmpty()) {
            batch.includedTransactionIds.chunked(500).forEach { chunk ->
              dao.markTransactionsFailed(chunk)
            }
          }
          dao.upsertSyncSettings(
            settings.copy(
              lastAttemptMillis = now,
              lastSyncMessage = result.message,
            )
          )
          SyncRuntimeStatus(
            phase = SyncPhase.ERROR,
            message = result.message,
            lastSentCount = 0,
          ).also { _runtimeStatus.value = it }
        }
      }
    }

  fun refreshIdleStatus(
    isWifiConnected: Boolean,
    pendingCount: Int,
    apiUrlConfigured: Boolean,
  ) {
    if (_runtimeStatus.value.phase == SyncPhase.SYNCING) return
    val next =
      when {
        pendingCount == 0 ->
          SyncRuntimeStatus(
            phase = SyncPhase.SYNCED,
            message =
              if (isWifiConnected) "Wi-Fi conectado • Sin datos pendientes"
              else "Modo offline • Todo sincronizado",
          )
        !isWifiConnected ->
          SyncRuntimeStatus(
            phase = SyncPhase.WAITING_WIFI,
            message = "$pendingCount pendientes • Se enviarán al conectar Wi-Fi",
          )
        !apiUrlConfigured ->
          SyncRuntimeStatus(
            phase = SyncPhase.WAITING_API_CONFIG,
            message = "Wi-Fi conectado • $pendingCount pendientes • Falta configurar URL del API",
          )
        _runtimeStatus.value.phase == SyncPhase.ERROR -> _runtimeStatus.value
        else ->
          SyncRuntimeStatus(
            phase = SyncPhase.IDLE,
            message = "Wi-Fi conectado • $pendingCount pendientes listos para enviar",
          )
      }
    _runtimeStatus.value = next
  }
}
