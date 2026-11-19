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
    val c1 = ClassificationEntity(id = 1L, typeId = TransactionTypes.GASTO, name = "A_Base", sortOrder = 1)
    val c2 = ClassificationEntity(id = 2L, typeId = TransactionTypes.GASTO, name = "B_Frequent", sortOrder = 2)
    val c3 = ClassificationEntity(id = 3L, typeId = TransactionTypes.GASTO, name = "C_Pinned", sortOrder = 3, isPinned = true)

    val d1 = DetailItemEntity(id = 10L, classificationId = 3L, name = "D_Base", sortOrder = 1)
    val d2 = DetailItemEntity(id = 11L, classificationId = 3L, name = "D_Used", sortOrder = 2)
    val d3 = DetailItemEntity(id = 12L, classificationId = 3L, name = "D_Pinned", sortOrder = 3, isPinned = true)

    val a1 = AccountEntity(id = 1L, name = "Efectivo", shortLabel = "Efectivo", sortOrder = 1)
    val a2 = AccountEntity(id = 2L, name = "BBVA", shortLabel = "BBVA", sortOrder = 2, isPinned = true)

    val m1 = MerchantEntity(id = 1L, name = "Tienda A", sortOrder = 1)
    val m2 = MerchantEntity(id = 2L, name = "Tienda B", sortOrder = 2)

    val txs =
      listOf(
        FinanceTransactionEntity(
          id = "t1",
          createdAtMillis = 1000L,
          operationEpochDay = 20770L,
          fiscalPeriodKey = "2026-11",
          fiscalPeriodLabel = "nov 2026",
          typeId = TransactionTypes.GASTO,
          classificationId = 2L,
          detailId = 11L,
          accountId = 1L,
          merchantName = "Tienda B",
          amountCents = 5000L,
        ),
        FinanceTransactionEntity(
          id = "t2",
          createdAtMillis = 2000L,
          operationEpochDay = 20770L,
          fiscalPeriodKey = "2026-11",
          fiscalPeriodLabel = "nov 2026",
          typeId = TransactionTypes.GASTO,
          classificationId = 2L,
          detailId = 11L,
          accountId = 1L,
          merchantName = "Tienda B",
          amountCents = 7500L,
        ),
      )

    val state =
      FinanceUiState(
        isLoaded = true,
        capture = CaptureFormState(typeId = TransactionTypes.GASTO),
        allClassifications = listOf(c1, c2, c3),
        allDetails = listOf(d1, d2, d3),
        allAccounts = listOf(a1, a2),
        allMerchants = listOf(m1, m2),
        allTransactions = txs,
      )

    // Pinned item first (c3), then most used (c2), then remaining by sortOrder (c1)
    assertEquals(listOf(3L, 2L, 1L), state.activeClassificationsForCapture.map { it.id })
    // Selected effectiveClassificationId defaults to first in smart-ordered list (c3), so details follow c3
    assertEquals(listOf(12L, 11L, 10L), state.activeDetailsForCapture.map { it.id })
    // Account a2 is pinned so comes first despite a1 having usage
    assertEquals(listOf(2L, 1L), state.activeAccounts.map { it.id })
    // Merchant m2 ("Tienda B") was used twice so bubbles above m1 ("Tienda A")
    assertEquals(listOf("Tienda B", "Tienda A"), state.activeMerchants.map { it.name })
    assertEquals(2, state.classificationUsageCount(2L))
    assertEquals(2, state.merchantUsageCount("tienda b"))
  }

  @Test
  fun apiJsonBuilder_includesPinnedMetadata() {
    val cls =
      ClassificationEntity(
        id = 5L,
        typeId = TransactionTypes.GASTO,
        name = "Salud",
        sortOrder = 5,
        isPinned = true,
      )
    val json = ApiJsonBuilder.classificationToJson(cls)
    assertTrue(json.getBoolean("isPinned"))
  }
}
