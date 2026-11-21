package com.example.data.sync

import com.example.data.local.AccountEntity
import com.example.data.local.ClassificationEntity
import com.example.data.local.DetailItemEntity
import com.example.data.local.FinanceDao
import com.example.data.local.FinanceTransactionEntity
import com.example.data.local.MerchantEntity
import com.example.data.local.ScheduledRuleEntity
import com.example.data.local.SyncSettingsEntity
import com.example.data.local.SyncStatus
import com.example.data.local.TransactionTypeEntity
import com.example.data.security.DeviceAuthContext
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

enum class SyncPhase {
  IDLE,
  WAITING_WIFI,
  WAITING_API_CONFIG,
  SYNCING,
  SYNCED,
  REVOKED,
  ERROR,
}

data class SyncRuntimeStatus(
  val phase: SyncPhase = SyncPhase.IDLE,
  val message: String = "Listo para sincronizar",
  val lastSentCount: Int = 0,
  val lastPulledCount: Int = 0,
)

class SyncManager(
  private val dao: FinanceDao,
  private val transport: SyncTransport,
) {
  private val mutex = Mutex()
  private var rateLimitBackoffMs: Long = 0L
  private var nextAllowedAttemptMillis: Long = 0L

  private val _runtimeStatus = MutableStateFlow(SyncRuntimeStatus())
  val runtimeStatus: StateFlow<SyncRuntimeStatus> = _runtimeStatus.asStateFlow()

  suspend fun previewPendingBatchPayload(): SyncBatchPayload {
    val queueItems = dao.getPendingSyncQueueOnce()
    val unsyncedTxs = dao.getPendingTransactionsOnce()
    val classifications = dao.observeAllClassifications().first().associateBy { it.id }
    val details = dao.observeAllDetails().first().associateBy { it.id }
    val accounts = dao.observeAllAccounts().first().associateBy { it.id }
    val settings = ensureDeviceSettings()

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

  private suspend fun ensureDeviceSettings(): SyncSettingsEntity {
    val existing = dao.getSyncSettingsOnce()
    if (existing == null) {
      val created = SyncSettingsEntity(deviceId = UUID.randomUUID().toString())
      dao.upsertSyncSettings(created)
      return created
    }
    if (existing.deviceId.isBlank()) {
      val updated = existing.copy(deviceId = UUID.randomUUID().toString())
      dao.upsertSyncSettings(updated)
      return updated
    }
    return existing
  }

  private fun authContextFrom(settings: SyncSettingsEntity): DeviceAuthContext =
    DeviceAuthContext(
      deviceId = settings.deviceId,
      deviceLabel = settings.deviceLabel,
      enrollmentToken = settings.enrollmentToken,
      isEnrolled = settings.isDeviceEnrolled,
    )

  /**
   * Pushes pending offline records to POST /api/v1/sync/batch with ECDSA P-256 signature,
   * handles partial failures & security error codes (401/403/429), and optionally pulls
   * differential changes from GET /api/v1/sync/changes.
   */
  suspend fun syncPendingIfReady(
    isWifiConnected: Boolean,
    forceAttemptWhenNotWifi: Boolean = false,
    pullAfterPush: Boolean = false,
  ): SyncRuntimeStatus =
    mutex.withLock {
      val settings = ensureDeviceSettings()

      if (settings.isDeviceRevoked) {
        return SyncRuntimeStatus(
          phase = SyncPhase.REVOKED,
          message = "Dispositivo revocado por el servidor. Sincronización automática detenida.",
        ).also { _runtimeStatus.value = it }
      }

      val nowCheck = System.currentTimeMillis()
      if (!forceAttemptWhenNotWifi && nowCheck < nextAllowedAttemptMillis) {
        val waitSec = ((nextAllowedAttemptMillis - nowCheck) / 1000L).coerceAtLeast(1L)
        return SyncRuntimeStatus(
          phase = SyncPhase.ERROR,
          message = "Límite de peticiones alcanzado. Reintentando en ${waitSec}s.",
        ).also { _runtimeStatus.value = it }
      }

      val batch = previewPendingBatchPayload()

      if (batch.totalItems == 0 && !pullAfterPush) {
        return SyncRuntimeStatus(
          phase = SyncPhase.SYNCED,
          message = "Todos los registros están sincronizados",
          lastSentCount = 0,
        ).also { _runtimeStatus.value = it }
      }

      if (!isWifiConnected && !forceAttemptWhenNotWifi) {
        return SyncRuntimeStatus(
          phase = SyncPhase.WAITING_WIFI,
          message = "${batch.totalItems} registros pendientes • Esperando conexión Wi-Fi",
        ).also { _runtimeStatus.value = it }
      }

      if (settings.apiEndpointUrl.isBlank()) {
        return SyncRuntimeStatus(
          phase = SyncPhase.WAITING_API_CONFIG,
          message =
            "Wi-Fi activo • ${batch.totalItems} pendientes • Configura la URL del API para sincronizar",
        ).also { _runtimeStatus.value = it }
      }

      var sentCount = 0
      var activeSettings = settings

      if (batch.totalItems > 0) {
        _runtimeStatus.value =
          SyncRuntimeStatus(
            phase = SyncPhase.SYNCING,
            message = "Enviando ${batch.totalItems} registros firmados vía Wi-Fi...",
          )

        val batchUrl = SyncUrlHelper.syncBatchUrl(activeSettings.apiEndpointUrl)
        val now = System.currentTimeMillis()
        var result =
          transport.postJson(
            endpointUrl = batchUrl,
            jsonPayload = batch.jsonString,
            authContext = authContextFrom(activeSettings),
          )

        // Auto-retry once on REPLAY_DETECTED with fresh nonce + timestamp
        if (result is SyncTransportResult.Failure && result.errorCode == "REPLAY_DETECTED") {
          result =
            transport.postJson(
              endpointUrl = batchUrl,
              jsonPayload = batch.jsonString,
              authContext = authContextFrom(activeSettings),
            )
        }

        when (result) {
          is SyncTransportResult.Success -> {
            rateLimitBackoffMs = 0L
            nextAllowedAttemptMillis = 0L
            val outcome = applyBatchSuccessResponse(batch, result.responseBody, now)
            sentCount = outcome.syncedCount
            val msg =
              if (outcome.failedCount > 0) {
                "Sincronizados ${outcome.syncedCount} registros (${outcome.failedCount} fallaron)"
              } else {
                "Sincronizados ${outcome.syncedCount} registros (HTTP ${result.httpCode})"
              }
            activeSettings =
              activeSettings.copy(
                isDeviceEnrolled = true,
                lastSuccessfulSyncMillis = now,
                lastAttemptMillis = now,
                lastSyncMessage = msg,
              )
            dao.upsertSyncSettings(activeSettings)
            if (!pullAfterPush) {
              return SyncRuntimeStatus(
                phase = if (outcome.failedCount > 0) SyncPhase.ERROR else SyncPhase.SYNCED,
                message = msg,
                lastSentCount = sentCount,
              ).also { _runtimeStatus.value = it }
            }
          }

          is SyncTransportResult.Failure -> {
            return handleSyncFailure(activeSettings, batch, result, now)
          }
        }
      }

      // Pull differential changes from GET /api/v1/sync/changes?since=<millis>
      if (pullAfterPush) {
        return executePullLocked(activeSettings, sentCount)
      }

      return SyncRuntimeStatus(
        phase = SyncPhase.SYNCED,
        message = "Todos los registros están sincronizados",
        lastSentCount = sentCount,
      ).also { _runtimeStatus.value = it }
    }

  suspend fun pullRemoteChanges(
    isWifiConnected: Boolean,
    forceAttemptWhenNotWifi: Boolean = false,
  ): SyncRuntimeStatus =
    mutex.withLock {
      val settings = ensureDeviceSettings()
      if (settings.isDeviceRevoked) {
        return SyncRuntimeStatus(
          phase = SyncPhase.REVOKED,
          message = "Dispositivo revocado por el servidor. Sincronización detenida.",
        ).also { _runtimeStatus.value = it }
      }
      if (!isWifiConnected && !forceAttemptWhenNotWifi) {
        return SyncRuntimeStatus(
          phase = SyncPhase.WAITING_WIFI,
          message = "Esperando conexión Wi-Fi para descargar cambios",
        ).also { _runtimeStatus.value = it }
      }
      if (settings.apiEndpointUrl.isBlank()) {
        return SyncRuntimeStatus(
          phase = SyncPhase.WAITING_API_CONFIG,
          message = "Configura la URL del API para descargar cambios",
        ).also { _runtimeStatus.value = it }
      }
      executePullLocked(settings, lastSentCount = 0)
    }

  private suspend fun executePullLocked(
    settings: SyncSettingsEntity,
    lastSentCount: Int,
  ): SyncRuntimeStatus {
    _runtimeStatus.value =
      SyncRuntimeStatus(
        phase = SyncPhase.SYNCING,
        message = "Descargando cambios desde el servidor...",
        lastSentCount = lastSentCount,
      )

    val url = SyncUrlHelper.syncChangesUrl(settings.apiEndpointUrl, settings.lastSyncServerMillis)
    val now = System.currentTimeMillis()
    var result = transport.getJson(endpointUrl = url, authContext = authContextFrom(settings))
    if (result is SyncTransportResult.Failure && result.errorCode == "REPLAY_DETECTED") {
      result = transport.getJson(endpointUrl = url, authContext = authContextFrom(settings))
    }

    return when (result) {
      is SyncTransportResult.Success -> {
        rateLimitBackoffMs = 0L
        nextAllowedAttemptMillis = 0L
        val pullResult = applyPullChangesResponse(result.responseBody, now)
        val msg =
          if (lastSentCount > 0) {
            "Enviados $lastSentCount • Descargados ${pullResult.appliedCount} cambios"
          } else {
            "Descargados ${pullResult.appliedCount} cambios del servidor"
          }
        dao.upsertSyncSettings(
          settings.copy(
            isDeviceEnrolled = true,
            lastSuccessfulSyncMillis = now,
            lastAttemptMillis = now,
            lastSyncServerMillis = pullResult.serverTimestampMillis ?: settings.lastSyncServerMillis,
            lastSyncMessage = msg,
          )
        )
        SyncRuntimeStatus(
          phase = SyncPhase.SYNCED,
          message = msg,
          lastSentCount = lastSentCount,
          lastPulledCount = pullResult.appliedCount,
        ).also { _runtimeStatus.value = it }
      }

      is SyncTransportResult.Failure -> {
        handleSyncFailure(settings, batch = null, failure = result, now = now)
      }
    }
  }

  private data class BatchApplyOutcome(val syncedCount: Int, val failedCount: Int)

  private suspend fun applyBatchSuccessResponse(
    batch: SyncBatchPayload,
    responseBody: String,
    now: Long,
  ): BatchApplyOutcome {
    val parsedData =
      runCatching { JSONObject(responseBody).optJSONObject("data") }.getOrNull()
    if (parsedData == null) {
      dao.markBatchSyncedAtomic(
        queueIds = batch.includedQueueIds,
        transactionIds = batch.includedTransactionIds,
        syncedAtMillis = now,
      )
      return BatchApplyOutcome(syncedCount = batch.totalItems, failedCount = 0)
    }

    val failedIds = mutableSetOf<String>()
    val failedArray = parsedData.optJSONArray("failedItems")
    if (failedArray != null) {
      for (i in 0 until failedArray.length()) {
        val obj = failedArray.optJSONObject(i) ?: continue
        val fId = obj.optString("id").takeIf { it.isNotBlank() } ?: continue
        failedIds.add(fId)
      }
    }

    if (failedIds.isEmpty()) {
      dao.markBatchSyncedAtomic(
        queueIds = batch.includedQueueIds,
        transactionIds = batch.includedTransactionIds,
        syncedAtMillis = now,
      )
      return BatchApplyOutcome(syncedCount = batch.totalItems, failedCount = 0)
    }

    // Partial failure: mark only non-failed items as SYNCED
    val pendingQueue = dao.getPendingSyncQueueOnce()
    val succeededQueueIds =
      pendingQueue
        .filter { it.id in batch.includedQueueIds && it.entityId !in failedIds }
        .map { it.id }
    val failedQueueIds =
      pendingQueue
        .filter { it.id in batch.includedQueueIds && it.entityId in failedIds }
        .map { it.id }
    val succeededTxIds = batch.includedTransactionIds.filter { it !in failedIds }
    val failedTxIds = batch.includedTransactionIds.filter { it in failedIds }

    dao.markBatchSyncedAtomic(
      queueIds = succeededQueueIds,
      transactionIds = succeededTxIds,
      syncedAtMillis = now,
    )
    if (failedQueueIds.isNotEmpty()) {
      failedQueueIds.chunked(500).forEach { chunk ->
        dao.markSyncQueueItemsFailed(chunk, now, "Fallo parcial reportado por el servidor")
      }
    }
    if (failedTxIds.isNotEmpty()) {
      failedTxIds.chunked(500).forEach { chunk -> dao.markTransactionsFailed(chunk) }
    }
    return BatchApplyOutcome(
      syncedCount = (batch.totalItems - failedIds.size).coerceAtLeast(0),
      failedCount = failedIds.size,
    )
  }

  private data class PullApplyOutcome(val appliedCount: Int, val serverTimestampMillis: Long?)

  private suspend fun applyPullChangesResponse(
    responseBody: String,
    now: Long,
  ): PullApplyOutcome {
    val root = runCatching { JSONObject(responseBody) }.getOrNull() ?: return PullApplyOutcome(0, null)
    val data = root.optJSONObject("data") ?: return PullApplyOutcome(0, null)
    val serverTs = if (data.has("serverTimestampMillis")) data.optLong("serverTimestampMillis") else null
    val changes = data.optJSONObject("changes") ?: return PullApplyOutcome(0, serverTs)

    var count = 0

    changes.optJSONArray("transactionTypes")?.let { arr ->
      val list = mutableListOf<TransactionTypeEntity>()
      for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        list.add(
          TransactionTypeEntity(
            id = o.getString("id"),
            code = o.optString("code", "GASTO"),
            name = o.optString("name", "Gasto"),
            sortOrder = o.optInt("sortOrder", i),
            isDefault = o.optBoolean("default", true),
          )
        )
      }
      if (list.isNotEmpty()) {
        dao.insertTypes(list)
        count += list.size
      }
    }

    changes.optJSONArray("classifications")?.let { arr ->
      val list = mutableListOf<ClassificationEntity>()
      for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        list.add(
          ClassificationEntity(
            id = o.getString("id"),
            typeId = o.getString("typeId"),
            name = o.getString("name"),
            sortOrder = o.optInt("sortOrder", i),
            isActive = o.optBoolean("isActive", true),
            monthlyBudgetCents = if (o.isNull("monthlyBudgetCents")) null else o.optLong("monthlyBudgetCents"),
            isPinned = o.optBoolean("isPinned", false),
            isDefault = o.optBoolean("default", false),
            deletedAtMillis = optDeletedMillis(o),
          )
        )
      }
      if (list.isNotEmpty()) {
        dao.insertClassifications(list)
        count += list.size
      }
    }

    changes.optJSONArray("details")?.let { arr ->
      val list = mutableListOf<DetailItemEntity>()
      for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        list.add(
          DetailItemEntity(
            id = o.getString("id"),
            classificationId = o.getString("classificationId"),
            name = o.getString("name"),
            sortOrder = o.optInt("sortOrder", i),
            isActive = o.optBoolean("isActive", true),
            isPinned = o.optBoolean("isPinned", false),
            isDefault = o.optBoolean("default", false),
            deletedAtMillis = optDeletedMillis(o),
          )
        )
      }
      if (list.isNotEmpty()) {
        dao.insertDetails(list)
        count += list.size
      }
    }

    changes.optJSONArray("accounts")?.let { arr ->
      val list = mutableListOf<AccountEntity>()
      for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        list.add(
          AccountEntity(
            id = o.getString("id"),
            name = o.getString("name"),
            shortLabel = o.optString("shortLabel", o.getString("name")),
            balanceCents = o.optLong("balanceCents", 0L),
            currency = o.optString("currency", "MXN"),
            accountKind = o.optString("accountKind", "DEBIT"),
            sortOrder = o.optInt("sortOrder", i),
            isActive = o.optBoolean("isActive", true),
            isPinned = o.optBoolean("isPinned", false),
            isDefault = o.optBoolean("default", false),
            deletedAtMillis = optDeletedMillis(o),
          )
        )
      }
      if (list.isNotEmpty()) {
        dao.insertAccounts(list)
        count += list.size
      }
    }

    changes.optJSONArray("merchants")?.let { arr ->
      val list = mutableListOf<MerchantEntity>()
      for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        list.add(
          MerchantEntity(
            id = o.getString("id"),
            name = o.getString("name"),
            sortOrder = o.optInt("sortOrder", i),
            isActive = o.optBoolean("isActive", true),
            isPinned = o.optBoolean("isPinned", false),
            isDefault = o.optBoolean("default", false),
            deletedAtMillis = optDeletedMillis(o),
          )
        )
      }
      if (list.isNotEmpty()) {
        dao.insertMerchants(list)
        count += list.size
      }
    }

    changes.optJSONArray("scheduledRules")?.let { arr ->
      val list = mutableListOf<ScheduledRuleEntity>()
      for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        list.add(
          ScheduledRuleEntity(
            id = o.getString("id"),
            title = o.getString("title"),
            typeId = o.getString("typeId"),
            classificationId = o.getString("classificationId"),
            detailId = o.getString("detailId"),
            accountId = o.getString("accountId"),
            merchantName = if (o.isNull("merchantName")) null else o.optString("merchantName"),
            amountCents = o.optLong("amountCents", 0L),
            dayOfMonth = o.optInt("dayOfMonth", 1),
            isEndOfMonth = o.optBoolean("isEndOfMonth", false),
            isActive = o.optBoolean("isActive", true),
            lastConfirmedPeriodKey =
              if (o.isNull("lastConfirmedPeriodKey")) null else o.optString("lastConfirmedPeriodKey"),
            snoozedUntilEpochDay =
              if (o.isNull("snoozedUntilEpochDay")) null else o.optLong("snoozedUntilEpochDay"),
            isDefault = o.optBoolean("default", false),
            deletedAtMillis = optDeletedMillis(o),
          )
        )
      }
      if (list.isNotEmpty()) {
        dao.insertScheduledRules(list)
        count += list.size
      }
    }

    changes.optJSONArray("transactions")?.let { arr ->
      val list = mutableListOf<FinanceTransactionEntity>()
      for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val updated = o.optLong("updatedAtMillis", now)
        list.add(
          FinanceTransactionEntity(
            id = o.getString("id"),
            createdAtMillis = o.optLong("createdAtMillis", updated),
            operationEpochDay = o.optLong("operationEpochDay"),
            fiscalPeriodKey = o.optString("fiscalPeriodKey"),
            fiscalPeriodLabel = o.optString("fiscalPeriodLabel"),
            typeId = o.getString("typeId"),
            classificationId = o.getString("classificationId"),
            detailId = o.getString("detailId"),
            accountId = o.getString("accountId"),
            merchantName = if (o.isNull("merchantName")) null else o.optString("merchantName"),
            amountCents = o.optLong("amountCents"),
            note = if (o.isNull("note")) null else o.optString("note"),
            updatedAtMillis = updated,
            syncStatus = SyncStatus.SYNCED,
            syncedAtMillis = now,
            isDefault = o.optBoolean("default", false),
            deletedAtMillis = optDeletedMillis(o),
          )
        )
      }
      if (list.isNotEmpty()) {
        dao.insertTransactions(list)
        count += list.size
      }
    }

    return PullApplyOutcome(appliedCount = count, serverTimestampMillis = serverTs)
  }

  private fun optDeletedMillis(obj: JSONObject): Long? {
    if (!obj.isNull("deletedAtMillis")) return obj.optLong("deletedAtMillis")
    if (!obj.isNull("deletedAt") && obj.optString("deletedAt").isNotBlank()) {
      return System.currentTimeMillis()
    }
    return null
  }

  private suspend fun handleSyncFailure(
    settings: SyncSettingsEntity,
    batch: SyncBatchPayload?,
    failure: SyncTransportResult.Failure,
    now: Long,
  ): SyncRuntimeStatus {
    val userMessage =
      when (failure.errorCode) {
        "TIMESTAMP_EXPIRED" ->
          "Desfase de reloj detectado (401). Verifica la fecha/hora automática del teléfono."
        "REPLAY_DETECTED" ->
          "Petición rechazada por anti-replay (401). Intenta sincronizar nuevamente."
        "INVALID_SIGNATURE" ->
          "Firma criptográfica inválida (401). Verifica la configuración de llave del dispositivo."
        "DEVICE_REVOKED" ->
          "Dispositivo revocado (403). Se detuvo la sincronización automática."
        "INVALID_ENROLLMENT_TOKEN" ->
          "Token de enrolamiento inválido (403). Ingresa el token válido en Configuración API."
        "RATE_LIMIT_EXCEEDED" -> {
          rateLimitBackoffMs =
            if (rateLimitBackoffMs == 0L) 15_000L else (rateLimitBackoffMs * 2).coerceAtMost(300_000L)
          nextAllowedAttemptMillis = now + rateLimitBackoffMs
          "Límite de peticiones excedido (429). Reintento en ${rateLimitBackoffMs / 1000L}s."
        }
        else -> failure.message
      }

    if (batch != null) {
      if (batch.includedQueueIds.isNotEmpty()) {
        batch.includedQueueIds.chunked(500).forEach { chunk ->
          dao.markSyncQueueItemsFailed(chunk, now, userMessage)
        }
      }
      if (batch.includedTransactionIds.isNotEmpty()) {
        batch.includedTransactionIds.chunked(500).forEach { chunk ->
          dao.markTransactionsFailed(chunk)
        }
      }
    }

    val isRevoked = failure.errorCode == "DEVICE_REVOKED"
    dao.upsertSyncSettings(
      settings.copy(
        isDeviceRevoked = settings.isDeviceRevoked || isRevoked,
        autoSyncOnWifi = if (isRevoked) false else settings.autoSyncOnWifi,
        lastAttemptMillis = now,
        lastSyncMessage = userMessage,
      )
    )

    val phase = if (isRevoked) SyncPhase.REVOKED else SyncPhase.ERROR
    return SyncRuntimeStatus(
      phase = phase,
      message = userMessage,
      lastSentCount = 0,
    ).also { _runtimeStatus.value = it }
  }

  fun refreshIdleStatus(
    isWifiConnected: Boolean,
    pendingCount: Int,
    apiUrlConfigured: Boolean,
    isDeviceRevoked: Boolean = false,
  ) {
    if (_runtimeStatus.value.phase == SyncPhase.SYNCING) return
    val next =
      when {
        isDeviceRevoked ->
          SyncRuntimeStatus(
            phase = SyncPhase.REVOKED,
            message = "Dispositivo revocado • Sincronización automática detenida",
          )
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
