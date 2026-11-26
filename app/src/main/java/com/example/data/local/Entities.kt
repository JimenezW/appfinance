package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

object TransactionTypes {
  const val GASTO = "GASTO"
  const val INGRESO = "INGRESO"

  // Official backend UUIDv5 identifiers from backFinanceApp (/api/v1/catalogs/admin-config)
  const val GASTO_UUID = "6d404c5b-6d59-5589-a36a-d39fc85137aa"
  const val INGRESO_UUID = "e1135a90-1a21-5d64-84ce-700efa2c8d19"

  fun isIncome(typeIdOrCode: String): Boolean =
    typeIdOrCode == INGRESO_UUID || typeIdOrCode.equals(INGRESO, ignoreCase = true)

  fun isExpense(typeIdOrCode: String): Boolean =
    typeIdOrCode == GASTO_UUID || typeIdOrCode.equals(GASTO, ignoreCase = true)

  fun codeFor(typeIdOrCode: String): String =
    if (isIncome(typeIdOrCode)) INGRESO else GASTO

  fun uuidForCode(code: String): String =
    if (code.equals(INGRESO, ignoreCase = true)) INGRESO_UUID else GASTO_UUID
}

@Entity(
  tableName = "transaction_types",
  indices = [Index(value = ["code"], unique = true)],
)
data class TransactionTypeEntity(
  @PrimaryKey val id: String,
  val code: String, // "GASTO" | "INGRESO"
  val name: String, // "Gasto" | "Ingreso"
  val sortOrder: Int,
  val isDefault: Boolean = true,
)

@Entity(
  tableName = "classifications",
  foreignKeys =
    [
      ForeignKey(
        entity = TransactionTypeEntity::class,
        parentColumns = ["id"],
        childColumns = ["typeId"],
        onDelete = ForeignKey.RESTRICT,
      )
    ],
  indices = [Index("typeId"), Index("deletedAtMillis")],
)
data class ClassificationEntity(
  @PrimaryKey val id: String = UUID.randomUUID().toString(),
  val typeId: String,
  val name: String,
  val sortOrder: Int,
  val isActive: Boolean = true,
  val monthlyBudgetCents: Long? = null,
  val isPinned: Boolean = false,
  val isDefault: Boolean = false,
  val deletedAtMillis: Long? = null,
)

@Entity(
  tableName = "detail_items",
  foreignKeys =
    [
      ForeignKey(
        entity = ClassificationEntity::class,
        parentColumns = ["id"],
        childColumns = ["classificationId"],
        onDelete = ForeignKey.CASCADE,
      )
    ],
  indices = [Index("classificationId"), Index("deletedAtMillis")],
)
data class DetailItemEntity(
  @PrimaryKey val id: String = UUID.randomUUID().toString(),
  val classificationId: String,
  val name: String,
  val sortOrder: Int,
  val isActive: Boolean = true,
  val isPinned: Boolean = false,
  val isDefault: Boolean = false,
  val deletedAtMillis: Long? = null,
)

@Entity(
  tableName = "accounts",
  indices = [Index("deletedAtMillis")],
)
data class AccountEntity(
  @PrimaryKey val id: String = UUID.randomUUID().toString(),
  val name: String,
  val shortLabel: String,
  val balanceCents: Long = 0L,
  val currency: String = "MXN",
  val accountKind: String = "DEBIT", // DEBIT, CASH, VALES, CREDIT, FINTECH
  val sortOrder: Int = 0,
  val isActive: Boolean = true,
  val isPinned: Boolean = false,
  val isDefault: Boolean = false,
  val deletedAtMillis: Long? = null,
)

@Entity(
  tableName = "merchants",
  indices = [Index("deletedAtMillis")],
)
data class MerchantEntity(
  @PrimaryKey val id: String = UUID.randomUUID().toString(),
  val name: String,
  val sortOrder: Int = 0,
  val isActive: Boolean = true,
  val isPinned: Boolean = false,
  val isDefault: Boolean = false,
  val deletedAtMillis: Long? = null,
)

@Entity(
  tableName = "scheduled_rules",
  indices = [Index("deletedAtMillis")],
)
data class ScheduledRuleEntity(
  @PrimaryKey val id: String = UUID.randomUUID().toString(),
  val title: String,
  val typeId: String,
  val classificationId: String,
  val detailId: String,
  val accountId: String,
  val merchantName: String? = null,
  val amountCents: Long,
  val dayOfMonth: Int, // 1..28, or 31 when isEndOfMonth = true
  val isEndOfMonth: Boolean = false,
  val isActive: Boolean = true,
  val lastConfirmedPeriodKey: String? = null,
  val snoozedUntilEpochDay: Long? = null,
  val isDefault: Boolean = false,
  val deletedAtMillis: Long? = null,
)

object SyncStatus {
  const val PENDING = "PENDING"
  const val SYNCED = "SYNCED"
  const val FAILED = "FAILED"
}

object SyncEntityTypes {
  const val TRANSACTION = "TRANSACTION"
  const val CLASSIFICATION = "CLASSIFICATION"
  const val DETAIL = "DETAIL"
  const val ACCOUNT = "ACCOUNT"
  const val MERCHANT = "MERCHANT"
  const val SCHEDULED_RULE = "SCHEDULED_RULE"
  const val BUDGET = "BUDGET"
  const val BUDGET_ITEM = "BUDGET_ITEM"
}

object SyncOperations {
  const val UPSERT = "UPSERT"
  const val DELETE = "DELETE"
}

@Entity(
  tableName = "budgets",
  indices = [Index(value = ["fiscalPeriodKey"], unique = true), Index("deletedAtMillis")],
)
data class BudgetEntity(
  @PrimaryKey val id: String,
  val fiscalPeriodKey: String,
  val expectedIncomeCents: Long? = null,
  val savingsGoalCents: Long? = null,
  val globalExpenseLimitCents: Long? = null,
  val notes: String? = null,
  val isDefault: Boolean = false,
  val createdAtMillis: Long = System.currentTimeMillis(),
  val updatedAtMillis: Long = createdAtMillis,
  val deletedAtMillis: Long? = null,
)

@Entity(
  tableName = "budget_items",
  foreignKeys =
    [
      ForeignKey(
        entity = BudgetEntity::class,
        parentColumns = ["id"],
        childColumns = ["budgetId"],
        onDelete = ForeignKey.CASCADE,
      )
    ],
  indices =
    [
      Index("budgetId"),
      Index("fiscalPeriodKey"),
      Index("classificationId"),
      Index("deletedAtMillis"),
    ],
)
data class BudgetItemEntity(
  @PrimaryKey val id: String,
  val budgetId: String,
  val fiscalPeriodKey: String,
  val classificationId: String,
  val detailId: String? = null,
  val amountCents: Long,
  val warningThresholdPercent: Int = 80,
  val rolloverEnabled: Boolean = false,
  val rolloverCapCents: Long? = null,
  val isDefault: Boolean = false,
  val createdAtMillis: Long = System.currentTimeMillis(),
  val updatedAtMillis: Long = createdAtMillis,
  val deletedAtMillis: Long? = null,
)

@Entity(
  tableName = "finance_transactions",
  indices =
    [
      Index("fiscalPeriodKey"),
      Index("operationEpochDay"),
      Index("accountId"),
      Index("deletedAtMillis"),
    ],
)
data class FinanceTransactionEntity(
  @PrimaryKey val id: String = UUID.randomUUID().toString(),
  val createdAtMillis: Long,
  val operationEpochDay: Long,
  val fiscalPeriodKey: String,
  val fiscalPeriodLabel: String,
  val typeId: String,
  val classificationId: String,
  val detailId: String,
  val accountId: String,
  val merchantName: String? = null,
  val amountCents: Long,
  val note: String? = null,
  val updatedAtMillis: Long = createdAtMillis,
  val syncStatus: String = SyncStatus.PENDING,
  val syncedAtMillis: Long? = null,
  val isDefault: Boolean = false,
  val deletedAtMillis: Long? = null,
)

@Entity(
  tableName = "sync_queue",
  indices = [Index("syncStatus"), Index("createdAtMillis")],
)
data class SyncQueueItemEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val entityType: String,
  val entityId: String,
  val operation: String,
  val payloadJson: String,
  val syncStatus: String = SyncStatus.PENDING,
  val createdAtMillis: Long = System.currentTimeMillis(),
  val lastAttemptMillis: Long? = null,
  val syncedAtMillis: Long? = null,
  val retryCount: Int = 0,
  val lastError: String? = null,
)

@Entity(tableName = "sync_settings")
data class SyncSettingsEntity(
  @PrimaryKey val id: Int = 1,
  val deviceId: String = "",
  val apiEndpointUrl: String = DEFAULT_API_BASE_URL,
  val apiAuthToken: String = "",
  val enrollmentToken: String = "",
  val isDeviceEnrolled: Boolean = false,
  val isDeviceRevoked: Boolean = false,
  val autoSyncOnWifi: Boolean = true,
  val deviceLabel: String = "Android",
  val lastSuccessfulSyncMillis: Long? = null,
  val lastAttemptMillis: Long? = null,
  val lastSyncServerMillis: Long = 0L,
  val lastSyncMessage: String? = null,
) {
  companion object {
    const val DEFAULT_API_BASE_URL =
      "https://soon-diamonds-writing-hundred.trycloudflare.com"
  }
}
