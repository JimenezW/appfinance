package com.example.data.repository

import com.example.data.local.AccountEntity
import com.example.data.local.ClassificationEntity
import com.example.data.local.DatabaseSeeder
import com.example.data.local.DetailItemEntity
import com.example.data.local.FinanceDao
import com.example.data.local.FinanceTransactionEntity
import com.example.data.local.MerchantEntity
import com.example.data.local.ScheduledRuleEntity
import com.example.data.local.SyncEntityTypes
import com.example.data.local.SyncOperations
import com.example.data.local.SyncQueueItemEntity
import com.example.data.local.SyncSettingsEntity
import com.example.data.local.SyncStatus
import com.example.data.local.TransactionTypeEntity
import com.example.data.local.TransactionTypes
import com.example.data.sync.ApiJsonBuilder
import com.example.domain.FiscalPeriodCalculator
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

class FinanceRepository(private val dao: FinanceDao) {
  val types: Flow<List<TransactionTypeEntity>> = dao.observeTypes()
  val classifications: Flow<List<ClassificationEntity>> = dao.observeAllClassifications()
  val details: Flow<List<DetailItemEntity>> = dao.observeAllDetails()
  val accounts: Flow<List<AccountEntity>> = dao.observeAllAccounts()
  val merchants: Flow<List<MerchantEntity>> = dao.observeAllMerchants()
  val scheduledRules: Flow<List<ScheduledRuleEntity>> = dao.observeAllScheduledRules()
  val transactions: Flow<List<FinanceTransactionEntity>> = dao.observeAllTransactions()
  val pendingSyncQueue: Flow<List<SyncQueueItemEntity>> = dao.observePendingSyncQueue()
  val allSyncQueue: Flow<List<SyncQueueItemEntity>> = dao.observeAllSyncQueue()
  val syncSettings: Flow<SyncSettingsEntity?> = dao.observeSyncSettings()

  suspend fun ensureSeeded() {
    DatabaseSeeder.seedIfEmpty(dao)
    val existingSettings = dao.getSyncSettingsOnce()
    if (existingSettings == null) {
      dao.upsertSyncSettings(
        SyncSettingsEntity(
          deviceId = UUID.randomUUID().toString(),
          apiEndpointUrl = SyncSettingsEntity.DEFAULT_API_BASE_URL,
        )
      )
    } else {
      val needsDeviceId = existingSettings.deviceId.isBlank()
      val needsDefaultTunnelUrl =
        existingSettings.apiEndpointUrl.isBlank() ||
          existingSettings.apiEndpointUrl.contains(
            "conscious-princess-constitutional-promotions.trycloudflare.com",
            ignoreCase = true,
          )
      if (needsDeviceId || needsDefaultTunnelUrl) {
        dao.upsertSyncSettings(
          existingSettings.copy(
            deviceId =
              if (needsDeviceId) UUID.randomUUID().toString() else existingSettings.deviceId,
            apiEndpointUrl =
              if (needsDefaultTunnelUrl) SyncSettingsEntity.DEFAULT_API_BASE_URL
              else existingSettings.apiEndpointUrl,
          )
        )
      }
    }
  }

  suspend fun resetDatabaseToSeed() {
    DatabaseSeeder.resetAndReseed(dao)
  }

  suspend fun saveSyncSettings(settings: SyncSettingsEntity) {
    val withDeviceId =
      if (settings.deviceId.isBlank()) settings.copy(deviceId = UUID.randomUUID().toString())
      else settings
    dao.upsertSyncSettings(withDeviceId)
  }

  // --- Transactions ---
  suspend fun recordTransaction(tx: FinanceTransactionEntity) {
    val now = System.currentTimeMillis()
    val pendingTx =
      tx.copy(
        id = tx.id.ifBlank { UUID.randomUUID().toString() },
        updatedAtMillis = now,
        syncStatus = SyncStatus.PENDING,
        syncedAtMillis = null,
        isDefault = false,
        deletedAtMillis = null,
      )
    dao.insertTransactionAndUpdateBalance(pendingTx)
    dao.insertSyncQueueItem(
      SyncQueueItemEntity(
        entityType = SyncEntityTypes.TRANSACTION,
        entityId = pendingTx.id,
        operation = SyncOperations.UPSERT,
        payloadJson = ApiJsonBuilder.transactionToJson(pendingTx).toString(),
        createdAtMillis = now,
      )
    )
  }

  suspend fun updateTransaction(
    oldTx: FinanceTransactionEntity,
    newTx: FinanceTransactionEntity,
  ) {
    val now = System.currentTimeMillis()
    val pendingTx =
      newTx.copy(
        updatedAtMillis = now,
        syncStatus = SyncStatus.PENDING,
        syncedAtMillis = null,
      )
    dao.updateTransactionAndReconcileBalance(oldTx, pendingTx)
    dao.insertSyncQueueItem(
      SyncQueueItemEntity(
        entityType = SyncEntityTypes.TRANSACTION,
        entityId = pendingTx.id,
        operation = SyncOperations.UPSERT,
        payloadJson = ApiJsonBuilder.transactionToJson(pendingTx).toString(),
        createdAtMillis = now,
      )
    )
  }

  suspend fun deleteTransaction(tx: FinanceTransactionEntity) {
    val now = System.currentTimeMillis()
    dao.deleteTransactionAndReverseBalance(tx, now)
    dao.insertSyncQueueItem(
      SyncQueueItemEntity(
        entityType = SyncEntityTypes.TRANSACTION,
        entityId = tx.id,
        operation = SyncOperations.DELETE,
        payloadJson = ApiJsonBuilder.transactionToJson(tx).toString(),
        createdAtMillis = now,
      )
    )
  }

  // --- Scheduled Rules ---
  suspend fun confirmScheduledRule(
    rule: ScheduledRuleEntity,
    tx: FinanceTransactionEntity,
    confirmedPeriodKey: String,
  ) {
    val now = System.currentTimeMillis()
    val pendingTx =
      tx.copy(
        id = tx.id.ifBlank { UUID.randomUUID().toString() },
        updatedAtMillis = now,
        syncStatus = SyncStatus.PENDING,
        syncedAtMillis = null,
        isDefault = false,
        deletedAtMillis = null,
      )
    dao.confirmScheduledRuleAtomic(rule, pendingTx, confirmedPeriodKey)
    val updatedRule =
      rule.copy(
        lastConfirmedPeriodKey = confirmedPeriodKey,
        snoozedUntilEpochDay = null,
      )
    dao.insertSyncQueueItem(
      SyncQueueItemEntity(
        entityType = SyncEntityTypes.TRANSACTION,
        entityId = pendingTx.id,
        operation = SyncOperations.UPSERT,
        payloadJson = ApiJsonBuilder.transactionToJson(pendingTx).toString(),
        createdAtMillis = now,
      )
    )
    dao.insertSyncQueueItem(
      SyncQueueItemEntity(
        entityType = SyncEntityTypes.SCHEDULED_RULE,
        entityId = updatedRule.id,
        operation = SyncOperations.UPSERT,
        payloadJson = ApiJsonBuilder.scheduledRuleToJson(updatedRule).toString(),
        createdAtMillis = now,
      )
    )
  }

  suspend fun snoozeScheduledRule(rule: ScheduledRuleEntity, untilEpochDay: Long) {
    val updated = rule.copy(snoozedUntilEpochDay = untilEpochDay)
    dao.updateScheduledRule(updated)
    dao.insertSyncQueueItem(
      SyncQueueItemEntity(
        entityType = SyncEntityTypes.SCHEDULED_RULE,
        entityId = updated.id,
        operation = SyncOperations.UPSERT,
        payloadJson = ApiJsonBuilder.scheduledRuleToJson(updated).toString(),
      )
    )
  }

  suspend fun saveScheduledRule(rule: ScheduledRuleEntity): String {
    val isNew = rule.id.isBlank()
    val saved =
      if (isNew) {
        rule.copy(id = UUID.randomUUID().toString(), isDefault = false, deletedAtMillis = null)
      } else {
        rule.copy(deletedAtMillis = null)
      }
    dao.insertScheduledRule(saved)
    dao.insertSyncQueueItem(
      SyncQueueItemEntity(
        entityType = SyncEntityTypes.SCHEDULED_RULE,
        entityId = saved.id,
        operation = SyncOperations.UPSERT,
        payloadJson = ApiJsonBuilder.scheduledRuleToJson(saved).toString(),
      )
    )
    return saved.id
  }

  suspend fun deleteScheduledRule(rule: ScheduledRuleEntity) {
    val now = System.currentTimeMillis()
    dao.softDeleteScheduledRule(rule.id, now)
    dao.insertSyncQueueItem(
      SyncQueueItemEntity(
        entityType = SyncEntityTypes.SCHEDULED_RULE,
        entityId = rule.id,
        operation = SyncOperations.DELETE,
        payloadJson = ApiJsonBuilder.scheduledRuleToJson(rule).toString(),
        createdAtMillis = now,
      )
    )
  }

  // --- Classifications CRUD ---
  suspend fun saveClassification(item: ClassificationEntity): String {
    val isNew = item.id.isBlank()
    val saved =
      if (isNew) {
        item.copy(id = UUID.randomUUID().toString(), isDefault = false, deletedAtMillis = null)
      } else {
        item.copy(deletedAtMillis = null)
      }
    dao.insertClassification(saved)
    dao.insertSyncQueueItem(
      SyncQueueItemEntity(
        entityType = SyncEntityTypes.CLASSIFICATION,
        entityId = saved.id,
        operation = SyncOperations.UPSERT,
        payloadJson = ApiJsonBuilder.classificationToJson(saved).toString(),
      )
    )
    return saved.id
  }

  suspend fun deleteClassification(item: ClassificationEntity) {
    val now = System.currentTimeMillis()
    dao.softDeleteClassification(item.id, now)
    dao.insertSyncQueueItem(
      SyncQueueItemEntity(
        entityType = SyncEntityTypes.CLASSIFICATION,
        entityId = item.id,
        operation = SyncOperations.DELETE,
        payloadJson = ApiJsonBuilder.classificationToJson(item).toString(),
        createdAtMillis = now,
      )
    )
  }

  // --- Details CRUD ---
  suspend fun saveDetail(item: DetailItemEntity): String {
    val isNew = item.id.isBlank()
    val saved =
      if (isNew) {
        item.copy(id = UUID.randomUUID().toString(), isDefault = false, deletedAtMillis = null)
      } else {
        item.copy(deletedAtMillis = null)
      }
    dao.insertDetail(saved)
    dao.insertSyncQueueItem(
      SyncQueueItemEntity(
        entityType = SyncEntityTypes.DETAIL,
        entityId = saved.id,
        operation = SyncOperations.UPSERT,
        payloadJson = ApiJsonBuilder.detailToJson(saved).toString(),
      )
    )
    return saved.id
  }

  suspend fun deleteDetail(item: DetailItemEntity) {
    val now = System.currentTimeMillis()
    dao.softDeleteDetail(item.id, now)
    dao.insertSyncQueueItem(
      SyncQueueItemEntity(
        entityType = SyncEntityTypes.DETAIL,
        entityId = item.id,
        operation = SyncOperations.DELETE,
        payloadJson = ApiJsonBuilder.detailToJson(item).toString(),
        createdAtMillis = now,
      )
    )
  }

  // --- Accounts CRUD ---
  suspend fun saveAccount(account: AccountEntity): String {
    val isNew = account.id.isBlank()
    val saved =
      if (isNew) {
        account.copy(id = UUID.randomUUID().toString(), isDefault = false, deletedAtMillis = null)
      } else {
        account.copy(deletedAtMillis = null)
      }
    dao.insertAccount(saved)
    dao.insertSyncQueueItem(
      SyncQueueItemEntity(
        entityType = SyncEntityTypes.ACCOUNT,
        entityId = saved.id,
        operation = SyncOperations.UPSERT,
        payloadJson = ApiJsonBuilder.accountToJson(saved).toString(),
      )
    )
    return saved.id
  }

  suspend fun deleteAccount(account: AccountEntity) {
    val now = System.currentTimeMillis()
    dao.softDeleteAccount(account.id, now)
    dao.insertSyncQueueItem(
      SyncQueueItemEntity(
        entityType = SyncEntityTypes.ACCOUNT,
        entityId = account.id,
        operation = SyncOperations.DELETE,
        payloadJson = ApiJsonBuilder.accountToJson(account).toString(),
        createdAtMillis = now,
      )
    )
  }

  // --- Merchants CRUD ---
  suspend fun saveMerchant(merchant: MerchantEntity): String {
    val isNew = merchant.id.isBlank()
    val saved =
      if (isNew) {
        merchant.copy(id = UUID.randomUUID().toString(), isDefault = false, deletedAtMillis = null)
      } else {
        merchant.copy(deletedAtMillis = null)
      }
    dao.insertMerchant(saved)
    dao.insertSyncQueueItem(
      SyncQueueItemEntity(
        entityType = SyncEntityTypes.MERCHANT,
        entityId = saved.id,
        operation = SyncOperations.UPSERT,
        payloadJson = ApiJsonBuilder.merchantToJson(saved).toString(),
      )
    )
    return saved.id
  }

  suspend fun ensureMerchantExists(name: String) {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return
    val existing = dao.findMerchantByName(trimmed)
    if (existing == null) {
      val newMerchant =
        MerchantEntity(
          id = UUID.randomUUID().toString(),
          name = trimmed,
          sortOrder = 999,
          isActive = true,
          isDefault = false,
        )
      dao.insertMerchant(newMerchant)
      dao.insertSyncQueueItem(
        SyncQueueItemEntity(
          entityType = SyncEntityTypes.MERCHANT,
          entityId = newMerchant.id,
          operation = SyncOperations.UPSERT,
          payloadJson = ApiJsonBuilder.merchantToJson(newMerchant).toString(),
        )
      )
    }
  }

  suspend fun deleteMerchant(merchant: MerchantEntity) {
    val now = System.currentTimeMillis()
    dao.softDeleteMerchant(merchant.id, now)
    dao.insertSyncQueueItem(
      SyncQueueItemEntity(
        entityType = SyncEntityTypes.MERCHANT,
        entityId = merchant.id,
        operation = SyncOperations.DELETE,
        payloadJson = ApiJsonBuilder.merchantToJson(merchant).toString(),
        createdAtMillis = now,
      )
    )
  }

  // --- Real Local Backup & API-Ready JSON Exports ---
  fun buildAdminConfigJson(
    types: List<TransactionTypeEntity>,
    classifications: List<ClassificationEntity>,
    details: List<DetailItemEntity>,
    accounts: List<AccountEntity>,
    merchants: List<MerchantEntity>,
    scheduledRules: List<ScheduledRuleEntity>,
  ): String =
    ApiJsonBuilder.buildAdminConfigJson(
      types = types,
      classifications = classifications,
      details = details,
      accounts = accounts,
      merchants = merchants,
      scheduledRules = scheduledRules,
    )

  fun buildCapturedDataJson(
    transactions: List<FinanceTransactionEntity>,
    classificationsById: Map<String, ClassificationEntity>,
    detailsById: Map<String, DetailItemEntity>,
    accountsById: Map<String, AccountEntity>,
  ): String =
    ApiJsonBuilder.buildCapturedDataJson(
      transactions = transactions,
      classificationsById = classificationsById,
      detailsById = detailsById,
      accountsById = accountsById,
    )

  fun buildCsvBackup(
    transactions: List<FinanceTransactionEntity>,
    classificationsById: Map<String, ClassificationEntity>,
    detailsById: Map<String, DetailItemEntity>,
    accountsById: Map<String, AccountEntity>,
  ): String {
    val sb = StringBuilder()
    sb.appendLine(
      "id,fecha_operacion,periodo_fiscal,tipo,clasificacion,detalle,cuenta,comercio,importe_mxn,nota,estado_sync,creado_ms"
    )
    transactions.forEach { tx ->
      val fecha = FiscalPeriodCalculator.formatShortDate(tx.operationEpochDay)
      val tipo = if (TransactionTypes.isIncome(tx.typeId)) "Ingreso" else "Gasto"
      val clasif = classificationsById[tx.classificationId]?.name ?: ""
      val det = detailsById[tx.detailId]?.name ?: ""
      val cta = accountsById[tx.accountId]?.name ?: ""
      val comercio = tx.merchantName ?: ""
      val importe = "%.2f".format(java.util.Locale.US, tx.amountCents / 100.0)
      val nota = tx.note ?: ""
      sb.appendLine(
        listOf(
            tx.id,
            fecha,
            tx.fiscalPeriodLabel,
            tipo,
            clasif,
            det,
            cta,
            comercio,
            importe,
            nota,
            tx.syncStatus,
            tx.createdAtMillis.toString(),
          )
          .joinToString(",") { cell -> "\"${cell.replace("\"", "\"\"")}\"" }
      )
    }
    return sb.toString()
  }

  fun buildJsonBackup(
    transactions: List<FinanceTransactionEntity>,
    accounts: List<AccountEntity>,
    classifications: List<ClassificationEntity>,
    details: List<DetailItemEntity>,
    merchants: List<MerchantEntity>,
    scheduledRules: List<ScheduledRuleEntity>,
  ): String {
    val root = JSONObject()
    root.put("schemaVersion", ApiJsonBuilder.SCHEMA_VERSION)
    root.put("exportType", "full_backup")
    root.put("exportedAtMillis", System.currentTimeMillis())
    root.put("app", "Mis Finanzas")
    root.put(
      "accounts",
      JSONArray().apply { accounts.forEach { put(ApiJsonBuilder.accountToJson(it)) } },
    )
    root.put(
      "classifications",
      JSONArray().apply { classifications.forEach { put(ApiJsonBuilder.classificationToJson(it)) } },
    )
    root.put(
      "details",
      JSONArray().apply { details.forEach { put(ApiJsonBuilder.detailToJson(it)) } },
    )
    root.put(
      "merchants",
      JSONArray().apply { merchants.forEach { put(ApiJsonBuilder.merchantToJson(it)) } },
    )
    root.put(
      "scheduledRules",
      JSONArray().apply { scheduledRules.forEach { put(ApiJsonBuilder.scheduledRuleToJson(it)) } },
    )
    root.put(
      "transactions",
      JSONArray().apply {
        transactions.forEach { put(ApiJsonBuilder.transactionToJson(it)) }
      },
    )
    return root.toString(2)
  }
}
