package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanceDao {
  // --- Transaction Types ---
  @Query("SELECT * FROM transaction_types ORDER BY sortOrder ASC")
  fun observeTypes(): Flow<List<TransactionTypeEntity>>

  @Query("SELECT code FROM transaction_types WHERE id = :typeId LIMIT 1")
  suspend fun getTransactionTypeCodeById(typeId: String): String?

  @Query("SELECT COUNT(*) FROM transaction_types")
  suspend fun countTypes(): Int

  @Upsert
  suspend fun insertTypes(types: List<TransactionTypeEntity>)

  // --- Classifications ---
  @Query(
    "SELECT * FROM classifications WHERE deletedAtMillis IS NULL ORDER BY typeId ASC, sortOrder ASC, name ASC"
  )
  fun observeAllClassifications(): Flow<List<ClassificationEntity>>

  @Upsert
  suspend fun insertClassification(item: ClassificationEntity)

  @Upsert
  suspend fun insertClassifications(items: List<ClassificationEntity>)

  @Update
  suspend fun updateClassification(item: ClassificationEntity)

  @Query("UPDATE classifications SET deletedAtMillis = :deletedAtMillis WHERE id = :id")
  suspend fun softDeleteClassification(id: String, deletedAtMillis: Long)

  @Delete
  suspend fun deleteClassification(item: ClassificationEntity)

  // --- Detail Items ---
  @Query(
    "SELECT * FROM detail_items WHERE deletedAtMillis IS NULL ORDER BY classificationId ASC, sortOrder ASC, name ASC"
  )
  fun observeAllDetails(): Flow<List<DetailItemEntity>>

  @Upsert
  suspend fun insertDetail(item: DetailItemEntity)

  @Upsert
  suspend fun insertDetails(items: List<DetailItemEntity>)

  @Update
  suspend fun updateDetail(item: DetailItemEntity)

  @Query("UPDATE detail_items SET deletedAtMillis = :deletedAtMillis WHERE id = :id")
  suspend fun softDeleteDetail(id: String, deletedAtMillis: Long)

  @Delete
  suspend fun deleteDetail(item: DetailItemEntity)

  // --- Accounts ---
  @Query("SELECT * FROM accounts WHERE deletedAtMillis IS NULL ORDER BY sortOrder ASC, name ASC")
  fun observeAllAccounts(): Flow<List<AccountEntity>>

  @Upsert
  suspend fun insertAccount(account: AccountEntity)

  @Upsert
  suspend fun insertAccounts(accounts: List<AccountEntity>)

  @Update
  suspend fun updateAccount(account: AccountEntity)

  @Query("UPDATE accounts SET deletedAtMillis = :deletedAtMillis WHERE id = :id")
  suspend fun softDeleteAccount(id: String, deletedAtMillis: Long)

  @Delete
  suspend fun deleteAccount(account: AccountEntity)

  @Query("UPDATE accounts SET balanceCents = balanceCents + :deltaCents WHERE id = :accountId")
  suspend fun adjustAccountBalance(accountId: String, deltaCents: Long)

  // --- Merchants ---
  @Query("SELECT * FROM merchants WHERE deletedAtMillis IS NULL ORDER BY sortOrder ASC, name ASC")
  fun observeAllMerchants(): Flow<List<MerchantEntity>>

  @Query(
    "SELECT * FROM merchants WHERE deletedAtMillis IS NULL AND LOWER(name) = LOWER(:name) LIMIT 1"
  )
  suspend fun findMerchantByName(name: String): MerchantEntity?

  @Upsert
  suspend fun insertMerchant(merchant: MerchantEntity)

  @Upsert
  suspend fun insertMerchants(merchants: List<MerchantEntity>)

  @Update
  suspend fun updateMerchant(merchant: MerchantEntity)

  @Query("UPDATE merchants SET deletedAtMillis = :deletedAtMillis WHERE id = :id")
  suspend fun softDeleteMerchant(id: String, deletedAtMillis: Long)

  @Delete
  suspend fun deleteMerchant(merchant: MerchantEntity)

  // --- Scheduled Rules ---
  @Query(
    "SELECT * FROM scheduled_rules WHERE deletedAtMillis IS NULL ORDER BY dayOfMonth ASC, title ASC"
  )
  fun observeAllScheduledRules(): Flow<List<ScheduledRuleEntity>>

  @Upsert
  suspend fun insertScheduledRule(rule: ScheduledRuleEntity)

  @Upsert
  suspend fun insertScheduledRules(rules: List<ScheduledRuleEntity>)

  @Update
  suspend fun updateScheduledRule(rule: ScheduledRuleEntity)

  @Query("UPDATE scheduled_rules SET deletedAtMillis = :deletedAtMillis WHERE id = :id")
  suspend fun softDeleteScheduledRule(id: String, deletedAtMillis: Long)

  @Delete
  suspend fun deleteScheduledRule(rule: ScheduledRuleEntity)

  // --- Transactions ---
  @Query(
    "SELECT * FROM finance_transactions WHERE deletedAtMillis IS NULL ORDER BY operationEpochDay DESC, createdAtMillis DESC"
  )
  fun observeAllTransactions(): Flow<List<FinanceTransactionEntity>>

  @Query(
    "SELECT * FROM finance_transactions WHERE deletedAtMillis IS NULL ORDER BY operationEpochDay DESC, createdAtMillis DESC"
  )
  suspend fun getAllTransactionsOnce(): List<FinanceTransactionEntity>

  @Upsert
  suspend fun insertTransactionRaw(tx: FinanceTransactionEntity)

  @Upsert
  suspend fun insertTransactions(txs: List<FinanceTransactionEntity>)

  @Update
  suspend fun updateTransactionRaw(tx: FinanceTransactionEntity)

  @Query(
    "UPDATE finance_transactions SET deletedAtMillis = :deletedAtMillis, updatedAtMillis = :deletedAtMillis WHERE id = :id"
  )
  suspend fun softDeleteTransactionRaw(id: String, deletedAtMillis: Long)

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

  // --- Sync Queue & Sync Settings ---
  @Query("SELECT * FROM sync_queue WHERE syncStatus != 'SYNCED' ORDER BY createdAtMillis ASC")
  fun observePendingSyncQueue(): Flow<List<SyncQueueItemEntity>>

  @Query("SELECT * FROM sync_queue ORDER BY createdAtMillis DESC LIMIT 100")
  fun observeAllSyncQueue(): Flow<List<SyncQueueItemEntity>>

  @Query("SELECT * FROM sync_queue WHERE syncStatus != 'SYNCED' ORDER BY createdAtMillis ASC")
  suspend fun getPendingSyncQueueOnce(): List<SyncQueueItemEntity>

  @Query(
    "SELECT * FROM finance_transactions WHERE deletedAtMillis IS NULL AND syncStatus != 'SYNCED' ORDER BY createdAtMillis ASC"
  )
  suspend fun getPendingTransactionsOnce(): List<FinanceTransactionEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSyncQueueItem(item: SyncQueueItemEntity): Long

  @Query(
    "UPDATE sync_queue SET syncStatus = 'SYNCED', syncedAtMillis = :syncedAtMillis, lastAttemptMillis = :syncedAtMillis, lastError = NULL WHERE id IN (:ids)"
  )
  suspend fun markSyncQueueItemsSynced(ids: List<Long>, syncedAtMillis: Long)

  @Query(
    "UPDATE sync_queue SET syncStatus = 'FAILED', lastAttemptMillis = :attemptAtMillis, retryCount = retryCount + 1, lastError = :error WHERE id IN (:ids)"
  )
  suspend fun markSyncQueueItemsFailed(ids: List<Long>, attemptAtMillis: Long, error: String)

  @Query(
    "UPDATE finance_transactions SET syncStatus = 'SYNCED', syncedAtMillis = :syncedAtMillis WHERE id IN (:ids)"
  )
  suspend fun markTransactionsSynced(ids: List<String>, syncedAtMillis: Long)

  @Query(
    "UPDATE finance_transactions SET syncStatus = 'FAILED' WHERE id IN (:ids) AND syncStatus != 'SYNCED'"
  )
  suspend fun markTransactionsFailed(ids: List<String>)

  @Query("DELETE FROM sync_queue")
  suspend fun deleteAllSyncQueue()

  @Query("SELECT * FROM sync_settings WHERE id = 1 LIMIT 1")
  fun observeSyncSettings(): Flow<SyncSettingsEntity?>

  @Query("SELECT * FROM sync_settings WHERE id = 1 LIMIT 1")
  suspend fun getSyncSettingsOnce(): SyncSettingsEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertSyncSettings(settings: SyncSettingsEntity)

  @Query("SELECT name FROM classifications WHERE id = :id LIMIT 1")
  suspend fun getClassificationNameById(id: String): String?

  @Query("SELECT name FROM detail_items WHERE id = :id LIMIT 1")
  suspend fun getDetailNameById(id: String): String?

  @Query("SELECT * FROM accounts WHERE deletedAtMillis IS NULL ORDER BY sortOrder ASC, name ASC")
  suspend fun getAllAccountsOnce(): List<AccountEntity>

  private suspend fun isIncomeType(typeId: String): Boolean {
    val dbCode = getTransactionTypeCodeById(typeId)
    return if (dbCode != null) dbCode.equals(TransactionTypes.INGRESO, ignoreCase = true)
    else TransactionTypes.isIncome(typeId)
  }

  private suspend fun computeBalanceDeltasForTx(tx: FinanceTransactionEntity): Map<String, Long> {
    val isInc = isIncomeType(tx.typeId)
    if (isInc) {
      val clsName = getClassificationNameById(tx.classificationId)
      val detName = getDetailNameById(tx.detailId)
      if (
        com.example.domain.WithdrawalTransferRule.isWithdrawalHierarchy(
          typeId = TransactionTypes.INGRESO_UUID,
          classificationName = clsName,
          detailName = detName,
        )
      ) {
        val accounts = getAllAccountsOnce()
        val resolved =
          com.example.domain.WithdrawalTransferRule.resolveWithdrawalAccounts(
            selectedAccountId = tx.accountId,
            accounts = accounts,
          )
        if (resolved != null) {
          return if (resolved.debitAccountId == resolved.cashAccountId) emptyMap()
          else
            mapOf(
              resolved.debitAccountId to -tx.amountCents,
              resolved.cashAccountId to tx.amountCents,
            )
        }
      }
    }
    val delta = if (isInc) tx.amountCents else -tx.amountCents
    return mapOf(tx.accountId to delta)
  }

  // --- Atomic Operations ---
  @Transaction
  suspend fun insertTransactionAndUpdateBalance(tx: FinanceTransactionEntity) {
    insertTransactionRaw(tx)
    computeBalanceDeltasForTx(tx).forEach { (accId, delta) ->
      adjustAccountBalance(accId, delta)
    }
  }

  @Transaction
  suspend fun updateTransactionAndReconcileBalance(
    oldTx: FinanceTransactionEntity,
    newTx: FinanceTransactionEntity,
  ) {
    computeBalanceDeltasForTx(oldTx).forEach { (accId, delta) ->
      adjustAccountBalance(accId, -delta)
    }
    updateTransactionRaw(newTx)
    computeBalanceDeltasForTx(newTx).forEach { (accId, delta) ->
      adjustAccountBalance(accId, delta)
    }
  }

  @Transaction
  suspend fun deleteTransactionAndReverseBalance(
    tx: FinanceTransactionEntity,
    deletedAtMillis: Long = System.currentTimeMillis(),
  ) {
    computeBalanceDeltasForTx(tx).forEach { (accId, delta) ->
      adjustAccountBalance(accId, -delta)
    }
    softDeleteTransactionRaw(tx.id, deletedAtMillis)
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
  suspend fun markBatchSyncedAtomic(
    queueIds: List<Long>,
    transactionIds: List<String>,
    syncedAtMillis: Long,
  ) {
    if (queueIds.isNotEmpty()) {
      queueIds.chunked(500).forEach { chunk ->
        markSyncQueueItemsSynced(chunk, syncedAtMillis)
      }
    }
    if (transactionIds.isNotEmpty()) {
      transactionIds.chunked(500).forEach { chunk ->
        markTransactionsSynced(chunk, syncedAtMillis)
      }
    }
  }

  // --- Budgets & Budget Items ---
  @Query("SELECT * FROM budgets WHERE deletedAtMillis IS NULL ORDER BY fiscalPeriodKey DESC")
  fun observeAllBudgets(): Flow<List<BudgetEntity>>

  @Query(
    "SELECT * FROM budget_items WHERE deletedAtMillis IS NULL ORDER BY fiscalPeriodKey DESC, classificationId ASC"
  )
  fun observeAllBudgetItems(): Flow<List<BudgetItemEntity>>

  @Query("SELECT * FROM budgets WHERE deletedAtMillis IS NULL ORDER BY fiscalPeriodKey DESC")
  suspend fun getAllBudgetsOnce(): List<BudgetEntity>

  @Query("SELECT * FROM budget_items WHERE deletedAtMillis IS NULL")
  suspend fun getAllBudgetItemsOnce(): List<BudgetItemEntity>

  @Query("SELECT * FROM budgets WHERE fiscalPeriodKey = :periodKey LIMIT 1")
  suspend fun getBudgetByPeriodKeyOnce(periodKey: String): BudgetEntity?

  @Query("SELECT * FROM budget_items WHERE budgetId = :budgetId AND deletedAtMillis IS NULL")
  suspend fun getActiveBudgetItemsByBudgetIdOnce(budgetId: String): List<BudgetItemEntity>

  @Upsert
  suspend fun insertBudget(budget: BudgetEntity)

  @Upsert
  suspend fun insertBudgets(budgets: List<BudgetEntity>)

  @Upsert
  suspend fun insertBudgetItem(item: BudgetItemEntity)

  @Upsert
  suspend fun insertBudgetItems(items: List<BudgetItemEntity>)

  @Query(
    "UPDATE budgets SET deletedAtMillis = :deletedAtMillis, updatedAtMillis = :deletedAtMillis WHERE id = :id"
  )
  suspend fun softDeleteBudget(id: String, deletedAtMillis: Long)

  @Query(
    "UPDATE budget_items SET deletedAtMillis = :deletedAtMillis, updatedAtMillis = :deletedAtMillis WHERE id = :id"
  )
  suspend fun softDeleteBudgetItem(id: String, deletedAtMillis: Long)

  @Query(
    "UPDATE budget_items SET deletedAtMillis = :deletedAtMillis, updatedAtMillis = :deletedAtMillis WHERE budgetId = :budgetId AND deletedAtMillis IS NULL"
  )
  suspend fun softDeleteBudgetItemsByBudgetId(budgetId: String, deletedAtMillis: Long)

  @Query("DELETE FROM budget_items")
  suspend fun deleteAllBudgetItems()

  @Query("DELETE FROM budgets")
  suspend fun deleteAllBudgets()

  @Transaction
  suspend fun applyPullChangesAtomic(
    types: List<TransactionTypeEntity>,
    classifications: List<ClassificationEntity>,
    details: List<DetailItemEntity>,
    accounts: List<AccountEntity>,
    merchants: List<MerchantEntity>,
    scheduledRules: List<ScheduledRuleEntity>,
    transactions: List<FinanceTransactionEntity>,
    budgets: List<BudgetEntity> = emptyList(),
    budgetItems: List<BudgetItemEntity> = emptyList(),
  ) {
    if (types.isNotEmpty()) insertTypes(types)
    if (classifications.isNotEmpty()) insertClassifications(classifications)
    if (details.isNotEmpty()) insertDetails(details)
    if (accounts.isNotEmpty()) insertAccounts(accounts)
    if (merchants.isNotEmpty()) insertMerchants(merchants)
    if (scheduledRules.isNotEmpty()) insertScheduledRules(scheduledRules)
    if (transactions.isNotEmpty()) insertTransactions(transactions)
    if (budgets.isNotEmpty()) insertBudgets(budgets)
    if (budgetItems.isNotEmpty()) insertBudgetItems(budgetItems)
  }

  @Transaction
  suspend fun clearAllData() {
    deleteAllBudgetItems()
    deleteAllBudgets()
    deleteAllTransactions()
    deleteAllScheduledRules()
    deleteAllMerchants()
    deleteAllAccounts()
    deleteAllDetails()
    deleteAllClassifications()
    deleteAllTypes()
    deleteAllSyncQueue()
  }
}
