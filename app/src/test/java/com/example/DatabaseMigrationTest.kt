package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.TransactionTypes
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DatabaseMigrationTest {

  @Test
  fun migration3To4_preservesDataAndRemapsNumericIdsToUuids() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val dbName = "migration_test_3_4_${System.currentTimeMillis()}.db"

    // 1. Create a real v3 SQLite database matching Room v3 schema
    val helper =
      object : SQLiteOpenHelper(context, dbName, null, 3) {
        override fun onCreate(db: SQLiteDatabase) {
          db.execSQL(
            """
            CREATE TABLE transaction_types (
              id TEXT NOT NULL PRIMARY KEY,
              name TEXT NOT NULL,
              sortOrder INTEGER NOT NULL
            )
            """.trimIndent()
          )
          db.execSQL(
            """
            CREATE TABLE classifications (
              id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              typeId TEXT NOT NULL,
              name TEXT NOT NULL,
              sortOrder INTEGER NOT NULL,
              isActive INTEGER NOT NULL,
              monthlyBudgetCents INTEGER,
              isPinned INTEGER NOT NULL,
              FOREIGN KEY(typeId) REFERENCES transaction_types(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
          )
          db.execSQL(
            """
            CREATE TABLE detail_items (
              id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              classificationId INTEGER NOT NULL,
              name TEXT NOT NULL,
              sortOrder INTEGER NOT NULL,
              isActive INTEGER NOT NULL,
              isPinned INTEGER NOT NULL,
              FOREIGN KEY(classificationId) REFERENCES classifications(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
          )
          db.execSQL(
            """
            CREATE TABLE accounts (
              id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              name TEXT NOT NULL,
              shortLabel TEXT NOT NULL,
              balanceCents INTEGER NOT NULL,
              accountKind TEXT NOT NULL,
              sortOrder INTEGER NOT NULL,
              isActive INTEGER NOT NULL,
              isPinned INTEGER NOT NULL
            )
            """.trimIndent()
          )
          db.execSQL(
            """
            CREATE TABLE merchants (
              id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              name TEXT NOT NULL,
              sortOrder INTEGER NOT NULL,
              isActive INTEGER NOT NULL,
              isPinned INTEGER NOT NULL
            )
            """.trimIndent()
          )
          db.execSQL(
            """
            CREATE TABLE scheduled_rules (
              id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              title TEXT NOT NULL,
              typeId TEXT NOT NULL,
              classificationId INTEGER NOT NULL,
              detailId INTEGER NOT NULL,
              accountId INTEGER NOT NULL,
              merchantName TEXT,
              amountCents INTEGER NOT NULL,
              dayOfMonth INTEGER NOT NULL,
              isEndOfMonth INTEGER NOT NULL,
              isActive INTEGER NOT NULL,
              lastConfirmedPeriodKey TEXT,
              snoozedUntilEpochDay INTEGER
            )
            """.trimIndent()
          )
          db.execSQL(
            """
            CREATE TABLE finance_transactions (
              id TEXT NOT NULL PRIMARY KEY,
              createdAtMillis INTEGER NOT NULL,
              operationEpochDay INTEGER NOT NULL,
              fiscalPeriodKey TEXT NOT NULL,
              fiscalPeriodLabel TEXT NOT NULL,
              typeId TEXT NOT NULL,
              classificationId INTEGER NOT NULL,
              detailId INTEGER NOT NULL,
              accountId INTEGER NOT NULL,
              merchantName TEXT,
              amountCents INTEGER NOT NULL,
              note TEXT,
              updatedAtMillis INTEGER NOT NULL,
              syncStatus TEXT NOT NULL,
              syncedAtMillis INTEGER
            )
            """.trimIndent()
          )
          db.execSQL(
            """
            CREATE TABLE sync_queue (
              id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              entityType TEXT NOT NULL,
              entityId TEXT NOT NULL,
              operation TEXT NOT NULL,
              payloadJson TEXT NOT NULL,
              syncStatus TEXT NOT NULL,
              createdAtMillis INTEGER NOT NULL,
              lastAttemptMillis INTEGER,
              syncedAtMillis INTEGER,
              retryCount INTEGER NOT NULL,
              lastError TEXT
            )
            """.trimIndent()
          )
          db.execSQL(
            "CREATE INDEX index_sync_queue_syncStatus ON sync_queue(syncStatus)"
          )
          db.execSQL(
            "CREATE INDEX index_sync_queue_createdAtMillis ON sync_queue(createdAtMillis)"
          )
          db.execSQL(
            """
            CREATE TABLE sync_settings (
              id INTEGER PRIMARY KEY NOT NULL,
              apiEndpointUrl TEXT NOT NULL,
              apiAuthToken TEXT NOT NULL,
              autoSyncOnWifi INTEGER NOT NULL,
              deviceLabel TEXT NOT NULL,
              lastSuccessfulSyncMillis INTEGER,
              lastAttemptMillis INTEGER,
              lastSyncMessage TEXT
            )
            """.trimIndent()
          )

          // Seed realistic v3 user rows
          db.execSQL("INSERT INTO transaction_types VALUES ('GASTO', 'Gasto', 0)")
          db.execSQL("INSERT INTO transaction_types VALUES ('INGRESO', 'Ingreso', 1)")
          db.execSQL("INSERT INTO classifications VALUES (10, 'GASTO', 'Alimentación', 1, 1, 500000, 1)")
          db.execSQL("INSERT INTO detail_items VALUES (20, 10, 'Despensa', 1, 1, 0)")
          db.execSQL("INSERT INTO accounts VALUES (30, 'BBVA Débito', 'BBVA', 125000, 'DEBIT', 0, 1, 1)")
          db.execSQL("INSERT INTO merchants VALUES (40, 'Chedraui', 0, 1, 0)")
          db.execSQL(
            "INSERT INTO scheduled_rules VALUES (50, 'Internet', 'GASTO', 10, 20, 30, NULL, 34000, 14, 0, 1, NULL, NULL)"
          )
          db.execSQL(
            """
            INSERT INTO finance_transactions VALUES (
              'cbcabee2-089e-43b1-809b-c1b19ed99f92', 1700000000000, 20770, '2026-11', 'nov 2026',
              'GASTO', 10, 20, 30, 'Chedraui', 26900, 'Super', 1700000000000, 'PENDING', NULL
            )
            """.trimIndent()
          )
          db.execSQL(
            "INSERT INTO sync_settings VALUES (1, 'https://example.com/sync', '', 1, 'Pixel', NULL, NULL, NULL)"
          )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
      }
    helper.writableDatabase.close()

    // 2. Open with Room v5 + MIGRATION_3_4 + MIGRATION_4_5 (runs Room's strict schema validation!)
    val migratedDb =
      Room.databaseBuilder(context, AppDatabase::class.java, dbName)
        .addMigrations(
          AppDatabase.MIGRATION_1_2,
          AppDatabase.MIGRATION_2_3,
          AppDatabase.MIGRATION_3_4,
          AppDatabase.MIGRATION_4_5,
        )
        .allowMainThreadQueries()
        .build()

    try {
      val dao = migratedDb.financeDao()
      val types = dao.observeTypes().first()
      val classifications = dao.observeAllClassifications().first()
      val details = dao.observeAllDetails().first()
      val accounts = dao.observeAllAccounts().first()
      val merchants = dao.observeAllMerchants().first()
      val rules = dao.observeAllScheduledRules().first()
      val txs = dao.getAllTransactionsOnce()
      val settings = dao.getSyncSettingsOnce()

      assertEquals(2, types.size)
      assertTrue(types.any { it.id == TransactionTypes.GASTO_UUID && it.code == "GASTO" })
      assertTrue(types.any { it.id == TransactionTypes.INGRESO_UUID && it.code == "INGRESO" })

      assertEquals(1, classifications.size)
      val cls = classifications.first()
      assertNotNull(UUID.fromString(cls.id))
      assertEquals(TransactionTypes.GASTO_UUID, cls.typeId)
      assertEquals("Alimentación", cls.name)
      assertEquals(500000L, cls.monthlyBudgetCents)
      assertTrue(cls.isPinned)

      assertEquals(1, details.size)
      val det = details.first()
      assertNotNull(UUID.fromString(det.id))
      assertEquals(cls.id, det.classificationId)

      assertEquals(1, accounts.size)
      val acc = accounts.first()
      assertNotNull(UUID.fromString(acc.id))
      assertEquals(125000L, acc.balanceCents)
      assertEquals("MXN", acc.currency)

      assertEquals(1, merchants.size)
      assertNotNull(UUID.fromString(merchants.first().id))

      assertEquals(1, rules.size)
      val rule = rules.first()
      assertEquals(cls.id, rule.classificationId)
      assertEquals(det.id, rule.detailId)
      assertEquals(acc.id, rule.accountId)
      assertEquals(TransactionTypes.GASTO_UUID, rule.typeId)

      assertEquals(1, txs.size)
      val tx = txs.first()
      assertEquals("cbcabee2-089e-43b1-809b-c1b19ed99f92", tx.id)
      assertEquals(TransactionTypes.GASTO_UUID, tx.typeId)
      assertEquals(cls.id, tx.classificationId)
      assertEquals(det.id, tx.detailId)
      assertEquals(acc.id, tx.accountId)

      assertNotNull(settings)
      assertNotNull(UUID.fromString(settings!!.deviceId))
    } finally {
      migratedDb.close()
      context.deleteDatabase(dbName)
    }
  }
}
