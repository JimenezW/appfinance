package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.ClassificationEntity
import com.example.data.local.DatabaseSeeder
import com.example.data.local.FinanceTransactionEntity
import com.example.data.local.SyncEntityTypes
import com.example.data.local.SyncSettingsEntity
import com.example.data.local.SyncStatus
import com.example.data.local.TransactionTypes
import com.example.data.repository.FinanceRepository
import com.example.data.sync.SyncManager
import com.example.data.sync.SyncPhase
import com.example.data.sync.SyncTransport
import com.example.data.sync.SyncTransportResult
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SyncAndExportTest {
  private lateinit var db: AppDatabase
  private lateinit var repository: FinanceRepository

  @Before
  fun setup() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db =
      Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    DatabaseSeeder.seedIfEmpty(db.financeDao())
    repository = FinanceRepository(db.financeDao())
    repository.ensureSeeded()
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun offlineCaptureAndAdminEdit_enqueuePendingItemsAndExportSeparateJsons() = runTest {
    val classifications = repository.classifications.first()
    val details = repository.details.first()
    val accounts = repository.accounts.first()
    val types = repository.types.first()
    val merchants = repository.merchants.first()
    val rules = repository.scheduledRules.first()

    // Seeded entities must carry official UUIDv5 IDs and isDefault = true
    assertEquals(2, types.size)
    assertTrue(types.any { it.id == TransactionTypes.GASTO_UUID && it.code == "GASTO" && it.isDefault })
    assertTrue(types.any { it.id == TransactionTypes.INGRESO_UUID && it.code == "INGRESO" && it.isDefault })
    assertTrue(classifications.all { it.isDefault })

    // ensureSeeded must initialize a valid UUIDv4 deviceId
    val settings = db.financeDao().getSyncSettingsOnce()
    assertNotNull(settings)
    assertNotNull(UUID.fromString(settings!!.deviceId))

    val cls = classifications.first { TransactionTypes.isExpense(it.typeId) }
    val det = details.first { it.classificationId == cls.id }
    val acc = accounts.first()

    // User adds a new classification offline (generates UUIDv4 and default = false)
    val newClassId =
      repository.saveClassification(
        ClassificationEntity(
          id = "",
          typeId = TransactionTypes.GASTO_UUID,
          name = "Mascotas",
          sortOrder = 99,
        )
      )
    assertNotNull(UUID.fromString(newClassId))
    val createdClass = repository.classifications.first().first { it.id == newClassId }
    assertFalse(createdClass.isDefault)

    // User records a transaction offline
    val txId = UUID.randomUUID().toString()
    val tx =
      FinanceTransactionEntity(
        id = txId,
        createdAtMillis = 1_700_000_000_000L,
        operationEpochDay = 20_770L,
        fiscalPeriodKey = "2026-11",
        fiscalPeriodLabel = "nov 2026",
        typeId = TransactionTypes.GASTO_UUID,
        classificationId = cls.id,
        detailId = det.id,
        accountId = acc.id,
        merchantName = "Chedraui",
        amountCents = 32550L,
        note = "Compra offline",
      )
    repository.recordTransaction(tx)

    // Verify account balance was atomically debited
    val updatedAcc = repository.accounts.first().first { it.id == acc.id }
    assertEquals(-32550L, updatedAcc.balanceCents)

    val pendingQueue = repository.pendingSyncQueue.first()
    assertEquals(2, pendingQueue.size)
    assertTrue(pendingQueue.any { it.entityType == SyncEntityTypes.CLASSIFICATION && it.entityId == newClassId })
    assertTrue(pendingQueue.any { it.entityType == SyncEntityTypes.TRANSACTION && it.entityId == txId })

    // Verify Admin JSON export includes TransactionType.code and default flag
    val adminJsonStr =
      repository.buildAdminConfigJson(
        types = types,
        classifications = repository.classifications.first(),
        details = details,
        accounts = accounts,
        merchants = merchants,
        scheduledRules = rules,
      )
    val adminJson = JSONObject(adminJsonStr)
    assertEquals("1.0", adminJson.getString("schemaVersion"))
    assertEquals("admin_configuration", adminJson.getString("exportType"))
    assertFalse(adminJson.has("transactions"))
    val dataObj = adminJson.getJSONObject("data")
    val typesArr = dataObj.getJSONArray("transactionTypes")
    assertEquals(2, typesArr.length())
    assertTrue(typesArr.getJSONObject(0).has("code"))
    assertTrue(typesArr.getJSONObject(0).getBoolean("default"))
    assertTrue(dataObj.getJSONArray("classifications").length() >= 1)
    assertTrue(dataObj.getJSONArray("accounts").length() >= 1)

    // Verify Captured Data JSON export
    val capturedJsonStr =
      repository.buildCapturedDataJson(
        transactions = repository.transactions.first(),
        classificationsById = repository.classifications.first().associateBy { it.id },
        detailsById = details.associateBy { it.id },
        accountsById = accounts.associateBy { it.id },
      )
    val capturedJson = JSONObject(capturedJsonStr)
    assertEquals("1.0", capturedJson.getString("schemaVersion"))
    assertEquals("captured_transactions", capturedJson.getString("exportType"))
    assertEquals(1, capturedJson.getJSONObject("summary").getInt("pendingSyncCount"))
    val txArray = capturedJson.getJSONArray("transactions")
    assertEquals(1, txArray.length())
    val txJson = txArray.getJSONObject(0)
    assertEquals(txId, txJson.getString("id"))
    assertEquals(325.5, txJson.getDouble("amountMxn"), 0.001)
    assertEquals(SyncStatus.PENDING, txJson.getString("syncStatus"))
    assertFalse(txJson.getBoolean("default"))

    // Soft-deleting transaction must hide it from observations and reverse account balance
    repository.deleteTransaction(repository.transactions.first().first { it.id == txId })
    assertEquals(0, repository.transactions.first().size)
    val restoredAcc = repository.accounts.first().first { it.id == acc.id }
    assertEquals(0L, restoredAcc.balanceCents)
  }

  @Test
  fun syncManager_respectsWifiAndApiConfig_andMarksSyncedOnHttp200() = runTest {
    val cls = repository.classifications.first().first { TransactionTypes.isExpense(it.typeId) }
    val det = repository.details.first().first { it.classificationId == cls.id }
    val acc = repository.accounts.first().first()
    val txId = UUID.randomUUID().toString()

    repository.recordTransaction(
      FinanceTransactionEntity(
        id = txId,
        createdAtMillis = 1_700_000_000_000L,
        operationEpochDay = 20_770L,
        fiscalPeriodKey = "2026-11",
        fiscalPeriodLabel = "nov 2026",
        typeId = TransactionTypes.GASTO_UUID,
        classificationId = cls.id,
        detailId = det.id,
        accountId = acc.id,
        amountCents = 15000L,
      )
    )

    var lastPostedJson: String? = null
    var lastPostedUrl: String? = null
    var mode = "503"
    val fakeTransport =
      object : SyncTransport {
        override suspend fun postJson(
          endpointUrl: String,
          jsonPayload: String,
          authContext: com.example.data.security.DeviceAuthContext,
          idempotencyKey: String?,
        ): SyncTransportResult {
          lastPostedUrl = endpointUrl
          lastPostedJson = jsonPayload
          return when (mode) {
            "200" ->
              SyncTransportResult.Success(
                httpCode = 200,
                responseBody =
                  """{"success":true,"data":{"serverTimestampMillis":1795211100000,"syncedTransactionIds":["$txId"],"deletedTransactionIds":[],"failedItems":[]}}""",
              )
            "REVOKED" ->
              SyncTransportResult.Failure(
                httpCode = 403,
                errorCode = "DEVICE_REVOKED",
                message = "Dispositivo revocado",
              )
            else ->
              SyncTransportResult.Failure(
                httpCode = 503,
                errorCode = "SERVICE_UNAVAILABLE",
                message = "HTTP 503: Servicio no disponible",
              )
          }
        }

        override suspend fun getJson(
          endpointUrl: String,
          authContext: com.example.data.security.DeviceAuthContext,
        ): SyncTransportResult {
          return SyncTransportResult.Success(
            httpCode = 200,
            responseBody =
              """
              {
                "success": true,
                "data": {
                  "sinceMillis": 0,
                  "serverTimestampMillis": 1795235495938,
                  "changes": {
                    "accounts": [
                      {
                        "id": "${acc.id}",
                        "name": "${acc.name}",
                        "shortLabel": "${acc.shortLabel}",
                        "accountKind": "${acc.accountKind}",
                        "balanceCents": 599100,
                        "currency": "MXN",
                        "sortOrder": 0,
                        "isActive": true,
                        "isPinned": false,
                        "default": true
                      }
                    ]
                  }
                }
              }
              """.trimIndent(),
          )
        }
      }

    val syncManager = SyncManager(dao = db.financeDao(), transport = fakeTransport)

    // 1. Not connected to Wi-Fi -> waits for Wi-Fi
    val offlineResult = syncManager.syncPendingIfReady(isWifiConnected = false)
    assertEquals(SyncPhase.WAITING_WIFI, offlineResult.phase)

    // 2. Wi-Fi connected, but API URL blank -> waits for API config
    val noUrlResult = syncManager.syncPendingIfReady(isWifiConnected = true)
    assertEquals(SyncPhase.WAITING_API_CONFIG, noUrlResult.phase)

    // 3. Configure base API URL, transport returns 503 -> error recorded, stays pending/failed
    repository.saveSyncSettings(
      SyncSettingsEntity(
        apiEndpointUrl = "https://api.ejemplo.com",
        enrollmentToken = "enroll-123",
        autoSyncOnWifi = true,
      )
    )
    val errorResult = syncManager.syncPendingIfReady(isWifiConnected = true)
    assertEquals(SyncPhase.ERROR, errorResult.phase)
    assertEquals("https://api.ejemplo.com/api/v1/sync/batch", lastPostedUrl)
    assertEquals(1, db.financeDao().getPendingSyncQueueOnce().size)
    assertEquals(SyncStatus.FAILED, db.financeDao().getPendingSyncQueueOnce().first().syncStatus)

    // 4. API returns HTTP 200 -> marks queue & transaction SYNCED, marks device enrolled, and pulls changes
    mode = "200"
    val successResult = syncManager.syncPendingIfReady(isWifiConnected = true, pullAfterPush = true)
    assertEquals(SyncPhase.SYNCED, successResult.phase)
    assertEquals(1, successResult.lastSentCount)
    assertEquals(1, successResult.lastPulledCount)
    assertNotNull(lastPostedJson)
    val sentBatch = JSONObject(lastPostedJson!!)
    assertEquals("wifi_sync_batch", sentBatch.getString("exportType"))
    assertTrue(sentBatch.getString("deviceId").isNotBlank())
    assertTrue(sentBatch.getJSONObject("adminChanges").has("deletedIds"))
    assertEquals(1, sentBatch.getJSONArray("capturedTransactions").length())

    assertEquals(0, db.financeDao().getPendingSyncQueueOnce().size)
    val storedTx = db.financeDao().getAllTransactionsOnce().first()
    assertEquals(SyncStatus.SYNCED, storedTx.syncStatus)

    // Pulled account balance and server cursor must be persisted
    val updatedAccount = repository.accounts.first().first { it.id == acc.id }
    assertEquals(599100L, updatedAccount.balanceCents)
    val updatedSettings = db.financeDao().getSyncSettingsOnce()!!
    assertTrue(updatedSettings.isDeviceEnrolled)
    assertEquals(1795235495938L, updatedSettings.lastSyncServerMillis)

    // 5. DEVICE_REVOKED (403) must disable auto-sync and transition to REVOKED
    repository.recordTransaction(
      FinanceTransactionEntity(
        id = UUID.randomUUID().toString(),
        createdAtMillis = 1_700_000_000_001L,
        operationEpochDay = 20_770L,
        fiscalPeriodKey = "2026-11",
        fiscalPeriodLabel = "nov 2026",
        typeId = TransactionTypes.GASTO_UUID,
        classificationId = cls.id,
        detailId = det.id,
        accountId = acc.id,
        amountCents = 1000L,
      )
    )
    mode = "REVOKED"
    val revokedResult = syncManager.syncPendingIfReady(isWifiConnected = true)
    assertEquals(SyncPhase.REVOKED, revokedResult.phase)
    val revokedSettings = db.financeDao().getSyncSettingsOnce()!!
    assertTrue(revokedSettings.isDeviceRevoked)
    assertFalse(revokedSettings.autoSyncOnWifi)
  }

  @Test
  fun pullRemoteChanges_fullCatalogOverSeededDb_succeedsWithoutSqliteConstraintTrigger1811() = runTest {
    val types = repository.types.first()
    val classifications = repository.classifications.first()
    val details = repository.details.first()
    val accounts = repository.accounts.first()

    val cls = classifications.first()
    val det = details.first { it.classificationId == cls.id }
    val acc = accounts.first()

    repository.saveSyncSettings(
      SyncSettingsEntity(
        apiEndpointUrl = "https://api.ejemplo.com",
        autoSyncOnWifi = true,
      )
    )

    val fakeTransport =
      object : SyncTransport {
        override suspend fun postJson(
          endpointUrl: String,
          jsonPayload: String,
          authContext: com.example.data.security.DeviceAuthContext,
          idempotencyKey: String?,
        ): SyncTransportResult =
          SyncTransportResult.Success(httpCode = 200, responseBody = """{"success":true}""")

        override suspend fun getJson(
          endpointUrl: String,
          authContext: com.example.data.security.DeviceAuthContext,
        ): SyncTransportResult {
          // Return existing seeded transactionTypes, classifications, details, and 2 historical transactions
          return SyncTransportResult.Success(
            httpCode = 200,
            responseBody =
              """
              {
                "success": true,
                "data": {
                  "sinceMillis": 0,
                  "serverTimestampMillis": 1795235495938,
                  "changes": {
                    "transactionTypes": [
                      {"id": "${types[0].id}", "code": "${types[0].code}", "name": "${types[0].name}", "sortOrder": 0, "default": true},
                      {"id": "${types[1].id}", "code": "${types[1].code}", "name": "${types[1].name}", "sortOrder": 1, "default": true}
                    ],
                    "classifications": [
                      {"id": "${cls.id}", "typeId": "${cls.typeId}", "name": "${cls.name}", "sortOrder": ${cls.sortOrder}, "isActive": true, "isPinned": false, "monthlyBudgetCents": null, "default": true}
                    ],
                    "details": [
                      {"id": "${det.id}", "classificationId": "${det.classificationId}", "name": "${det.name}", "sortOrder": ${det.sortOrder}, "isActive": true, "isPinned": false, "default": true}
                    ],
                    "accounts": [
                      {"id": "${acc.id}", "name": "${acc.name}", "shortLabel": "${acc.shortLabel}", "accountKind": "${acc.accountKind}", "balanceCents": 450000, "currency": "MXN", "sortOrder": 0, "isActive": true, "isPinned": false, "default": true}
                    ],
                    "transactions": [
                      {"id": "e5ceb67e-987a-5ed9-9b80-62a842a8e4c4", "operationEpochDay": 20775, "operationDate": "18/11/2026", "fiscalPeriodKey": "2026-12", "fiscalPeriodLabel": "dic 2026", "typeId": "${cls.typeId}", "classificationId": "${cls.id}", "detailId": "${det.id}", "accountId": "${acc.id}", "merchantName": "Netflix", "amountCents": 26900, "note": "Histórico 1", "default": true, "updatedAtMillis": 1794999604213}
                    ]
                  }
                }
              }
              """.trimIndent(),
          )
        }
      }

    val syncManager = SyncManager(dao = db.financeDao(), transport = fakeTransport)
    val result =
      syncManager.pullRemoteChanges(
        isWifiConnected = true,
        forceAttemptWhenNotWifi = true,
        sinceMillisOverride = 0L,
      )

    assertEquals(SyncPhase.SYNCED, result.phase)
    assertEquals(6, result.lastPulledCount)
    assertEquals(1, repository.transactions.first().size)
    assertEquals(14, repository.classifications.first().size)
    assertEquals(65, repository.details.first().size)
  }
}
