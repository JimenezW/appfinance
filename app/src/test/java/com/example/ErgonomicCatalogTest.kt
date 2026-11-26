package com.example

import com.example.data.local.AccountEntity
import com.example.data.local.ClassificationEntity
import com.example.data.local.DetailItemEntity
import com.example.data.local.FinanceTransactionEntity
import com.example.data.local.MerchantEntity
import com.example.data.local.TransactionTypes
import com.example.data.sync.ApiJsonBuilder
import com.example.ui.CaptureFormState
import com.example.ui.FinanceUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ErgonomicCatalogTest {

  @Test
  fun smartOrdering_prioritizesPinnedThenUsageFrequencyThenSortOrder() {
    val c1Id = "11111111-1111-4111-8111-111111111111"
    val c2Id = "22222222-2222-4222-8222-222222222222"
    val c3Id = "33333333-3333-4333-8333-333333333333"
    val d1Id = "10101010-1010-4010-8010-101010101010"
    val d2Id = "11111111-1010-4010-8010-111111111111"
    val d3Id = "12121212-1010-4010-8010-121212121212"
    val a1Id = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1"
    val a2Id = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa2"
    val m1Id = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1"
    val m2Id = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb2"

    val c1 = ClassificationEntity(id = c1Id, typeId = TransactionTypes.GASTO_UUID, name = "A_Base", sortOrder = 1)
    val c2 = ClassificationEntity(id = c2Id, typeId = TransactionTypes.GASTO_UUID, name = "B_Frequent", sortOrder = 2)
    val c3 = ClassificationEntity(id = c3Id, typeId = TransactionTypes.GASTO_UUID, name = "C_Pinned", sortOrder = 3, isPinned = true)

    val d1 = DetailItemEntity(id = d1Id, classificationId = c3Id, name = "D_Base", sortOrder = 1)
    val d2 = DetailItemEntity(id = d2Id, classificationId = c3Id, name = "D_Used", sortOrder = 2)
    val d3 = DetailItemEntity(id = d3Id, classificationId = c3Id, name = "D_Pinned", sortOrder = 3, isPinned = true)

    val a1 = AccountEntity(id = a1Id, name = "Efectivo", shortLabel = "Efectivo", sortOrder = 1)
    val a2 = AccountEntity(id = a2Id, name = "BBVA", shortLabel = "BBVA", sortOrder = 2, isPinned = true)

    val m1 = MerchantEntity(id = m1Id, name = "Tienda A", sortOrder = 1)
    val m2 = MerchantEntity(id = m2Id, name = "Tienda B", sortOrder = 2)

    val txs =
      listOf(
        FinanceTransactionEntity(
          id = "t1",
          createdAtMillis = 1000L,
          operationEpochDay = 20770L,
          fiscalPeriodKey = "2026-11",
          fiscalPeriodLabel = "nov 2026",
          typeId = TransactionTypes.GASTO_UUID,
          classificationId = c2Id,
          detailId = d2Id,
          accountId = a1Id,
          merchantName = "Tienda B",
          amountCents = 5000L,
        ),
        FinanceTransactionEntity(
          id = "t2",
          createdAtMillis = 2000L,
          operationEpochDay = 20770L,
          fiscalPeriodKey = "2026-11",
          fiscalPeriodLabel = "nov 2026",
          typeId = TransactionTypes.GASTO_UUID,
          classificationId = c2Id,
          detailId = d2Id,
          accountId = a1Id,
          merchantName = "Tienda B",
          amountCents = 7500L,
        ),
      )

    val state =
      FinanceUiState(
        isLoaded = true,
        capture = CaptureFormState(typeId = TransactionTypes.GASTO_UUID),
        allClassifications = listOf(c1, c2, c3),
        allDetails = listOf(d1, d2, d3),
        allAccounts = listOf(a1, a2),
        allMerchants = listOf(m1, m2),
        allTransactions = txs,
      )

    // Pinned item first (c3), then most used (c2), then remaining by sortOrder (c1)
    assertEquals(listOf(c3Id, c2Id, c1Id), state.activeClassificationsForCapture.map { it.id })
    // Selected effectiveClassificationId defaults to first in smart-ordered list (c3), so details follow c3
    assertEquals(listOf(d3Id, d2Id, d1Id), state.activeDetailsForCapture.map { it.id })
    // Account a2 is pinned so comes first despite a1 having usage
    assertEquals(listOf(a2Id, a1Id), state.activeAccounts.map { it.id })
    // Merchant m2 ("Tienda B") was used twice so bubbles above m1 ("Tienda A")
    assertEquals(listOf("Tienda B", "Tienda A"), state.activeMerchants.map { it.name })
    assertEquals(2, state.classificationUsageCount(c2Id))
    assertEquals(2, state.merchantUsageCount("tienda b"))
  }

  @Test
  fun apiJsonBuilder_includesPinnedAndDefaultMetadata() {
    val cls =
      ClassificationEntity(
        id = "55555555-5555-4555-8555-555555555555",
        typeId = TransactionTypes.GASTO_UUID,
        name = "Salud",
        sortOrder = 5,
        isPinned = true,
        isDefault = false,
      )
    val json = ApiJsonBuilder.classificationToJson(cls)
    assertTrue(json.getBoolean("isPinned"))
    assertFalse(json.getBoolean("default"))
  }

  @Test
  fun periodFilteringAndNetBalance_updatesDynamicallyWhenSelectedPeriodChanges() {
    val cId = "11111111-1111-4111-8111-111111111111"
    val dId = "10101010-1010-4010-8010-101010101010"
    val aId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1"

    // Oct 2026 tx (with explicit fiscalPeriodKey)
    val txOct =
      FinanceTransactionEntity(
        id = "tx-oct",
        createdAtMillis = 1000L,
        operationEpochDay = 20736L, // 10/10/2026 -> 2026-10
        fiscalPeriodKey = "2026-10",
        fiscalPeriodLabel = "oct 2026",
        typeId = TransactionTypes.INGRESO_UUID,
        classificationId = cId,
        detailId = dId,
        accountId = aId,
        amountCents = 100000L,
      )
    // Nov 2026 tx (with blank fiscalPeriodKey to verify fallback from operationEpochDay 16/10/2026 -> 2026-11)
    val txNovExpense =
      FinanceTransactionEntity(
        id = "tx-nov",
        createdAtMillis = 2000L,
        operationEpochDay = com.example.domain.FiscalPeriodCalculator.ymdToEpochDay(2026, 10, 16),
        fiscalPeriodKey = "",
        fiscalPeriodLabel = "",
        typeId = TransactionTypes.GASTO_UUID,
        classificationId = cId,
        detailId = dId,
        accountId = aId,
        amountCents = 35000L,
      )

    val octState =
      FinanceUiState(
        isLoaded = true,
        selectedPeriod = com.example.domain.FiscalPeriod(2026, 10),
        allTransactions = listOf(txOct, txNovExpense),
      )
    assertEquals(1, octState.periodTransactions.size)
    assertEquals("tx-oct", octState.periodTransactions.first().entity.id)
    assertEquals(100000L, octState.periodNetBalanceCents)
    assertEquals(100000L, octState.periodAccountBalanceCents(aId))

    val novState = octState.copy(selectedPeriod = com.example.domain.FiscalPeriod(2026, 11))
    assertEquals(1, novState.periodTransactions.size)
    assertEquals("tx-nov", novState.periodTransactions.first().entity.id)
    assertEquals(-35000L, novState.periodNetBalanceCents)
    assertEquals(-35000L, novState.periodAccountBalanceCents(aId))

    // Transactions from periods strictly earlier than currentFiscalPeriod must be read-only
    val pastTx = txOct.copy(fiscalPeriodKey = "2020-01")
    val currentTx = txOct.copy(fiscalPeriodKey = octState.currentFiscalPeriod.key)
    assertFalse(octState.isTransactionEditable(pastTx))
    assertTrue(octState.isTransactionEditable(currentTx))
  }

  @Test
  fun withdrawalTransferRule_deductsDebitCreditsCashAndExcludesFromRealIncome() {
    val clsOtroId = "c-otro-ingreso"
    val detRetiroId = "d-retiro"
    val detAguinaldoId = "d-aguinaldo"
    val debitId = "acc-debit-bbva"
    val cashId = "acc-cash-efectivo"
    val valesId = "acc-vales-edenred"

    val clsOtro =
      ClassificationEntity(
        id = clsOtroId,
        typeId = TransactionTypes.INGRESO_UUID,
        name = "Otro",
        sortOrder = 2,
      )
    val detRetiro =
      DetailItemEntity(
        id = detRetiroId,
        classificationId = clsOtroId,
        name = "Retiro",
        sortOrder = 1,
      )
    val detAguinaldo =
      DetailItemEntity(
        id = detAguinaldoId,
        classificationId = clsOtroId,
        name = "Aguinaldo",
        sortOrder = 2,
      )

    val debitAcc =
      AccountEntity(
        id = debitId,
        name = "Tarjeta de Débito (BBVA)",
        shortLabel = "BBVA Débito",
        accountKind = "DEBIT",
        sortOrder = 1,
      )
    val cashAcc =
      AccountEntity(
        id = cashId,
        name = "Efectivo",
        shortLabel = "Efectivo",
        accountKind = "CASH",
        sortOrder = 2,
      )
    val valesAcc =
      AccountEntity(
        id = valesId,
        name = "Vales Despensa",
        shortLabel = "Vales",
        accountKind = "VALES",
        sortOrder = 3,
      )
    val accounts = listOf(debitAcc, cashAcc, valesAcc)

    // Invalid account (VALES) must be rejected by resolveWithdrawalAccounts
    org.junit.Assert.assertNull(
      com.example.domain.WithdrawalTransferRule.resolveWithdrawalAccounts(valesId, accounts)
    )

    // Selecting either DEBIT or CASH resolves (debitId -> cashId)
    val fromDebit =
      com.example.domain.WithdrawalTransferRule.resolveWithdrawalAccounts(debitId, accounts)
    val fromCash =
      com.example.domain.WithdrawalTransferRule.resolveWithdrawalAccounts(cashId, accounts)
    assertEquals(debitId, fromDebit?.debitAccountId)
    assertEquals(cashId, fromDebit?.cashAccountId)
    assertEquals(debitId, fromCash?.debitAccountId)
    assertEquals(cashId, fromCash?.cashAccountId)

    // Record a 500.00 MXN withdrawal selecting Efectivo (or BBVA Débito) in 2026-11
    val withdrawalTx =
      FinanceTransactionEntity(
        id = "tx-retiro-500",
        createdAtMillis = 1000L,
        operationEpochDay = 20770L,
        fiscalPeriodKey = "2026-11",
        fiscalPeriodLabel = "nov 2026",
        typeId = TransactionTypes.INGRESO_UUID,
        classificationId = clsOtroId,
        detailId = detRetiroId,
        accountId = cashId,
        amountCents = 50_000L,
      )

    val state =
      FinanceUiState(
        isLoaded = true,
        selectedPeriod = com.example.domain.FiscalPeriod(2026, 11),
        allClassifications = listOf(clsOtro),
        allDetails = listOf(detRetiro, detAguinaldo),
        allAccounts = accounts,
        allTransactions = listOf(withdrawalTx),
      )

    // 1. Account period balances: DEBIT -50,000, CASH +50,000
    assertEquals(-50_000L, state.periodAccountBalanceCents(debitId))
    assertEquals(50_000L, state.periodAccountBalanceCents(cashId))

    // 2. Excluded from real income, incomeBreakdown, and global net cash flow (-500 + 500 = 0)
    assertEquals(0L, state.periodIncomeCents)
    assertTrue(state.incomeBreakdown.isEmpty())
    assertEquals(0L, state.periodNetBalanceCents)

    // 3. Visible in transaction history with transfer label "BBVA Débito → Efectivo"
    assertEquals(1, state.periodTransactions.size)
    assertEquals("BBVA Débito → Efectivo", state.periodTransactions.first().accountShortLabel)

    // 4. When filtering by DEBIT account, net balance is -50,000 and tx is visible as outflow
    val debitFiltered = state.copy(selectedAccountFilterId = debitId)
    assertEquals(-50_000L, debitFiltered.periodNetBalanceCents)
    assertEquals(1, debitFiltered.periodTransactions.size)
    assertTrue(debitFiltered.periodTransactions.first().isEffectiveOutflow)

    // 5. When filtering by CASH account, net balance is +50,000 and tx is visible as inflow
    val cashFiltered = state.copy(selectedAccountFilterId = cashId)
    assertEquals(50_000L, cashFiltered.periodNetBalanceCents)
    assertEquals(1, cashFiltered.periodTransactions.size)
    assertFalse(cashFiltered.periodTransactions.first().isEffectiveOutflow)
  }

  @Test
  fun periodAndCaptureAccountBalances_matchCapturedPeriodTransactionsAndSumToPeriodNetBalance() {
    val clsOtroId = "c-otro-ingreso"
    val clsNominaId = "c-nomina"
    val clsBebeId = "c-bebe"
    val detRetiroId = "d-retiro"
    val detAnticipoId = "d-anticipo"
    val detValesId = "d-vales"
    val detFormulaId = "d-formula"
    val debitId = "c0ffd56e-8ffa-59bf-9d4b-3c07acf21554"
    val cashId = "adfa1b53-ff67-5615-9d14-ceed4223db9d"
    val valesId = "bf2bee3c-84c2-5ae2-8413-2ad64a83b109"

    val classifications =
      listOf(
        ClassificationEntity(id = clsOtroId, typeId = TransactionTypes.INGRESO_UUID, name = "Otro", sortOrder = 1),
        ClassificationEntity(id = clsNominaId, typeId = TransactionTypes.INGRESO_UUID, name = "Nómina", sortOrder = 2),
        ClassificationEntity(id = clsBebeId, typeId = TransactionTypes.GASTO_UUID, name = "Bebé", sortOrder = 3),
      )
    val details =
      listOf(
        DetailItemEntity(id = detRetiroId, classificationId = clsOtroId, name = "Retiro", sortOrder = 1),
        DetailItemEntity(id = detValesId, classificationId = clsOtroId, name = "Vales de despensa", sortOrder = 2),
        DetailItemEntity(id = detAnticipoId, classificationId = clsNominaId, name = "Anticipo Nómina", sortOrder = 1),
        DetailItemEntity(id = detFormulaId, classificationId = clsBebeId, name = "Fórmula", sortOrder = 1),
      )

    // Accounts carry stale/cumulative DB balanceCents from prior periods or sync
    val accounts =
      listOf(
        AccountEntity(id = debitId, name = "Tarjeta de Débito (BBVA)", shortLabel = "BBVA Débito", balanceCents = 999_999L, accountKind = "DEBIT", sortOrder = 0),
        AccountEntity(id = cashId, name = "Efectivo", shortLabel = "Efectivo", balanceCents = 888_888L, accountKind = "CASH", sortOrder = 1),
        AccountEntity(id = valesId, name = "Tarjeta de Vales de Despensa", shortLabel = "Vales", balanceCents = 777_777L, accountKind = "VALES", sortOrder = 2),
      )

    // Transactions across two fiscal periods:
    // - nov 2026 (2026-11): +3,201.50 on DEBIT
    // - dic 2026 (2026-12):
    //   - Ingreso +6,600.00 on DEBIT
    //   - Ingreso +689.40 on VALES
    //   - Gasto   -3,810.50 on DEBIT
    //   - Retiro   2,000.00 (DEBIT -2,000.00, CASH +2,000.00)
    //   - Gasto   -500.00 on CASH
    val novEpochDay = com.example.domain.FiscalPeriodCalculator.ymdToEpochDay(2026, 11, 10) // -> 2026-11
    val dicEpochDay = com.example.domain.FiscalPeriodCalculator.ymdToEpochDay(2026, 11, 20) // -> 2026-12 (cutoff 15)
    val octEpochDay = com.example.domain.FiscalPeriodCalculator.ymdToEpochDay(2026, 10, 10) // -> 2026-10

    val txs =
      listOf(
        FinanceTransactionEntity(
          id = "tx-nov-inc",
          createdAtMillis = 1000L,
          operationEpochDay = novEpochDay,
          fiscalPeriodKey = "2026-11",
          fiscalPeriodLabel = "nov 2026",
          typeId = TransactionTypes.INGRESO_UUID,
          classificationId = clsNominaId,
          detailId = detAnticipoId,
          accountId = debitId,
          amountCents = 320_150L,
        ),
        FinanceTransactionEntity(
          id = "tx-dic-inc-debit",
          createdAtMillis = 2000L,
          operationEpochDay = dicEpochDay,
          fiscalPeriodKey = "2026-12",
          fiscalPeriodLabel = "dic 2026",
          typeId = TransactionTypes.INGRESO_UUID,
          classificationId = clsNominaId,
          detailId = detAnticipoId,
          accountId = debitId,
          amountCents = 660_000L,
        ),
        FinanceTransactionEntity(
          id = "tx-dic-inc-vales",
          createdAtMillis = 2500L,
          operationEpochDay = dicEpochDay,
          fiscalPeriodKey = "2026-12",
          fiscalPeriodLabel = "dic 2026",
          typeId = TransactionTypes.INGRESO_UUID,
          classificationId = clsOtroId,
          detailId = detValesId,
          accountId = valesId,
          amountCents = 68_940L,
        ),
        FinanceTransactionEntity(
          id = "tx-dic-exp-debit",
          createdAtMillis = 3000L,
          operationEpochDay = dicEpochDay,
          fiscalPeriodKey = "2026-12",
          fiscalPeriodLabel = "dic 2026",
          typeId = TransactionTypes.GASTO_UUID,
          classificationId = clsBebeId,
          detailId = detFormulaId,
          accountId = debitId,
          amountCents = 381_050L,
        ),
        FinanceTransactionEntity(
          id = "tx-dic-retiro",
          createdAtMillis = 4000L,
          operationEpochDay = dicEpochDay,
          fiscalPeriodKey = "2026-12",
          fiscalPeriodLabel = "dic 2026",
          typeId = TransactionTypes.INGRESO_UUID,
          classificationId = clsOtroId,
          detailId = detRetiroId,
          accountId = debitId,
          amountCents = 200_000L,
        ),
        FinanceTransactionEntity(
          id = "tx-dic-exp-cash",
          createdAtMillis = 5000L,
          operationEpochDay = dicEpochDay,
          fiscalPeriodKey = "2026-12",
          fiscalPeriodLabel = "dic 2026",
          typeId = TransactionTypes.GASTO_UUID,
          classificationId = clsBebeId,
          detailId = detFormulaId,
          accountId = cashId,
          amountCents = 50_000L,
        ),
      )

    val dicState =
      FinanceUiState(
        isLoaded = true,
        capture = CaptureFormState(operationEpochDay = dicEpochDay),
        selectedPeriod = com.example.domain.FiscalPeriod(2026, 12),
        allClassifications = classifications,
        allDetails = details,
        allAccounts = accounts,
        allTransactions = txs,
      )

    // dic 2026:
    // DEBIT = +660,000 - 381,050 - 200,000 = +78,950
    // CASH  = +200,000 - 50,000            = +150,000
    // VALES = +68,940                      = +68,940
    // Total = 78,950 + 150,000 + 68,940    = 297,890 == periodNetBalanceCents
    assertEquals(78_950L, dicState.periodAccountBalanceCents(debitId))
    assertEquals(150_000L, dicState.periodAccountBalanceCents(cashId))
    assertEquals(68_940L, dicState.periodAccountBalanceCents(valesId))
    val sumDicAccounts =
      dicState.periodAccountBalanceCents(debitId) +
        dicState.periodAccountBalanceCents(cashId) +
        dicState.periodAccountBalanceCents(valesId)
    assertEquals(dicState.periodNetBalanceCents, sumDicAccounts)
    assertEquals(297_890L, dicState.periodNetBalanceCents)

    // Capture screen for dic 2026 matches Resumen de Cuentas for dic 2026
    assertEquals(78_950L, dicState.captureAccountBalanceCents(debitId))
    assertEquals(150_000L, dicState.captureAccountBalanceCents(cashId))
    assertEquals(68_940L, dicState.captureAccountBalanceCents(valesId))

    // Changing Capture operation date to nov 2026 updates Capture account balances independently
    val captureNovState = dicState.copy(capture = CaptureFormState(operationEpochDay = novEpochDay))
    assertEquals(320_150L, captureNovState.captureAccountBalanceCents(debitId))
    assertEquals(0L, captureNovState.captureAccountBalanceCents(cashId))
    assertEquals(0L, captureNovState.captureAccountBalanceCents(valesId))

    // Switching Resumen de Cuentas to nov 2026 matches nov 2026 transactions only
    val novState = dicState.copy(selectedPeriod = com.example.domain.FiscalPeriod(2026, 11))
    assertEquals(320_150L, novState.periodAccountBalanceCents(debitId))
    assertEquals(0L, novState.periodAccountBalanceCents(cashId))
    assertEquals(0L, novState.periodAccountBalanceCents(valesId))
    assertEquals(novState.periodNetBalanceCents, novState.periodAccountBalanceCents(debitId))

    // Empty period (oct 2026) shows 0.00 across all accounts in both views
    val octState =
      dicState.copy(
        capture = CaptureFormState(operationEpochDay = octEpochDay),
        selectedPeriod = com.example.domain.FiscalPeriod(2026, 10),
      )
    assertEquals(0L, octState.periodAccountBalanceCents(debitId))
    assertEquals(0L, octState.periodAccountBalanceCents(cashId))
    assertEquals(0L, octState.periodAccountBalanceCents(valesId))
    assertEquals(0L, octState.captureAccountBalanceCents(debitId))
    assertEquals(0L, octState.captureAccountBalanceCents(cashId))
    assertEquals(0L, octState.captureAccountBalanceCents(valesId))
  }
}
