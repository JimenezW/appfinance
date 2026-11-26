package com.example.domain

import com.example.data.local.AccountEntity
import com.example.data.local.ClassificationEntity
import com.example.data.local.DetailItemEntity
import com.example.data.local.FinanceTransactionEntity
import com.example.data.local.TransactionTypes
import java.text.Normalizer
import java.util.Locale

data class WithdrawalAccountResolution(
  val debitAccountId: String,
  val cashAccountId: String,
)

/**
 * Implements the "Regla de Retiro (Débito -> Efectivo)" business rule:
 * - Triggered when a transaction matches hierarchy: Ingreso -> Otro -> Retiro
 *   (case-insensitive and accent-insensitive).
 * - Effects:
 *   - DEBIT account: -amountCents
 *   - CASH account: +amountCents
 *   - Net worth delta: 0
 * - Excluded from periodIncomeCents, incomeBreakdown, and periodNetBalanceCents (netCashFlowCents).
 */
object WithdrawalTransferRule {

  fun normalizeToken(value: String?): String {
    if (value.isNullOrBlank()) return ""
    val nfd = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
    return nfd.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").lowercase(Locale.ROOT)
  }

  fun isWithdrawalHierarchy(
    typeId: String,
    classificationName: String?,
    detailName: String?,
  ): Boolean {
    if (!TransactionTypes.isIncome(typeId)) return false
    return normalizeToken(classificationName) == "otro" &&
      normalizeToken(detailName) == "retiro"
  }

  fun isWithdrawalTransaction(
    tx: FinanceTransactionEntity,
    classificationsById: Map<String, ClassificationEntity>,
    detailsById: Map<String, DetailItemEntity>,
  ): Boolean {
    val clsName = classificationsById[tx.classificationId]?.name
    val detName = detailsById[tx.detailId]?.name
    return isWithdrawalHierarchy(tx.typeId, clsName, detName)
  }

  /**
   * Resolves the pair (debitAccountId, cashAccountId) affected by a withdrawal.
   * Returns null if the selected account is invalid (e.g. VALES, CREDIT, FINTECH)
   * or if either the DEBIT or CASH account is missing.
   */
  fun resolveWithdrawalAccounts(
    selectedAccountId: String,
    accounts: List<AccountEntity>,
  ): WithdrawalAccountResolution? {
    val selected = accounts.firstOrNull { it.id == selectedAccountId } ?: return null
    val sortedActive =
      accounts
        .filter { it.isActive }
        .sortedWith(compareBy<AccountEntity> { it.sortOrder }.thenBy { it.name })
    val fallbackAll =
      accounts.sortedWith(compareBy<AccountEntity> { it.sortOrder }.thenBy { it.name })

    return when (selected.accountKind.uppercase(Locale.ROOT)) {
      "DEBIT" -> {
        val cash =
          sortedActive.firstOrNull { it.accountKind.equals("CASH", ignoreCase = true) }
            ?: fallbackAll.firstOrNull { it.accountKind.equals("CASH", ignoreCase = true) }
            ?: return null
        WithdrawalAccountResolution(debitAccountId = selected.id, cashAccountId = cash.id)
      }
      "CASH" -> {
        val debit =
          sortedActive.firstOrNull { it.accountKind.equals("DEBIT", ignoreCase = true) }
            ?: fallbackAll.firstOrNull { it.accountKind.equals("DEBIT", ignoreCase = true) }
            ?: return null
        WithdrawalAccountResolution(debitAccountId = debit.id, cashAccountId = selected.id)
      }
      else -> null
    }
  }

  fun isValesIncomeHierarchy(
    typeId: String,
    classificationName: String?,
    detailName: String?,
  ): Boolean {
    if (!TransactionTypes.isIncome(typeId)) return false
    return normalizeToken(classificationName) == "otro" &&
      normalizeToken(detailName).contains("vales")
  }

  fun isValesIncomeTransaction(
    tx: FinanceTransactionEntity,
    classificationsById: Map<String, ClassificationEntity>,
    detailsById: Map<String, DetailItemEntity>,
  ): Boolean {
    val clsName = classificationsById[tx.classificationId]?.name
    val detName = detailsById[tx.detailId]?.name
    return isValesIncomeHierarchy(tx.typeId, clsName, detName)
  }

  fun resolveValesAccountId(accounts: List<AccountEntity>): String? {
    val sortedActive =
      accounts
        .filter { it.isActive }
        .sortedWith(compareBy<AccountEntity> { it.sortOrder }.thenBy { it.name })
    val fallbackAll =
      accounts.sortedWith(compareBy<AccountEntity> { it.sortOrder }.thenBy { it.name })
    return sortedActive.firstOrNull { it.accountKind.equals("VALES", ignoreCase = true) }?.id
      ?: fallbackAll.firstOrNull { it.accountKind.equals("VALES", ignoreCase = true) }?.id
  }

  /**
   * Returns the effective account ID for a non-withdrawal transaction:
   * - Ingreso -> Otro -> Vales (de) despensa is always attributed to the VALES account when present.
   * - All other transactions use tx.accountId.
   */
  fun effectiveAccountId(
    tx: FinanceTransactionEntity,
    classificationsById: Map<String, ClassificationEntity>,
    detailsById: Map<String, DetailItemEntity>,
    accounts: List<AccountEntity>,
  ): String {
    if (isValesIncomeTransaction(tx, classificationsById, detailsById)) {
      resolveValesAccountId(accounts)?.let { return it }
    }
    return tx.accountId
  }

  /**
   * Computes the map of accountId -> balanceDeltaCents that a transaction produces.
   * - Normal INGRESO: mapOf(effectiveAccountId to +tx.amountCents)
   * - Normal GASTO: mapOf(tx.accountId to -tx.amountCents)
   * - Withdrawal (Ingreso -> Otro -> Retiro): mapOf(debitId to -tx.amountCents, cashId to +tx.amountCents)
   * - Vales income (Ingreso -> Otro -> Vales [de] despensa): mapOf(valesAccountId to +tx.amountCents)
   */
  fun computeAccountBalanceDeltas(
    tx: FinanceTransactionEntity,
    classificationsById: Map<String, ClassificationEntity>,
    detailsById: Map<String, DetailItemEntity>,
    accounts: List<AccountEntity>,
  ): Map<String, Long> {
    if (isWithdrawalTransaction(tx, classificationsById, detailsById)) {
      val resolved = resolveWithdrawalAccounts(tx.accountId, accounts)
      if (resolved != null) {
        if (resolved.debitAccountId == resolved.cashAccountId) return emptyMap()
        return mapOf(
          resolved.debitAccountId to -tx.amountCents,
          resolved.cashAccountId to tx.amountCents,
        )
      }
    }
    val targetAccountId = effectiveAccountId(tx, classificationsById, detailsById, accounts)
    val delta = if (TransactionTypes.isIncome(tx.typeId)) tx.amountCents else -tx.amountCents
    return mapOf(targetAccountId to delta)
  }
}
