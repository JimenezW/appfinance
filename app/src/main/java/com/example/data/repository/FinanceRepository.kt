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
import com.example.data.sync.ApiJsonBuilder
import com.example.domain.FiscalPeriodCalculator
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
    if (dao.getSyncSettingsOnce() == null) {
      dao.upsertSyncSettings(SyncSettingsEntity())
    }
  }

  suspend fun resetDatabaseToSeed() {
    DatabaseSeeder.resetAndReseed(dao)
  }

  suspend fun saveSyncSettings(settings: SyncSettingsEntity) {
    dao.upsertSyncSettings(settings)
  }

  // --- Transactions ---
  suspend fun recordTransaction(tx: FinanceTransactionEntity) {
    val now = System.currentTimeMillis()
    val pendingTx =
      tx.copy(
        updatedAtMillis = now,
        syncStatus = SyncStatus.PENDING,
        syncedAtMillis = null,
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
    dao.deleteTransactionAndReverseBalance(tx)
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
        updatedAtMillis = now,
        syncStatus = SyncStatus.PENDING,
        syncedAtMillis = null,
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
        entityId = updatedRule.id.toString(),
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
        entityId = updated.id.toString(),
        operation = SyncOperations.UPSERT,
        payloadJson = ApiJsonBuilder.scheduledRuleToJson(updated).toString(),
      )
    )
  }

  suspend fun saveScheduledRule(rule: ScheduledRuleEntity) {
    val savedId =
      if (rule.id == 0L) dao.insertScheduledRule(rule)
      else {
        dao.updateScheduledRule(rule)
        rule.id
      }
    val saved = rule.copy(id = savedId)
    dao.insertSyncQueueItem(
      SyncQueueItemEntity(
        entityType = SyncEntityTypes.SCHEDULED_RULE,
        entityId = saved.id.toString(),
        operation = SyncOperations.UPSERT,
        payloadJson = ApiJsonBuilder.scheduledRuleToJson(saved).toString(),
      )
    )
  }

  suspend fun deleteScheduledRule(rule: ScheduledRuleEntity) {
    dao.deleteScheduledRule(rule)
    dao.insertSyncQueueItem(
      SyncQueueItemEntity(
        entityType = SyncEntityTypes.SCHEDULED_RULE,
        entityId = rule.id.toString(),
        operation = SyncOperations.DELETE,
        payloadJson = ApiJsonBuilder.scheduledRuleToJson(rule).toString(),
      )
    )
  }

  // --- Classifications CRUD ---
  suspend fun saveClassification(item: ClassificationEntity): Long {
    val savedId =
      if (item.id == 0L) dao.insertClassification(item)
      else {
        dao.updateClassification(item)
        item.id
      }
    val saved = item.copy(id = savedId)
    dao.insertSyncQueueItem(
      SyncQueueItemEntity(
        entityType = SyncEntityTypes.CLASSIFICATION,
        entityId = saved.id.toString(),
        operation = SyncOperations.UPSERT,
        payloadJson = ApiJsonBuilder.classificationToJson(saved).toString(),
      )
    )
    return savedId
  }

  suspend fun deleteClassification(item: ClassificationEntity) {
    dao.deleteClassification(item)
    dao.insertSyncQueueItem(
      SyncQueueItemEntity(
        entityType = SyncEntityTypes.CLASSIFICATION,
        entityId = item.id.toString(),
        operation = SyncOperations.DELETE,
        payloadJson = ApiJsonBuilder.classificationToJson(item).toString(),
      )
    )
  }

  // --- Details CRUD ---
  suspend fun saveDetail(item: DetailItemEntity): Long {
    val savedId =
      if (item.id == 0L) dao.insertDetail(item)
      else {
        dao.updateDetail(item)
        item.id
      }
    val saved = item.copy(id = savedId)
    dao.insertSyncQueueItem(
      SyncQueueItemEntity(
        entityType = SyncEntityTypes.DETAIL,
        entityId = saved.id.toString(),
        operation = SyncOperations.UPSERT,
        payloadJson = ApiJsonBuilder.detailToJson(saved).toString(),
      )
    )
    return savedId
  }

  suspend fun deleteDetail(item: DetailItemEntity) {
    dao.deleteDetail(item)
    dao.insertSyncQueueItem(
      SyncQueueItemEntity(
        entityType = SyncEntityTypes.DETAIL,
        entityId = item.id.toString(),
        operation = SyncOperations.DELETE,
        payloadJson = ApiJsonBuilder.detailToJson(item).toString(),
      )
    )
  }

  // --- Accounts CRUD ---
  suspend fun saveAccount(account: AccountEntity): Long {
    val savedId =
      if (account.id == 0L) dao.insertAccount(account)
      else {
        dao.updateAccount(account)
        account.id
      }
    val saved = account.copy(id = savedId)
    dao.insertSyncQueueItem(
      SyncQueueItemEntity(
        entityType = SyncEntityTypes.ACCOUNT,
        entityId = saved.id.toString(),
        operation = SyncOperations.UPSERT,
        payloadJson = ApiJsonBuilder.accountToJson(saved).toString(),
      )
    )
    return savedId
  }

  suspend fun deleteAccount(account: AccountEntity) {
    dao.deleteAccount(account)
    dao.insertSyncQueueItem(
      SyncQueueItemEntity(
        entityType = SyncEntityTypes.ACCOUNT,
        entityId = account.id.toString(),
        operation = SyncOperations.DELETE,
        payloadJson = ApiJsonBuilder.accountToJson(account).toString(),
      )
    )
  }

  // --- Merchants CRUD ---
  suspend fun saveMerchant(merchant: MerchantEntity): Long {
    val savedId =
      if (merchant.id == 0L) dao.insertMerchant(merchant)
      else {
        dao.updateMerchant(merchant)
        merchant.id
      }
    val saved = merchant.copy(id = savedId)
    dao.insertSyncQueueItem(
      SyncQueueItemEntity(
        entityType = SyncEntityTypes.MERCHANT,
        entityId = saved.id.toString(),
        operation = SyncOperations.UPSERT,
        payloadJson = ApiJsonBuilder.merchantToJson(saved).toString(),
      )
    )
    return savedId
  }

  suspend fun ensureMerchantExists(name: String) {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return
    val existing = dao.findMerchantByName(trimmed)
    if (existing == null) {
      val newMerchant = MerchantEntity(name = trimmed, sortOrder = 999, isActive = true)
      val savedId = dao.insertMerchant(newMerchant)
      dao.insertSyncQueueItem(
        SyncQueueItemEntity(
          entityType = SyncEntityTypes.MERCHANT,
          entityId = savedId.toString(),
          operation = SyncOperations.UPSERT,
          payloadJson = ApiJsonBuilder.merchantToJson(newMerchant.copy(id = savedId)).toString(),
        )
      )
    }
  }

  suspend fun deleteMerchant(merchant: MerchantEntity) {
    dao.deleteMerchant(merchant)
    dao.insertSyncQueueItem(
      SyncQueueItemEntity(
        entityType = SyncEntityTypes.MERCHANT,
        entityId = merchant.id.toString(),
        operation = SyncOperations.DELETE,
        payloadJson = ApiJsonBuilder.merchantToJson(merchant).toString(),
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
    classificationsById: Map<Long, ClassificationEntity>,
    detailsById: Map<Long, DetailItemEntity>,
    accountsById: Map<Long, AccountEntity>,
  ): String =
    ApiJsonBuilder.buildCapturedDataJson(
      transactions = transactions,
      classificationsById = classificationsById,
      detailsById = detailsById,
      accountsById = accountsById,
    )

  fun buildCsvBackup(
    transactions: List<FinanceTransactionEntity>,
    classificationsById: Map<Long, ClassificationEntity>,
    detailsById: Map<Long, DetailItemEntity>,
    accountsById: Map<Long, AccountEntity>,
  ): String {
    val sb = StringBuilder()
    sb.appendLine(
      "id,fecha_operacion,periodo_fiscal,tipo,clasificacion,detalle,cuenta,comercio,importe_mxn,nota,estado_sync,creado_ms"
    )
    transactions.forEach { tx ->
      val fecha = FiscalPeriodCalculator.formatShortDate(tx.operationEpochDay)
      val tipo = if (tx.typeId == "INGRESO") "Ingreso" else "Gasto"
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
    root.put("accounts", JSONArray().apply { accounts.forEach { put(ApiJsonBuilder.accountToJson(it)) } })
    root.put(
      "classifications",
      JSONArray().apply { classifications.forEach { put(ApiJsonBuilder.classificationToJson(it)) } },
    )
    root.put("details", JSONArray().apply { details.forEach { put(ApiJsonBuilder.detailToJson(it)) } })
    root.put("merchants", JSONArray().apply { merchants.forEach { put(ApiJsonBuilder.merchantToJson(it)) } })
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
