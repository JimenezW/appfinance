package com.example.data.sync

import com.example.data.local.AccountEntity
import com.example.data.local.ClassificationEntity
import com.example.data.local.DetailItemEntity
import com.example.data.local.FinanceTransactionEntity
import com.example.data.local.MerchantEntity
import com.example.data.local.ScheduledRuleEntity
import com.example.data.local.SyncEntityTypes
import com.example.data.local.SyncOperations
import com.example.data.local.SyncQueueItemEntity
import com.example.data.local.SyncStatus
import com.example.data.local.TransactionTypeEntity
import com.example.data.local.TransactionTypes
import com.example.domain.FiscalPeriodCalculator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.json.JSONArray
import org.json.JSONObject

data class SyncBatchPayload(
  val jsonString: String,
  val includedQueueIds: List<Long>,
  val includedTransactionIds: List<String>,
  val totalItems: Int,
)

object ApiJsonBuilder {
  const val SCHEMA_VERSION = "1.0"

  fun formatIsoUtc(millis: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(Date(millis))
  }

  fun classificationToJson(item: ClassificationEntity): JSONObject =
    JSONObject().apply {
      put("id", item.id)
      put("typeId", item.typeId)
      put("name", item.name)
      put("sortOrder", item.sortOrder)
      put("isActive", item.isActive)
      put("isPinned", item.isPinned)
      put("monthlyBudgetCents", item.monthlyBudgetCents ?: JSONObject.NULL)
      put(
        "monthlyBudgetMxn",
        item.monthlyBudgetCents?.let { it / 100.0 } ?: JSONObject.NULL,
      )
      put("default", item.isDefault)
    }

  fun detailToJson(
    item: DetailItemEntity,
    classificationName: String? = null,
  ): JSONObject =
    JSONObject().apply {
      put("id", item.id)
      put("classificationId", item.classificationId)
      if (classificationName != null) put("classificationName", classificationName)
      put("name", item.name)
      put("sortOrder", item.sortOrder)
      put("isActive", item.isActive)
      put("isPinned", item.isPinned)
      put("default", item.isDefault)
    }

  fun accountToJson(account: AccountEntity): JSONObject =
    JSONObject().apply {
      put("id", account.id)
      put("name", account.name)
      put("shortLabel", account.shortLabel)
      put("accountKind", account.accountKind)
      put("currency", account.currency)
      put("balanceCents", account.balanceCents)
      put("balanceMxn", account.balanceCents / 100.0)
      put("sortOrder", account.sortOrder)
      put("isActive", account.isActive)
      put("isPinned", account.isPinned)
      put("default", account.isDefault)
    }

  fun merchantToJson(merchant: MerchantEntity): JSONObject =
    JSONObject().apply {
      put("id", merchant.id)
      put("name", merchant.name)
      put("sortOrder", merchant.sortOrder)
      put("isActive", merchant.isActive)
      put("isPinned", merchant.isPinned)
      put("default", merchant.isDefault)
    }

  fun scheduledRuleToJson(rule: ScheduledRuleEntity): JSONObject =
    JSONObject().apply {
      put("id", rule.id)
      put("title", rule.title)
      put("typeId", rule.typeId)
      put("classificationId", rule.classificationId)
      put("detailId", rule.detailId)
      put("accountId", rule.accountId)
      put("merchantName", rule.merchantName ?: JSONObject.NULL)
      put("amountCents", rule.amountCents)
      put("amountMxn", rule.amountCents / 100.0)
      put("dayOfMonth", rule.dayOfMonth)
      put("isEndOfMonth", rule.isEndOfMonth)
      put("isActive", rule.isActive)
      put("lastConfirmedPeriodKey", rule.lastConfirmedPeriodKey ?: JSONObject.NULL)
      put("snoozedUntilEpochDay", rule.snoozedUntilEpochDay ?: JSONObject.NULL)
      put("default", rule.isDefault)
    }

  fun budgetToJson(budget: com.example.data.local.BudgetEntity): JSONObject =
    JSONObject().apply {
      put("id", budget.id)
      put("fiscalPeriodKey", budget.fiscalPeriodKey)
      put("expectedIncomeCents", budget.expectedIncomeCents ?: JSONObject.NULL)
      put("savingsGoalCents", budget.savingsGoalCents ?: JSONObject.NULL)
      put("globalExpenseLimitCents", budget.globalExpenseLimitCents ?: JSONObject.NULL)
      put("notes", budget.notes ?: JSONObject.NULL)
      put("default", budget.isDefault)
      put("createdAtMillis", budget.createdAtMillis)
      put("updatedAtMillis", budget.updatedAtMillis)
    }

  fun budgetItemToJson(item: com.example.data.local.BudgetItemEntity): JSONObject =
    JSONObject().apply {
      put("id", item.id)
      put("budgetId", item.budgetId)
      put("fiscalPeriodKey", item.fiscalPeriodKey)
      put("classificationId", item.classificationId)
      put("detailId", item.detailId ?: JSONObject.NULL)
      put("amountCents", item.amountCents)
      put("amountMxn", item.amountCents / 100.0)
      put("warningThresholdPercent", item.warningThresholdPercent)
      put("rolloverEnabled", item.rolloverEnabled)
      put("rolloverCapCents", item.rolloverCapCents ?: JSONObject.NULL)
      put("default", item.isDefault)
      put("createdAtMillis", item.createdAtMillis)
      put("updatedAtMillis", item.updatedAtMillis)
    }

  fun transactionToJson(
    tx: FinanceTransactionEntity,
    classificationName: String? = null,
    detailName: String? = null,
    accountName: String? = null,
    accountShortLabel: String? = null,
  ): JSONObject =
    JSONObject().apply {
      put("id", tx.id)
      put("createdAtMillis", tx.createdAtMillis)
      put("updatedAtMillis", tx.updatedAtMillis)
      put("operationEpochDay", tx.operationEpochDay)
      put("operationDate", FiscalPeriodCalculator.formatShortDate(tx.operationEpochDay))
      put("fiscalPeriodKey", tx.fiscalPeriodKey)
      put("fiscalPeriodLabel", tx.fiscalPeriodLabel)
      put("typeId", tx.typeId)
      put("classificationId", tx.classificationId)
      if (classificationName != null) put("classificationName", classificationName)
      put("detailId", tx.detailId)
      if (detailName != null) put("detailName", detailName)
      put("accountId", tx.accountId)
      if (accountName != null) put("accountName", accountName)
      if (accountShortLabel != null) put("accountShortLabel", accountShortLabel)
      put("merchantName", tx.merchantName ?: JSONObject.NULL)
      put("amountCents", tx.amountCents)
      put("amountMxn", tx.amountCents / 100.0)
      put("note", tx.note ?: JSONObject.NULL)
      put("syncStatus", tx.syncStatus)
      put("syncedAtMillis", tx.syncedAtMillis ?: JSONObject.NULL)
      put("default", tx.isDefault)
    }

  /**
   * Manual JSON export for user-configured administration data (catalogs, accounts, merchants, rules).
   * Aligned with backFinanceApp v0.4.0 contract (UUIDs, TransactionType.code, default flag).
   */
  fun buildAdminConfigJson(
    types: List<TransactionTypeEntity>,
    classifications: List<ClassificationEntity>,
    details: List<DetailItemEntity>,
    accounts: List<AccountEntity>,
    merchants: List<MerchantEntity>,
    scheduledRules: List<ScheduledRuleEntity>,
    nowMillis: Long = System.currentTimeMillis(),
  ): String {
    val classificationsById = classifications.associateBy { it.id }
    val root = JSONObject()
    root.put("schemaVersion", SCHEMA_VERSION)
    root.put("exportType", "admin_configuration")
    root.put("exportedAtIso", formatIsoUtc(nowMillis))
    root.put("exportedAtMillis", nowMillis)
    root.put("app", "Mis Finanzas")
    root.put(
      "summary",
      JSONObject().apply {
        put("transactionTypesCount", types.size)
        put("classificationsCount", classifications.size)
        put("detailsCount", details.size)
        put("accountsCount", accounts.size)
        put("merchantsCount", merchants.size)
        put("scheduledRulesCount", scheduledRules.size)
      },
    )
    root.put(
      "data",
      JSONObject().apply {
        put(
          "transactionTypes",
          JSONArray().apply {
            types.forEach { t ->
              put(
                JSONObject().apply {
                  put("id", t.id)
                  put("code", t.code)
                  put("name", t.name)
                  put("sortOrder", t.sortOrder)
                  put("default", t.isDefault)
                }
              )
            }
          },
        )
        put(
          "classifications",
          JSONArray().apply { classifications.forEach { put(classificationToJson(it)) } },
        )
        put(
          "details",
          JSONArray().apply {
            details.forEach { d ->
              put(detailToJson(d, classificationsById[d.classificationId]?.name))
            }
          },
        )
        put("accounts", JSONArray().apply { accounts.forEach { put(accountToJson(it)) } })
        put("merchants", JSONArray().apply { merchants.forEach { put(merchantToJson(it)) } })
        put(
          "scheduledRules",
          JSONArray().apply { scheduledRules.forEach { put(scheduledRuleToJson(it)) } },
        )
      },
    )
    return root.toString(2)
  }

  /**
   * Manual JSON export for user-captured financial movements (transactions).
   */
  fun buildCapturedDataJson(
    transactions: List<FinanceTransactionEntity>,
    classificationsById: Map<String, ClassificationEntity>,
    detailsById: Map<String, DetailItemEntity>,
    accountsById: Map<String, AccountEntity>,
    nowMillis: Long = System.currentTimeMillis(),
  ): String {
    val totalExpenseCents =
      transactions.filter { TransactionTypes.isExpense(it.typeId) }.sumOf { it.amountCents }
    val totalIncomeCents =
      transactions.filter { TransactionTypes.isIncome(it.typeId) }.sumOf { it.amountCents }
    val pendingCount = transactions.count { it.syncStatus != SyncStatus.SYNCED }
    val syncedCount = transactions.count { it.syncStatus == SyncStatus.SYNCED }

    val root = JSONObject()
    root.put("schemaVersion", SCHEMA_VERSION)
    root.put("exportType", "captured_transactions")
    root.put("exportedAtIso", formatIsoUtc(nowMillis))
    root.put("exportedAtMillis", nowMillis)
    root.put("app", "Mis Finanzas")
    root.put(
      "summary",
      JSONObject().apply {
        put("totalTransactions", transactions.size)
        put("pendingSyncCount", pendingCount)
        put("syncedCount", syncedCount)
        put("totalExpenseCents", totalExpenseCents)
        put("totalExpenseMxn", totalExpenseCents / 100.0)
        put("totalIncomeCents", totalIncomeCents)
        put("totalIncomeMxn", totalIncomeCents / 100.0)
      },
    )
    root.put(
      "transactions",
      JSONArray().apply {
        transactions.forEach { tx ->
          put(
            transactionToJson(
              tx = tx,
              classificationName = classificationsById[tx.classificationId]?.name,
              detailName = detailsById[tx.detailId]?.name,
              accountName = accountsById[tx.accountId]?.name,
              accountShortLabel = accountsById[tx.accountId]?.shortLabel,
            )
          )
        }
      },
    )
    return root.toString(2)
  }

  /**
   * Builds the Wi-Fi automatic sync JSON batch payload matching POST /api/v1/sync/batch (backFinanceApp v0.4.0).
   */
  fun buildWifiSyncBatchJson(
    deviceLabel: String,
    pendingQueueItems: List<SyncQueueItemEntity>,
    unsyncedTransactions: List<FinanceTransactionEntity>,
    classificationsById: Map<String, ClassificationEntity>,
    detailsById: Map<String, DetailItemEntity>,
    accountsById: Map<String, AccountEntity>,
    deviceId: String = "",
    nowMillis: Long = System.currentTimeMillis(),
  ): SyncBatchPayload {
    val txUpsertsArray = JSONArray()
    val txDeletesArray = JSONArray()

    val classificationsUpserts = JSONArray()
    val detailsUpserts = JSONArray()
    val merchantsUpserts = JSONArray()
    val accountsUpserts = JSONArray()
    val scheduledRulesUpserts = JSONArray()
    val budgetsUpserts = JSONArray()
    val budgetItemsUpserts = JSONArray()

    val deletedClassifications = JSONArray()
    val deletedDetails = JSONArray()
    val deletedMerchants = JSONArray()
    val deletedAccounts = JSONArray()
    val deletedScheduledRules = JSONArray()
    val deletedBudgets = JSONArray()
    val deletedBudgetItems = JSONArray()

    val includedQueueIds = pendingQueueItems.map { it.id }
    val includedTxIds = mutableSetOf<String>()
    val seenDeletedTxIds = mutableSetOf<String>()
    val seenAdminKey = mutableSetOf<String>()

    val deletedTxIds =
      pendingQueueItems
        .filter {
          it.entityType == SyncEntityTypes.TRANSACTION && it.operation == SyncOperations.DELETE
        }
        .map { it.entityId }
        .toSet()

    fun syncTransactionJson(tx: FinanceTransactionEntity): JSONObject =
      JSONObject().apply {
        put("id", tx.id)
        put("operationDate", FiscalPeriodCalculator.formatShortDate(tx.operationEpochDay))
        put("typeId", tx.typeId)
        put("classificationId", tx.classificationId)
        put("detailId", tx.detailId)
        put("accountId", tx.accountId)
        put("merchantName", tx.merchantName ?: JSONObject.NULL)
        put("amountCents", tx.amountCents)
        put("note", tx.note ?: JSONObject.NULL)
      }

    // 1. Add all unsynced transactions directly from the database (latest state)
    unsyncedTransactions
      .filter { it.id !in deletedTxIds }
      .forEach { tx ->
        includedTxIds.add(tx.id)
        txUpsertsArray.put(syncTransactionJson(tx))
      }

    // 2. Process queued transaction deletes and queued admin changes (latest per entity)
    pendingQueueItems.asReversed().forEach { item ->
      if (item.entityType == SyncEntityTypes.TRANSACTION) {
        if (item.operation == SyncOperations.DELETE) {
          if (seenDeletedTxIds.add(item.entityId)) {
            txDeletesArray.put(item.entityId)
          }
        } else if (item.entityId !in includedTxIds && item.entityId !in deletedTxIds) {
          includedTxIds.add(item.entityId)
          val parsed = runCatching { JSONObject(item.payloadJson) }.getOrNull()
          if (parsed != null) {
            txUpsertsArray.put(
              JSONObject().apply {
                put("id", parsed.optString("id", item.entityId))
                put("operationDate", parsed.optString("operationDate"))
                put("typeId", parsed.optString("typeId"))
                put("classificationId", parsed.optString("classificationId"))
                put("detailId", parsed.optString("detailId"))
                put("accountId", parsed.optString("accountId"))
                put("merchantName", if (parsed.isNull("merchantName")) JSONObject.NULL else parsed.opt("merchantName"))
                put("amountCents", parsed.optLong("amountCents"))
                put("note", if (parsed.isNull("note")) JSONObject.NULL else parsed.opt("note"))
              }
            )
          }
        }
      } else {
        val dedupeKey = "${item.entityType}:${item.entityId}"
        if (!seenAdminKey.add(dedupeKey)) return@forEach

        if (item.operation == SyncOperations.DELETE) {
          when (item.entityType) {
            SyncEntityTypes.CLASSIFICATION -> deletedClassifications.put(item.entityId)
            SyncEntityTypes.DETAIL -> deletedDetails.put(item.entityId)
            SyncEntityTypes.MERCHANT -> deletedMerchants.put(item.entityId)
            SyncEntityTypes.ACCOUNT -> deletedAccounts.put(item.entityId)
            SyncEntityTypes.SCHEDULED_RULE -> deletedScheduledRules.put(item.entityId)
            SyncEntityTypes.BUDGET -> deletedBudgets.put(item.entityId)
            SyncEntityTypes.BUDGET_ITEM -> deletedBudgetItems.put(item.entityId)
          }
        } else {
          val parsed = runCatching { JSONObject(item.payloadJson) }.getOrNull() ?: return@forEach
          when (item.entityType) {
            SyncEntityTypes.CLASSIFICATION -> classificationsUpserts.put(parsed)
            SyncEntityTypes.DETAIL -> detailsUpserts.put(parsed)
            SyncEntityTypes.MERCHANT -> merchantsUpserts.put(parsed)
            SyncEntityTypes.ACCOUNT -> accountsUpserts.put(parsed)
            SyncEntityTypes.SCHEDULED_RULE -> scheduledRulesUpserts.put(parsed)
            SyncEntityTypes.BUDGET -> budgetsUpserts.put(parsed)
            SyncEntityTypes.BUDGET_ITEM -> budgetItemsUpserts.put(parsed)
          }
        }
      }
    }

    val adminCount =
      classificationsUpserts.length() +
        detailsUpserts.length() +
        merchantsUpserts.length() +
        accountsUpserts.length() +
        scheduledRulesUpserts.length() +
        budgetsUpserts.length() +
        budgetItemsUpserts.length() +
        deletedClassifications.length() +
        deletedDetails.length() +
        deletedMerchants.length() +
        deletedAccounts.length() +
        deletedScheduledRules.length() +
        deletedBudgets.length() +
        deletedBudgetItems.length()

    val totalItems = txUpsertsArray.length() + txDeletesArray.length() + adminCount
    val root = JSONObject().apply {
      put("schemaVersion", SCHEMA_VERSION)
      put("exportType", "wifi_sync_batch")
      if (deviceId.isNotBlank()) put("deviceId", deviceId)
      put("sentAtMillis", nowMillis)
      put(
        "adminChanges",
        JSONObject().apply {
          put("classifications", classificationsUpserts)
          put("details", detailsUpserts)
          put("merchants", merchantsUpserts)
          put("accounts", accountsUpserts)
          put("scheduledRules", scheduledRulesUpserts)
          put("budgets", budgetsUpserts)
          put("budgetItems", budgetItemsUpserts)
          put(
            "deletedIds",
            JSONObject().apply {
              put("classifications", deletedClassifications)
              put("details", deletedDetails)
              put("merchants", deletedMerchants)
              put("accounts", deletedAccounts)
              put("scheduledRules", deletedScheduledRules)
              put("budgets", deletedBudgets)
              put("budgetItems", deletedBudgetItems)
            },
          )
        },
      )
      put("capturedTransactions", txUpsertsArray)
      put("deletedTransactions", txDeletesArray)
    }

    return SyncBatchPayload(
      jsonString = root.toString(2),
      includedQueueIds = includedQueueIds,
      includedTransactionIds = includedTxIds.toList(),
      totalItems = totalItems,
    )
  }
}
