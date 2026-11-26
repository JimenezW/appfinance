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
  val budgets: Flow<List<com.example.data.local.BudgetEntity>> = dao.observeAllBudgets()
  val budgetItems: Flow<List<com.example.data.local.BudgetItemEntity>> = dao.observeAllBudgetItems()
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
          ) ||
          existingSettings.apiEndpointUrl.contains(
            "precisely-preferences-crew-exhibition.trycloudflare.com",
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

  // --- Budgets CRUD & Operations ---
  suspend fun ensureExplicitBudgetForPeriod(
    fiscalPeriodKey: String,
    baselineCategories: List<com.example.domain.BudgetCategoryEvaluation> = emptyList(),
    expectedIncomeCents: Long? = null,
    savingsGoalCents: Long? = null,
    globalExpenseLimitCents: Long? = null,
    notes: String? = null,
  ): com.example.data.local.BudgetEntity {
    val now = System.currentTimeMillis()
    val existing = dao.getBudgetByPeriodKeyOnce(fiscalPeriodKey)
    if (existing != null && existing.deletedAtMillis == null) {
      return existing
    }
    val budgetId = com.example.domain.BudgetEngine.budgetIdForPeriod(fiscalPeriodKey)
    val created =
      com.example.data.local.BudgetEntity(
        id = budgetId,
        fiscalPeriodKey = fiscalPeriodKey,
        expectedIncomeCents = expectedIncomeCents,
        savingsGoalCents = savingsGoalCents,
        globalExpenseLimitCents = globalExpenseLimitCents,
        notes = notes,
        isDefault = false,
        createdAtMillis = existing?.createdAtMillis ?: now,
        updatedAtMillis = now,
        deletedAtMillis = null,
      )
    dao.insertBudget(created)
    dao.insertSyncQueueItem(
      SyncQueueItemEntity(
        entityType = SyncEntityTypes.BUDGET,
        entityId = created.id,
        operation = SyncOperations.UPSERT,
        payloadJson = ApiJsonBuilder.budgetToJson(created).toString(),
        createdAtMillis = now,
      )
    )

    // Materialize inherited/catalog baseline items so editing one category preserves the rest
    val materialized = mutableListOf<com.example.data.local.BudgetItemEntity>()
    baselineCategories.forEach { cat ->
      val rootId = com.example.domain.BudgetEngine.budgetItemId(budgetId, cat.classificationId, null)
      materialized.add(
        com.example.data.local.BudgetItemEntity(
          id = rootId,
          budgetId = budgetId,
          fiscalPeriodKey = fiscalPeriodKey,
          classificationId = cat.classificationId,
          detailId = null,
          amountCents = cat.budgetedCents,
          warningThresholdPercent = cat.warningThresholdPercent,
          rolloverEnabled = cat.rolloverEnabled,
          rolloverCapCents = cat.rolloverCapCents,
          isDefault = false,
          createdAtMillis = now,
          updatedAtMillis = now,
          deletedAtMillis = null,
        )
      )
      cat.subLimits.forEach { sub ->
        val subId =
          com.example.domain.BudgetEngine.budgetItemId(budgetId, cat.classificationId, sub.detailId)
        materialized.add(
          com.example.data.local.BudgetItemEntity(
            id = subId,
            budgetId = budgetId,
            fiscalPeriodKey = fiscalPeriodKey,
            classificationId = cat.classificationId,
            detailId = sub.detailId,
            amountCents = sub.budgetedCents,
            warningThresholdPercent = sub.warningThresholdPercent,
            rolloverEnabled = false,
            rolloverCapCents = null,
            isDefault = false,
            createdAtMillis = now,
            updatedAtMillis = now,
            deletedAtMillis = null,
          )
        )
      }
    }
    if (materialized.isNotEmpty()) {
      dao.insertBudgetItems(materialized)
      materialized.forEach { item ->
        dao.insertSyncQueueItem(
          SyncQueueItemEntity(
            entityType = SyncEntityTypes.BUDGET_ITEM,
            entityId = item.id,
            operation = SyncOperations.UPSERT,
            payloadJson = ApiJsonBuilder.budgetItemToJson(item).toString(),
            createdAtMillis = now,
          )
        )
      }
    }
    return created
  }

  suspend fun saveBudgetCategoryConfig(
    fiscalPeriodKey: String,
    baselineSummary: com.example.domain.BudgetPeriodSummary,
    classificationId: String,
    baseAmountCents: Long,
    warningThresholdPercent: Int,
    rolloverEnabled: Boolean,
    rolloverCapCents: Long?,
    subLimitsByDetailId: Map<String, Long>,
  ) {
    val now = System.currentTimeMillis()
    val budget =
      ensureExplicitBudgetForPeriod(
        fiscalPeriodKey = fiscalPeriodKey,
        baselineCategories = baselineSummary.categories,
        expectedIncomeCents = baselineSummary.expectedIncomeCents.takeIf { it > 0L },
        savingsGoalCents = baselineSummary.savingsGoalCents.takeIf { it > 0L },
        globalExpenseLimitCents = baselineSummary.globalExpenseLimitCents,
        notes = baselineSummary.notes,
      )
    val existingItems =
      dao.getActiveBudgetItemsByBudgetIdOnce(budget.id).filter {
        it.classificationId == classificationId
      }

    // Upsert root classification budget item
    val rootItemId =
      com.example.domain.BudgetEngine.budgetItemId(budget.id, classificationId, null)
    val rootItem =
      com.example.data.local.BudgetItemEntity(
        id = rootItemId,
        budgetId = budget.id,
        fiscalPeriodKey = fiscalPeriodKey,
        classificationId = classificationId,
        detailId = null,
        amountCents = baseAmountCents,
        warningThresholdPercent = warningThresholdPercent.coerceIn(1, 99),
        rolloverEnabled = rolloverEnabled,
        rolloverCapCents = if (rolloverEnabled) rolloverCapCents?.takeIf { it > 0L } else null,
        isDefault = false,
        createdAtMillis = existingItems.firstOrNull { it.detailId == null }?.createdAtMillis ?: now,
        updatedAtMillis = now,
        deletedAtMillis = null,
      )
    dao.insertBudgetItem(rootItem)
    dao.insertSyncQueueItem(
      SyncQueueItemEntity(
        entityType = SyncEntityTypes.BUDGET_ITEM,
        entityId = rootItem.id,
        operation = SyncOperations.UPSERT,
        payloadJson = ApiJsonBuilder.budgetItemToJson(rootItem).toString(),
        createdAtMillis = now,
      )
    )

    // Upsert active sub-limits and soft-delete cleared sub-limits
    val activeDetailIds = subLimitsByDetailId.filterValues { it > 0L }.keys
    subLimitsByDetailId
      .filterValues { it > 0L }
      .forEach { (detailId, amount) ->
        val subId =
          com.example.domain.BudgetEngine.budgetItemId(budget.id, classificationId, detailId)
        val subItem =
          com.example.data.local.BudgetItemEntity(
            id = subId,
            budgetId = budget.id,
            fiscalPeriodKey = fiscalPeriodKey,
            classificationId = classificationId,
            detailId = detailId,
            amountCents = amount,
            warningThresholdPercent = warningThresholdPercent.coerceIn(1, 99),
            rolloverEnabled = false,
            rolloverCapCents = null,
            isDefault = false,
            createdAtMillis =
              existingItems.firstOrNull { it.detailId == detailId }?.createdAtMillis ?: now,
            updatedAtMillis = now,
            deletedAtMillis = null,
          )
        dao.insertBudgetItem(subItem)
        dao.insertSyncQueueItem(
          SyncQueueItemEntity(
            entityType = SyncEntityTypes.BUDGET_ITEM,
            entityId = subItem.id,
            operation = SyncOperations.UPSERT,
            payloadJson = ApiJsonBuilder.budgetItemToJson(subItem).toString(),
            createdAtMillis = now,
          )
        )
      }

    existingItems
      .filter { it.detailId != null && it.detailId !in activeDetailIds }
      .forEach { obsolete ->
        dao.softDeleteBudgetItem(obsolete.id, now)
        dao.insertSyncQueueItem(
          SyncQueueItemEntity(
            entityType = SyncEntityTypes.BUDGET_ITEM,
            entityId = obsolete.id,
            operation = SyncOperations.DELETE,
            payloadJson = ApiJsonBuilder.budgetItemToJson(obsolete).toString(),
            createdAtMillis = now,
          )
        )
      }
  }

  suspend fun deleteBudgetCategory(
    fiscalPeriodKey: String,
    baselineSummary: com.example.domain.BudgetPeriodSummary,
    classificationId: String,
  ) {
    val now = System.currentTimeMillis()
    val budget =
      ensureExplicitBudgetForPeriod(
        fiscalPeriodKey = fiscalPeriodKey,
        baselineCategories = baselineSummary.categories,
        expectedIncomeCents = baselineSummary.expectedIncomeCents.takeIf { it > 0L },
        savingsGoalCents = baselineSummary.savingsGoalCents.takeIf { it > 0L },
        globalExpenseLimitCents = baselineSummary.globalExpenseLimitCents,
        notes = baselineSummary.notes,
      )
    val toRemove =
      dao.getActiveBudgetItemsByBudgetIdOnce(budget.id).filter {
        it.classificationId == classificationId
      }
    toRemove.forEach { item ->
      dao.softDeleteBudgetItem(item.id, now)
      dao.insertSyncQueueItem(
        SyncQueueItemEntity(
          entityType = SyncEntityTypes.BUDGET_ITEM,
          entityId = item.id,
          operation = SyncOperations.DELETE,
          payloadJson = ApiJsonBuilder.budgetItemToJson(item).toString(),
          createdAtMillis = now,
        )
      )
    }
  }

  suspend fun saveBudgetHeader(
    fiscalPeriodKey: String,
    baselineSummary: com.example.domain.BudgetPeriodSummary,
    expectedIncomeCents: Long?,
    savingsGoalCents: Long?,
    globalExpenseLimitCents: Long?,
    notes: String?,
  ) {
    val now = System.currentTimeMillis()
    val existing =
      ensureExplicitBudgetForPeriod(
        fiscalPeriodKey = fiscalPeriodKey,
        baselineCategories = baselineSummary.categories,
        expectedIncomeCents = expectedIncomeCents,
        savingsGoalCents = savingsGoalCents,
        globalExpenseLimitCents = globalExpenseLimitCents,
        notes = notes,
      )
    val updated =
      existing.copy(
        expectedIncomeCents = expectedIncomeCents,
        savingsGoalCents = savingsGoalCents,
        globalExpenseLimitCents = globalExpenseLimitCents,
        notes = notes?.trim()?.takeIf { it.isNotEmpty() },
        updatedAtMillis = now,
        deletedAtMillis = null,
      )
    dao.insertBudget(updated)
    dao.insertSyncQueueItem(
      SyncQueueItemEntity(
        entityType = SyncEntityTypes.BUDGET,
        entityId = updated.id,
        operation = SyncOperations.UPSERT,
        payloadJson = ApiJsonBuilder.budgetToJson(updated).toString(),
        createdAtMillis = now,
      )
    )
  }

  suspend fun cloneBudgetFromSummary(
    targetPeriodKey: String,
    sourceSummary: com.example.domain.BudgetPeriodSummary,
  ): Int {
    val now = System.currentTimeMillis()
    val budgetId = com.example.domain.BudgetEngine.budgetIdForPeriod(targetPeriodKey)
    val existingBudget = dao.getBudgetByPeriodKeyOnce(targetPeriodKey)
    val targetBudget =
      com.example.data.local.BudgetEntity(
        id = budgetId,
        fiscalPeriodKey = targetPeriodKey,
        expectedIncomeCents = sourceSummary.expectedIncomeCents.takeIf { it > 0L },
        savingsGoalCents = sourceSummary.savingsGoalCents.takeIf { it > 0L },
        globalExpenseLimitCents = sourceSummary.globalExpenseLimitCents,
        notes = sourceSummary.notes,
        isDefault = false,
        createdAtMillis = existingBudget?.createdAtMillis ?: now,
        updatedAtMillis = now,
        deletedAtMillis = null,
      )
    dao.insertBudget(targetBudget)
    dao.insertSyncQueueItem(
      SyncQueueItemEntity(
        entityType = SyncEntityTypes.BUDGET,
        entityId = targetBudget.id,
        operation = SyncOperations.UPSERT,
        payloadJson = ApiJsonBuilder.budgetToJson(targetBudget).toString(),
        createdAtMillis = now,
      )
    )

    // Soft-delete existing target items before cloning
    val oldItems = dao.getActiveBudgetItemsByBudgetIdOnce(budgetId)
    oldItems.forEach { item ->
      dao.softDeleteBudgetItem(item.id, now)
      dao.insertSyncQueueItem(
        SyncQueueItemEntity(
          entityType = SyncEntityTypes.BUDGET_ITEM,
          entityId = item.id,
          operation = SyncOperations.DELETE,
          payloadJson = ApiJsonBuilder.budgetItemToJson(item).toString(),
          createdAtMillis = now,
        )
      )
    }

    val clonedItems = mutableListOf<com.example.data.local.BudgetItemEntity>()
    sourceSummary.categories.forEach { cat ->
      val rootId = com.example.domain.BudgetEngine.budgetItemId(budgetId, cat.classificationId, null)
      clonedItems.add(
        com.example.data.local.BudgetItemEntity(
          id = rootId,
          budgetId = budgetId,
          fiscalPeriodKey = targetPeriodKey,
          classificationId = cat.classificationId,
          detailId = null,
          amountCents = cat.budgetedCents,
          warningThresholdPercent = cat.warningThresholdPercent,
          rolloverEnabled = cat.rolloverEnabled,
          rolloverCapCents = cat.rolloverCapCents,
          isDefault = false,
          createdAtMillis = now,
          updatedAtMillis = now,
          deletedAtMillis = null,
        )
      )
      cat.subLimits.forEach { sub ->
        val subId =
          com.example.domain.BudgetEngine.budgetItemId(budgetId, cat.classificationId, sub.detailId)
        clonedItems.add(
          com.example.data.local.BudgetItemEntity(
            id = subId,
            budgetId = budgetId,
            fiscalPeriodKey = targetPeriodKey,
            classificationId = cat.classificationId,
            detailId = sub.detailId,
            amountCents = sub.budgetedCents,
            warningThresholdPercent = sub.warningThresholdPercent,
            rolloverEnabled = false,
            rolloverCapCents = null,
            isDefault = false,
            createdAtMillis = now,
            updatedAtMillis = now,
            deletedAtMillis = null,
          )
        )
      }
    }
    if (clonedItems.isNotEmpty()) {
      dao.insertBudgetItems(clonedItems)
      clonedItems.forEach { item ->
        dao.insertSyncQueueItem(
          SyncQueueItemEntity(
            entityType = SyncEntityTypes.BUDGET_ITEM,
            entityId = item.id,
            operation = SyncOperations.UPSERT,
            payloadJson = ApiJsonBuilder.budgetItemToJson(item).toString(),
            createdAtMillis = now,
          )
        )
      }
    }
    return clonedItems.size
  }

  suspend fun applyBudgetSuggestions(
    targetPeriodKey: String,
    baselineSummary: com.example.domain.BudgetPeriodSummary,
    suggestions: List<com.example.domain.BudgetCategorySuggestion>,
  ): Int {
    if (suggestions.isEmpty()) return 0
    val now = System.currentTimeMillis()
    val budget =
      ensureExplicitBudgetForPeriod(
        fiscalPeriodKey = targetPeriodKey,
        baselineCategories = baselineSummary.categories,
        expectedIncomeCents = baselineSummary.expectedIncomeCents.takeIf { it > 0L },
        savingsGoalCents = baselineSummary.savingsGoalCents.takeIf { it > 0L },
        globalExpenseLimitCents = baselineSummary.globalExpenseLimitCents,
        notes = baselineSummary.notes,
      )
    val existingByClass =
      dao.getActiveBudgetItemsByBudgetIdOnce(budget.id)
        .filter { it.detailId == null }
        .associateBy { it.classificationId }

    val toUpsert =
      suggestions.map { s ->
        val existing = existingByClass[s.classificationId]
        val rootId =
          com.example.domain.BudgetEngine.budgetItemId(budget.id, s.classificationId, null)
        com.example.data.local.BudgetItemEntity(
          id = rootId,
          budgetId = budget.id,
          fiscalPeriodKey = targetPeriodKey,
          classificationId = s.classificationId,
          detailId = null,
          amountCents = s.suggestedCents,
          warningThresholdPercent = existing?.warningThresholdPercent ?: 80,
          rolloverEnabled = existing?.rolloverEnabled ?: false,
          rolloverCapCents = existing?.rolloverCapCents,
          isDefault = false,
          createdAtMillis = existing?.createdAtMillis ?: now,
          updatedAtMillis = now,
          deletedAtMillis = null,
        )
      }
    dao.insertBudgetItems(toUpsert)
    toUpsert.forEach { item ->
      dao.insertSyncQueueItem(
        SyncQueueItemEntity(
          entityType = SyncEntityTypes.BUDGET_ITEM,
          entityId = item.id,
          operation = SyncOperations.UPSERT,
          payloadJson = ApiJsonBuilder.budgetItemToJson(item).toString(),
          createdAtMillis = now,
        )
      )
    }
    return toUpsert.size
  }
}
