package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanceDao {
  // --- Transaction Types ---
  @Query("SELECT * FROM transaction_types ORDER BY sortOrder ASC")
  fun observeTypes(): Flow<List<TransactionTypeEntity>>

  @Query("SELECT COUNT(*) FROM transaction_types")
  suspend fun countTypes(): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertTypes(types: List<TransactionTypeEntity>)

  // --- Classifications ---
  @Query("SELECT * FROM classifications ORDER BY typeId ASC, sortOrder ASC, id ASC")
  fun observeAllClassifications(): Flow<List<ClassificationEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertClassification(item: ClassificationEntity): Long

  @Update
  suspend fun updateClassification(item: ClassificationEntity)

  @Delete
  suspend fun deleteClassification(item: ClassificationEntity)

  // --- Detail Items ---
  @Query("SELECT * FROM detail_items ORDER BY classificationId ASC, sortOrder ASC, id ASC")
  fun observeAllDetails(): Flow<List<DetailItemEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertDetail(item: DetailItemEntity): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertDetails(items: List<DetailItemEntity>)

  @Update
  suspend fun updateDetail(item: DetailItemEntity)

  @Delete
  suspend fun deleteDetail(item: DetailItemEntity)

  // --- Accounts ---
  @Query("SELECT * FROM accounts ORDER BY sortOrder ASC, id ASC")
  fun observeAllAccounts(): Flow<List<AccountEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAccount(account: AccountEntity): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAccounts(accounts: List<AccountEntity>)

  @Update
  suspend fun updateAccount(account: AccountEntity)

  @Delete
  suspend fun deleteAccount(account: AccountEntity)

  @Query("UPDATE accounts SET balanceCents = balanceCents + :deltaCents WHERE id = :accountId")
  suspend fun adjustAccountBalance(accountId: Long, deltaCents: Long)

  // --- Merchants ---
  @Query("SELECT * FROM merchants ORDER BY sortOrder ASC, name ASC")
  fun observeAllMerchants(): Flow<List<MerchantEntity>>

  @Query("SELECT * FROM merchants WHERE LOWER(name) = LOWER(:name) LIMIT 1")
  suspend fun findMerchantByName(name: String): MerchantEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertMerchant(merchant: MerchantEntity): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertMerchants(merchants: List<MerchantEntity>)

  @Update
  suspend fun updateMerchant(merchant: MerchantEntity)

  @Delete
  suspend fun deleteMerchant(merchant: MerchantEntity)

  // --- Scheduled Rules ---
  @Query("SELECT * FROM scheduled_rules ORDER BY dayOfMonth ASC, id ASC")
  fun observeAllScheduledRules(): Flow<List<ScheduledRuleEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertScheduledRule(rule: ScheduledRuleEntity): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertScheduledRules(rules: List<ScheduledRuleEntity>)

  @Update
  suspend fun updateScheduledRule(rule: ScheduledRuleEntity)

  @Delete
  suspend fun deleteScheduledRule(rule: ScheduledRuleEntity)

  // --- Transactions ---
  @Query("SELECT * FROM finance_transactions ORDER BY operationEpochDay DESC, createdAtMillis DESC")
  fun observeAllTransactions(): Flow<List<FinanceTransactionEntity>>

  @Query("SELECT * FROM finance_transactions ORDER BY operationEpochDay DESC, createdAtMillis DESC")
  suspend fun getAllTransactionsOnce(): List<FinanceTransactionEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertTransactionRaw(tx: FinanceTransactionEntity)

  @Update
  suspend fun updateTransactionRaw(tx: FinanceTransactionEntity)

  @Delete
  suspend fun deleteTransactionRaw(tx: FinanceTransactionEntity)

  @Query("DELETE FROM finance_transactions")
  suspend fun deleteAllTransactions()

  @Query("DELETE FROM scheduled_rules")
  suspend fun deleteAllScheduledRules()

  @Query("DELETE FROM merchants")
  suspend fun deleteAllMerchants()

  @Query("DELETE FROM accounts")
  suspend fun deleteAllAccounts()

  @Query("DELETE FROM detail_items")
  suspend fun deleteAllDetails()

  @Query("DELETE FROM classifications")
  suspend fun deleteAllClassifications()

  @Query("DELETE FROM transaction_types")
  suspend fun deleteAllTypes()

  // --- Atomic Operations ---
  @Transaction
  suspend fun insertTransactionAndUpdateBalance(tx: FinanceTransactionEntity) {
    insertTransactionRaw(tx)
    val delta = if (tx.typeId == TransactionTypes.INGRESO) tx.amountCents else -tx.amountCents
    adjustAccountBalance(tx.accountId, delta)
  }

  @Transaction
  suspend fun updateTransactionAndReconcileBalance(
    oldTx: FinanceTransactionEntity,
    newTx: FinanceTransactionEntity,
  ) {
    val reverseOld = if (oldTx.typeId == TransactionTypes.INGRESO) -oldTx.amountCents else oldTx.amountCents
    adjustAccountBalance(oldTx.accountId, reverseOld)
    updateTransactionRaw(newTx)
    val applyNew = if (newTx.typeId == TransactionTypes.INGRESO) newTx.amountCents else -newTx.amountCents
    adjustAccountBalance(newTx.accountId, applyNew)
  }

  @Transaction
  suspend fun deleteTransactionAndReverseBalance(tx: FinanceTransactionEntity) {
    val reverse = if (tx.typeId == TransactionTypes.INGRESO) -tx.amountCents else tx.amountCents
    adjustAccountBalance(tx.accountId, reverse)
    deleteTransactionRaw(tx)
  }

  @Transaction
  suspend fun confirmScheduledRuleAtomic(
    rule: ScheduledRuleEntity,
    tx: FinanceTransactionEntity,
    confirmedPeriodKey: String,
  ) {
    insertTransactionAndUpdateBalance(tx)
    updateScheduledRule(
      rule.copy(
        lastConfirmedPeriodKey = confirmedPeriodKey,
        snoozedUntilEpochDay = null,
      )
    )
  }

  @Transaction
  suspend fun clearAllData() {
    deleteAllTransactions()
    deleteAllScheduledRules()
    deleteAllMerchants()
    deleteAllAccounts()
    deleteAllDetails()
    deleteAllClassifications()
    deleteAllTypes()
  }
}
