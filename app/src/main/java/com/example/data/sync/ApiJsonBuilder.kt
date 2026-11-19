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
    }

  fun accountToJson(account: AccountEntity): JSONObject =
    JSONObject().apply {
      put("id", account.id)
      put("name", account.name)
      put("shortLabel", account.shortLabel)
      put("accountKind", account.accountKind)
      put("balanceCents", account.balanceCents)
      put("balanceMxn", account.balanceCents / 100.0)
      put("sortOrder", account.sortOrder)
      put("isActive", account.isActive)
      put("isPinned", account.isPinned)
    }

  fun merchantToJson(merchant: MerchantEntity): JSONObject =
    JSONObject().apply {
      put("id", merchant.id)
      put("name", merchant.name)
      put("sortOrder", merchant.sortOrder)
      put("isActive", merchant.isActive)
      put("isPinned", merchant.isPinned)
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
    }

  /**
   * Manual JSON export for user-configured administration data (catalogs, accounts, merchants, rules).
   * Designed as the contract foundation for building future backend REST APIs.
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
                  put("name", t.name)
                  put("sortOrder", t.sortOrder)
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
   * Designed as the contract foundation for building future backend REST APIs.
   */
  fun buildCapturedDataJson(
    transactions: List<FinanceTransactionEntity>,
    classificationsById: Map<Long, ClassificationEntity>,
    detailsById: Map<Long, DetailItemEntity>,
    accountsById: Map<Long, AccountEntity>,
    nowMillis: Long = System.currentTimeMillis(),
  ): String {
    val totalExpenseCents =
      transactions.filter { it.typeId == TransactionTypes.GASTO }.sumOf { it.amountCents }
    val totalIncomeCents =
      transactions.filter { it.typeId == TransactionTypes.INGRESO }.sumOf { it.amountCents }
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
   * Builds the Wi-Fi automatic sync JSON batch payload sent via HTTP POST to the REST API.
   */
  fun buildWifiSyncBatchJson(
    deviceLabel: String,
    pendingQueueItems: List<SyncQueueItemEntity>,
    unsyncedTransactions: List<FinanceTransactionEntity>,
    classificationsById: Map<Long, ClassificationEntity>,
    detailsById: Map<Long, DetailItemEntity>,
    accountsById: Map<Long, AccountEntity>,
    nowMillis: Long = System.currentTimeMillis(),
  ): SyncBatchPayload {
    val txUpsertsArray = JSONArray()
    val txDeletesArray = JSONArray()
    val adminChangesArray = JSONArray()

    val includedQueueIds = pendingQueueItems.map { it.id }
    val includedTxIds = mutableSetOf<String>()

    val deletedTxIds =
      pendingQueueItems
        .filter {
          it.entityType == SyncEntityTypes.TRANSACTION && it.operation == SyncOperations.DELETE
        }
        .map { it.entityId }
        .toSet()

    // 1. Add all unsynced transactions directly from the database (latest state)
    unsyncedTransactions
      .filter { it.id !in deletedTxIds }
      .forEach { tx ->
        includedTxIds.add(tx.id)
        txUpsertsArray.put(
          transactionToJson(
            tx = tx,
            classificationName = classificationsById[tx.classificationId]?.name,
            detailName = detailsById[tx.detailId]?.name,
            accountName = accountsById[tx.accountId]?.name,
            accountShortLabel = accountsById[tx.accountId]?.shortLabel,
          )
        )
      }

    // 2. Process queued transaction deletes and queued admin changes
    pendingQueueItems.forEach { item ->
      if (item.entityType == SyncEntityTypes.TRANSACTION) {
        if (item.operation == SyncOperations.DELETE) {
          txDeletesArray.put(
            JSONObject().apply {
              put("queueId", item.id)
              put("transactionId", item.entityId)
              put("deletedAtMillis", item.createdAtMillis)
              put("payload", runCatching { JSONObject(item.payloadJson) }.getOrNull() ?: JSONObject.NULL)
            }
          )
        } else {
          // If an upsert tx isn't in unsyncedTransactions (e.g. edge case), include queue snapshot
          if (item.entityId !in includedTxIds) {
            includedTxIds.add(item.entityId)
            val parsed = runCatching { JSONObject(item.payloadJson) }.getOrNull()
            if (parsed != null) txUpsertsArray.put(parsed)
          }
        }
      } else {
        adminChangesArray.put(
          JSONObject().apply {
            put("queueId", item.id)
            put("entityType", item.entityType)
            put("entityId", item.entityId)
            put("operation", item.operation)
            put("createdAtMillis", item.createdAtMillis)
            put("data", runCatching { JSONObject(item.payloadJson) }.getOrNull() ?: item.payloadJson)
          }
        )
      }
    }

    val totalItems = txUpsertsArray.length() + txDeletesArray.length() + adminChangesArray.length()
    val root = JSONObject().apply {
      put("schemaVersion", SCHEMA_VERSION)
      put("exportType", "wifi_sync_batch")
      put("app", "Mis Finanzas")
      put("deviceLabel", deviceLabel)
      put("sentAtIso", formatIsoUtc(nowMillis))
      put("sentAtMillis", nowMillis)
      put(
        "batchCounts",
        JSONObject().apply {
          put("capturedTransactions", txUpsertsArray.length())
          put("deletedTransactions", txDeletesArray.length())
          put("adminChanges", adminChangesArray.length())
          put("totalItems", totalItems)
        },
      )
      put("capturedTransactions", txUpsertsArray)
      put("deletedTransactions", txDeletesArray)
      put("adminChanges", adminChangesArray)
    }

    return SyncBatchPayload(
      jsonString = root.toString(2),
      includedQueueIds = includedQueueIds,
      includedTransactionIds = includedTxIds.toList(),
      totalItems = totalItems,
    )
  }
}
