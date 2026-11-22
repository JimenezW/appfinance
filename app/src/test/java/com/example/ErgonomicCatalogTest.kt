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
}
