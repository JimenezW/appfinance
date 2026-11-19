package com.example.data.repository

import com.example.data.local.AccountEntity
import com.example.data.local.ClassificationEntity
import com.example.data.local.DatabaseSeeder
import com.example.data.local.DetailItemEntity
import com.example.data.local.FinanceDao
import com.example.data.local.FinanceTransactionEntity
import com.example.data.local.MerchantEntity
import com.example.data.local.ScheduledRuleEntity
import com.example.data.local.TransactionTypeEntity
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

  suspend fun ensureSeeded() {
    DatabaseSeeder.seedIfEmpty(dao)
  }

  suspend fun resetDatabaseToSeed() {
    DatabaseSeeder.resetAndReseed(dao)
  }

  // --- Transactions ---
  suspend fun recordTransaction(tx: FinanceTransactionEntity) {
    dao.insertTransactionAndUpdateBalance(tx)
  }

  suspend fun updateTransaction(
    oldTx: FinanceTransactionEntity,
    newTx: FinanceTransactionEntity,
  ) {
    dao.updateTransactionAndReconcileBalance(oldTx, newTx)
  }

  suspend fun deleteTransaction(tx: FinanceTransactionEntity) {
    dao.deleteTransactionAndReverseBalance(tx)
  }

  // --- Scheduled Rules ---
  suspend fun confirmScheduledRule(
    rule: ScheduledRuleEntity,
    tx: FinanceTransactionEntity,
    confirmedPeriodKey: String,
  ) {
    dao.confirmScheduledRuleAtomic(rule, tx, confirmedPeriodKey)
  }

  suspend fun snoozeScheduledRule(rule: ScheduledRuleEntity, untilEpochDay: Long) {
    dao.updateScheduledRule(rule.copy(snoozedUntilEpochDay = untilEpochDay))
  }

  suspend fun saveScheduledRule(rule: ScheduledRuleEntity) {
    if (rule.id == 0L) dao.insertScheduledRule(rule) else dao.updateScheduledRule(rule)
  }

  suspend fun deleteScheduledRule(rule: ScheduledRuleEntity) {
    dao.deleteScheduledRule(rule)
  }

  // --- Classifications CRUD ---
  suspend fun saveClassification(item: ClassificationEntity): Long {
    return if (item.id == 0L) dao.insertClassification(item)
    else {
      dao.updateClassification(item)
      item.id
    }
  }

  suspend fun deleteClassification(item: ClassificationEntity) {
    dao.deleteClassification(item)
  }

  // --- Details CRUD ---
  suspend fun saveDetail(item: DetailItemEntity): Long {
    return if (item.id == 0L) dao.insertDetail(item)
    else {
      dao.updateDetail(item)
      item.id
    }
  }

  suspend fun deleteDetail(item: DetailItemEntity) {
    dao.deleteDetail(item)
  }

  // --- Accounts CRUD ---
  suspend fun saveAccount(account: AccountEntity): Long {
    return if (account.id == 0L) dao.insertAccount(account)
    else {
      dao.updateAccount(account)
      account.id
    }
  }

  suspend fun deleteAccount(account: AccountEntity) {
    dao.deleteAccount(account)
  }

  // --- Merchants CRUD ---
  suspend fun saveMerchant(merchant: MerchantEntity): Long {
    return if (merchant.id == 0L) dao.insertMerchant(merchant)
    else {
      dao.updateMerchant(merchant)
      merchant.id
    }
  }

  suspend fun ensureMerchantExists(name: String) {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return
    val existing = dao.findMerchantByName(trimmed)
    if (existing == null) {
      dao.insertMerchant(MerchantEntity(name = trimmed, sortOrder = 999, isActive = true))
    }
  }

  suspend fun deleteMerchant(merchant: MerchantEntity) {
    dao.deleteMerchant(merchant)
  }

  // --- Real Local Backup Export (CSV & JSON) ---
  fun buildCsvBackup(
    transactions: List<FinanceTransactionEntity>,
    classificationsById: Map<Long, ClassificationEntity>,
    detailsById: Map<Long, DetailItemEntity>,
    accountsById: Map<Long, AccountEntity>,
  ): String {
    val sb = StringBuilder()
    sb.appendLine(
      "id,fecha_operacion,periodo_fiscal,tipo,clasificacion,detalle,cuenta,comercio,importe_mxn,nota,creado_ms"
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
    root.put("exportedAtMillis", System.currentTimeMillis())
    root.put("app", "Mis Finanzas")
    root.put(
      "accounts",
      JSONArray().apply {
        accounts.forEach { a ->
          put(
            JSONObject().apply {
              put("id", a.id)
              put("name", a.name)
              put("shortLabel", a.shortLabel)
              put("balanceMxn", a.balanceCents / 100.0)
              put("kind", a.accountKind)
              put("isActive", a.isActive)
            }
          )
        }
      },
    )
    root.put(
      "classifications",
      JSONArray().apply {
        classifications.forEach { c ->
          put(
            JSONObject().apply {
              put("id", c.id)
              put("typeId", c.typeId)
              put("name", c.name)
              put("isActive", c.isActive)
              if (c.monthlyBudgetCents != null) {
                put("monthlyBudgetMxn", c.monthlyBudgetCents / 100.0)
              }
            }
          )
        }
      },
    )
    root.put(
      "details",
      JSONArray().apply {
        details.forEach { d ->
          put(
            JSONObject().apply {
              put("id", d.id)
              put("classificationId", d.classificationId)
              put("name", d.name)
              put("isActive", d.isActive)
            }
          )
        }
      },
    )
    root.put(
      "merchants",
      JSONArray().apply {
        merchants.forEach { m ->
          put(
            JSONObject().apply {
              put("id", m.id)
              put("name", m.name)
              put("isActive", m.isActive)
            }
          )
        }
      },
    )
    root.put(
      "scheduledRules",
      JSONArray().apply {
        scheduledRules.forEach { r ->
          put(
            JSONObject().apply {
              put("id", r.id)
              put("title", r.title)
              put("typeId", r.typeId)
              put("amountMxn", r.amountCents / 100.0)
              put("dayOfMonth", r.dayOfMonth)
              put("isEndOfMonth", r.isEndOfMonth)
              put("isActive", r.isActive)
            }
          )
        }
      },
    )
    root.put(
      "transactions",
      JSONArray().apply {
        transactions.forEach { t ->
          put(
            JSONObject().apply {
              put("id", t.id)
              put("createdAtMillis", t.createdAtMillis)
              put("operationDate", FiscalPeriodCalculator.formatShortDate(t.operationEpochDay))
              put("fiscalPeriodKey", t.fiscalPeriodKey)
              put("fiscalPeriodLabel", t.fiscalPeriodLabel)
              put("typeId", t.typeId)
              put("classificationId", t.classificationId)
              put("detailId", t.detailId)
              put("accountId", t.accountId)
              put("merchantName", t.merchantName ?: JSONObject.NULL)
              put("amountMxn", t.amountCents / 100.0)
              put("note", t.note ?: JSONObject.NULL)
            }
          )
        }
      },
    )
    return root.toString(2)
  }
}
