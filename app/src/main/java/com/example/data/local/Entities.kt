package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

object TransactionTypes {
  const val GASTO = "GASTO"
  const val INGRESO = "INGRESO"
}

@Entity(tableName = "transaction_types")
data class TransactionTypeEntity(
  @PrimaryKey val id: String,
  val name: String,
  val sortOrder: Int,
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
  indices = [Index("typeId")],
)
data class ClassificationEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val typeId: String,
  val name: String,
  val sortOrder: Int,
  val isActive: Boolean = true,
  val monthlyBudgetCents: Long? = null,
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
  indices = [Index("classificationId")],
)
data class DetailItemEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val classificationId: Long,
  val name: String,
  val sortOrder: Int,
  val isActive: Boolean = true,
)

@Entity(tableName = "accounts")
data class AccountEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val shortLabel: String,
  val balanceCents: Long = 0L,
  val accountKind: String = "DEBIT", // DEBIT, CASH, VALES, CREDIT, FINTECH
  val sortOrder: Int = 0,
  val isActive: Boolean = true,
)

@Entity(tableName = "merchants")
data class MerchantEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val sortOrder: Int = 0,
  val isActive: Boolean = true,
)

@Entity(tableName = "scheduled_rules")
data class ScheduledRuleEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val title: String,
  val typeId: String,
  val classificationId: Long,
  val detailId: Long,
  val accountId: Long,
  val merchantName: String? = null,
  val amountCents: Long,
  val dayOfMonth: Int, // 1..28, or 31 when isEndOfMonth = true
  val isEndOfMonth: Boolean = false,
  val isActive: Boolean = true,
  val lastConfirmedPeriodKey: String? = null,
  val snoozedUntilEpochDay: Long? = null,
)

@Entity(
  tableName = "finance_transactions",
  indices = [Index("fiscalPeriodKey"), Index("operationEpochDay"), Index("accountId")],
)
data class FinanceTransactionEntity(
  @PrimaryKey val id: String,
  val createdAtMillis: Long,
  val operationEpochDay: Long,
  val fiscalPeriodKey: String,
  val fiscalPeriodLabel: String,
  val typeId: String,
  val classificationId: Long,
  val detailId: Long,
  val accountId: Long,
  val merchantName: String? = null,
  val amountCents: Long,
  val note: String? = null,
)
