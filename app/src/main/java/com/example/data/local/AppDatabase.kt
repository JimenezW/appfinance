package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Database(
  entities =
    [
      TransactionTypeEntity::class,
      ClassificationEntity::class,
      DetailItemEntity::class,
      AccountEntity::class,
      MerchantEntity::class,
      ScheduledRuleEntity::class,
      FinanceTransactionEntity::class,
      SyncQueueItemEntity::class,
      SyncSettingsEntity::class,
    ],
  version = 4,
  exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun financeDao(): FinanceDao

  companion object {
    @Volatile private var INSTANCE: AppDatabase? = null

    val MIGRATION_1_2: Migration =
      object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL(
            "ALTER TABLE finance_transactions ADD COLUMN updatedAtMillis INTEGER NOT NULL DEFAULT 0"
          )
          db.execSQL(
            "UPDATE finance_transactions SET updatedAtMillis = createdAtMillis WHERE updatedAtMillis = 0"
          )
          db.execSQL(
            "ALTER TABLE finance_transactions ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'PENDING'"
          )
          db.execSQL("ALTER TABLE finance_transactions ADD COLUMN syncedAtMillis INTEGER")
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sync_queue (
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
            "CREATE INDEX IF NOT EXISTS index_sync_queue_syncStatus ON sync_queue(syncStatus)"
          )
          db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_sync_queue_createdAtMillis ON sync_queue(createdAtMillis)"
          )
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sync_settings (
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
        }
      }

    val MIGRATION_2_3: Migration =
      object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL("ALTER TABLE classifications ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
          db.execSQL("ALTER TABLE detail_items ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
          db.execSQL("ALTER TABLE accounts ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
          db.execSQL("ALTER TABLE merchants ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
        }
      }

    /**
     * Migrates v3 numeric Long primary/foreign keys to v4 String UUIDs, adds TransactionType.code,
     * isDefault, Account.currency, soft-delete (deletedAtMillis), and device sync fields.
     */
    val MIGRATION_3_4: Migration =
      object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL("PRAGMA foreign_keys=OFF")

          val typeIdMap = mutableMapOf<String, String>()
          val classIdMap = mutableMapOf<Long, String>()
          val detailIdMap = mutableMapOf<Long, String>()
          val accountIdMap = mutableMapOf<Long, String>()

          // 1. transaction_types
          db.execSQL(
            """
            CREATE TABLE transaction_types_new (
              id TEXT NOT NULL PRIMARY KEY,
              code TEXT NOT NULL,
              name TEXT NOT NULL,
              sortOrder INTEGER NOT NULL,
              isDefault INTEGER NOT NULL
            )
            """.trimIndent()
          )
          db.query("SELECT id, name, sortOrder FROM transaction_types").use { c ->
            while (c.moveToNext()) {
              val oldId = c.getString(0)
              val name = c.getString(1)
              val sortOrder = c.getInt(2)
              val code = TransactionTypes.codeFor(oldId)
              val newId = TransactionTypes.uuidForCode(code)
              typeIdMap[oldId] = newId
              db.execSQL(
                "INSERT OR REPLACE INTO transaction_types_new (id, code, name, sortOrder, isDefault) VALUES (?, ?, ?, ?, 1)",
                arrayOf<Any>(newId, code, name, sortOrder),
              )
            }
          }

          // 2. classifications
          db.execSQL(
            """
            CREATE TABLE classifications_new (
              id TEXT NOT NULL PRIMARY KEY,
              typeId TEXT NOT NULL,
              name TEXT NOT NULL,
              sortOrder INTEGER NOT NULL,
              isActive INTEGER NOT NULL,
              monthlyBudgetCents INTEGER,
              isPinned INTEGER NOT NULL,
              isDefault INTEGER NOT NULL,
              deletedAtMillis INTEGER,
              FOREIGN KEY(typeId) REFERENCES transaction_types(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
          )
          db.query(
              "SELECT id, typeId, name, sortOrder, isActive, monthlyBudgetCents, isPinned FROM classifications"
            )
            .use { c ->
              while (c.moveToNext()) {
                val oldId = c.getLong(0)
                val oldTypeId = c.getString(1)
                val name = c.getString(2)
                val sortOrder = c.getInt(3)
                val isActive = c.getInt(4)
                val budget: Long? = if (c.isNull(5)) null else c.getLong(5)
                val isPinned = c.getInt(6)
                val newTypeId = typeIdMap[oldTypeId] ?: TransactionTypes.uuidForCode(oldTypeId)
                val newId = UuidV5.fromName("legacy:classification:$oldId:$newTypeId:$name")
                classIdMap[oldId] = newId
                db.execSQL(
                  "INSERT INTO classifications_new (id, typeId, name, sortOrder, isActive, monthlyBudgetCents, isPinned, isDefault, deletedAtMillis) VALUES (?, ?, ?, ?, ?, ?, ?, 0, NULL)",
                  arrayOf<Any?>(newId, newTypeId, name, sortOrder, isActive, budget, isPinned),
                )
              }
            }

          // 3. detail_items
          db.execSQL(
            """
            CREATE TABLE detail_items_new (
              id TEXT NOT NULL PRIMARY KEY,
              classificationId TEXT NOT NULL,
              name TEXT NOT NULL,
              sortOrder INTEGER NOT NULL,
              isActive INTEGER NOT NULL,
              isPinned INTEGER NOT NULL,
              isDefault INTEGER NOT NULL,
              deletedAtMillis INTEGER,
              FOREIGN KEY(classificationId) REFERENCES classifications(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
          )
          db.query("SELECT id, classificationId, name, sortOrder, isActive, isPinned FROM detail_items")
            .use { c ->
              while (c.moveToNext()) {
                val oldId = c.getLong(0)
                val oldClassId = c.getLong(1)
                val name = c.getString(2)
                val sortOrder = c.getInt(3)
                val isActive = c.getInt(4)
                val isPinned = c.getInt(5)
                val newClassId =
                  classIdMap[oldClassId] ?: UuidV5.fromName("legacy:classification:$oldClassId")
                val newId = UuidV5.fromName("legacy:detail:$oldId:$newClassId:$name")
                detailIdMap[oldId] = newId
                db.execSQL(
                  "INSERT INTO detail_items_new (id, classificationId, name, sortOrder, isActive, isPinned, isDefault, deletedAtMillis) VALUES (?, ?, ?, ?, ?, ?, 0, NULL)",
                  arrayOf<Any>(newId, newClassId, name, sortOrder, isActive, isPinned),
                )
              }
            }

          // 4. accounts
          db.execSQL(
            """
            CREATE TABLE accounts_new (
              id TEXT NOT NULL PRIMARY KEY,
              name TEXT NOT NULL,
              shortLabel TEXT NOT NULL,
              balanceCents INTEGER NOT NULL,
              currency TEXT NOT NULL,
              accountKind TEXT NOT NULL,
              sortOrder INTEGER NOT NULL,
              isActive INTEGER NOT NULL,
              isPinned INTEGER NOT NULL,
              isDefault INTEGER NOT NULL,
              deletedAtMillis INTEGER
            )
            """.trimIndent()
          )
          db.query(
              "SELECT id, name, shortLabel, balanceCents, accountKind, sortOrder, isActive, isPinned FROM accounts"
            )
            .use { c ->
              while (c.moveToNext()) {
                val oldId = c.getLong(0)
                val name = c.getString(1)
                val shortLabel = c.getString(2)
                val balanceCents = c.getLong(3)
                val accountKind = c.getString(4)
                val sortOrder = c.getInt(5)
                val isActive = c.getInt(6)
                val isPinned = c.getInt(7)
                val newId = UuidV5.fromName("legacy:account:$oldId:$name")
                accountIdMap[oldId] = newId
                db.execSQL(
                  "INSERT INTO accounts_new (id, name, shortLabel, balanceCents, currency, accountKind, sortOrder, isActive, isPinned, isDefault, deletedAtMillis) VALUES (?, ?, ?, ?, 'MXN', ?, ?, ?, ?, 0, NULL)",
                  arrayOf<Any>(
                    newId,
                    name,
                    shortLabel,
                    balanceCents,
                    accountKind,
                    sortOrder,
                    isActive,
                    isPinned,
                  ),
                )
              }
            }

          // 5. merchants
          db.execSQL(
            """
            CREATE TABLE merchants_new (
              id TEXT NOT NULL PRIMARY KEY,
              name TEXT NOT NULL,
              sortOrder INTEGER NOT NULL,
              isActive INTEGER NOT NULL,
              isPinned INTEGER NOT NULL,
              isDefault INTEGER NOT NULL,
              deletedAtMillis INTEGER
            )
            """.trimIndent()
          )
          db.query("SELECT id, name, sortOrder, isActive, isPinned FROM merchants").use { c ->
            while (c.moveToNext()) {
              val oldId = c.getLong(0)
              val name = c.getString(1)
              val sortOrder = c.getInt(2)
              val isActive = c.getInt(3)
              val isPinned = c.getInt(4)
              val newId = UuidV5.fromName("legacy:merchant:$oldId:$name")
              db.execSQL(
                "INSERT INTO merchants_new (id, name, sortOrder, isActive, isPinned, isDefault, deletedAtMillis) VALUES (?, ?, ?, ?, ?, 0, NULL)",
                arrayOf<Any>(newId, name, sortOrder, isActive, isPinned),
              )
            }
          }

          // 6. scheduled_rules
          db.execSQL(
            """
            CREATE TABLE scheduled_rules_new (
              id TEXT NOT NULL PRIMARY KEY,
              title TEXT NOT NULL,
              typeId TEXT NOT NULL,
              classificationId TEXT NOT NULL,
              detailId TEXT NOT NULL,
              accountId TEXT NOT NULL,
              merchantName TEXT,
              amountCents INTEGER NOT NULL,
              dayOfMonth INTEGER NOT NULL,
              isEndOfMonth INTEGER NOT NULL,
              isActive INTEGER NOT NULL,
              lastConfirmedPeriodKey TEXT,
              snoozedUntilEpochDay INTEGER,
              isDefault INTEGER NOT NULL,
              deletedAtMillis INTEGER
            )
            """.trimIndent()
          )
          db.query(
              """
              SELECT id, title, typeId, classificationId, detailId, accountId, merchantName,
                     amountCents, dayOfMonth, isEndOfMonth, isActive, lastConfirmedPeriodKey, snoozedUntilEpochDay
              FROM scheduled_rules
              """.trimIndent()
            )
            .use { c ->
              while (c.moveToNext()) {
                val oldId = c.getLong(0)
                val title = c.getString(1)
                val oldTypeId = c.getString(2)
                val oldClassId = c.getLong(3)
                val oldDetailId = c.getLong(4)
                val oldAccountId = c.getLong(5)
                val merchantName: String? = if (c.isNull(6)) null else c.getString(6)
                val amountCents = c.getLong(7)
                val dayOfMonth = c.getInt(8)
                val isEndOfMonth = c.getInt(9)
                val isActive = c.getInt(10)
                val lastConfirmed: String? = if (c.isNull(11)) null else c.getString(11)
                val snoozed: Long? = if (c.isNull(12)) null else c.getLong(12)

                val newId = UuidV5.fromName("legacy:scheduled_rule:$oldId:$title")
                val newTypeId = typeIdMap[oldTypeId] ?: TransactionTypes.uuidForCode(oldTypeId)
                val newClassId =
                  classIdMap[oldClassId] ?: UuidV5.fromName("legacy:classification:$oldClassId")
                val newDetailId =
                  detailIdMap[oldDetailId] ?: UuidV5.fromName("legacy:detail:$oldDetailId")
                val newAccountId =
                  accountIdMap[oldAccountId] ?: UuidV5.fromName("legacy:account:$oldAccountId")

                db.execSQL(
                  """
                  INSERT INTO scheduled_rules_new (
                    id, title, typeId, classificationId, detailId, accountId, merchantName,
                    amountCents, dayOfMonth, isEndOfMonth, isActive, lastConfirmedPeriodKey,
                    snoozedUntilEpochDay, isDefault, deletedAtMillis
                  ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, NULL)
                  """.trimIndent(),
                  arrayOf<Any?>(
                    newId,
                    title,
                    newTypeId,
                    newClassId,
                    newDetailId,
                    newAccountId,
                    merchantName,
                    amountCents,
                    dayOfMonth,
                    isEndOfMonth,
                    isActive,
                    lastConfirmed,
                    snoozed,
                  ),
                )
              }
            }

          // 7. finance_transactions
          db.execSQL(
            """
            CREATE TABLE finance_transactions_new (
              id TEXT NOT NULL PRIMARY KEY,
              createdAtMillis INTEGER NOT NULL,
              operationEpochDay INTEGER NOT NULL,
              fiscalPeriodKey TEXT NOT NULL,
              fiscalPeriodLabel TEXT NOT NULL,
              typeId TEXT NOT NULL,
              classificationId TEXT NOT NULL,
              detailId TEXT NOT NULL,
              accountId TEXT NOT NULL,
              merchantName TEXT,
              amountCents INTEGER NOT NULL,
              note TEXT,
              updatedAtMillis INTEGER NOT NULL,
              syncStatus TEXT NOT NULL,
              syncedAtMillis INTEGER,
              isDefault INTEGER NOT NULL,
              deletedAtMillis INTEGER
            )
            """.trimIndent()
          )
          db.query(
              """
              SELECT id, createdAtMillis, operationEpochDay, fiscalPeriodKey, fiscalPeriodLabel,
                     typeId, classificationId, detailId, accountId, merchantName, amountCents,
                     note, updatedAtMillis, syncStatus, syncedAtMillis
              FROM finance_transactions
              """.trimIndent()
            )
            .use { c ->
              while (c.moveToNext()) {
                val id = c.getString(0)
                val createdAtMillis = c.getLong(1)
                val operationEpochDay = c.getLong(2)
                val fiscalPeriodKey = c.getString(3)
                val fiscalPeriodLabel = c.getString(4)
                val oldTypeId = c.getString(5)
                val oldClassId = c.getLong(6)
                val oldDetailId = c.getLong(7)
                val oldAccountId = c.getLong(8)
                val merchantName: String? = if (c.isNull(9)) null else c.getString(9)
                val amountCents = c.getLong(10)
                val note: String? = if (c.isNull(11)) null else c.getString(11)
                val updatedAtMillis = c.getLong(12)
                val syncStatus = c.getString(13)
                val syncedAtMillis: Long? = if (c.isNull(14)) null else c.getLong(14)

                val newTypeId = typeIdMap[oldTypeId] ?: TransactionTypes.uuidForCode(oldTypeId)
                val newClassId =
                  classIdMap[oldClassId] ?: UuidV5.fromName("legacy:classification:$oldClassId")
                val newDetailId =
                  detailIdMap[oldDetailId] ?: UuidV5.fromName("legacy:detail:$oldDetailId")
                val newAccountId =
                  accountIdMap[oldAccountId] ?: UuidV5.fromName("legacy:account:$oldAccountId")

                db.execSQL(
                  """
                  INSERT INTO finance_transactions_new (
                    id, createdAtMillis, operationEpochDay, fiscalPeriodKey, fiscalPeriodLabel,
                    typeId, classificationId, detailId, accountId, merchantName, amountCents,
                    note, updatedAtMillis, syncStatus, syncedAtMillis, isDefault, deletedAtMillis
                  ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, NULL)
                  """.trimIndent(),
                  arrayOf<Any?>(
                    id,
                    createdAtMillis,
                    operationEpochDay,
                    fiscalPeriodKey,
                    fiscalPeriodLabel,
                    newTypeId,
                    newClassId,
                    newDetailId,
                    newAccountId,
                    merchantName,
                    amountCents,
                    note,
                    updatedAtMillis,
                    syncStatus,
                    syncedAtMillis,
                  ),
                )
              }
            }

          // Drop legacy tables (children first) and rename new tables
          db.execSQL("DROP TABLE finance_transactions")
          db.execSQL("DROP TABLE scheduled_rules")
          db.execSQL("DROP TABLE merchants")
          db.execSQL("DROP TABLE accounts")
          db.execSQL("DROP TABLE detail_items")
          db.execSQL("DROP TABLE classifications")
          db.execSQL("DROP TABLE transaction_types")

          db.execSQL("ALTER TABLE transaction_types_new RENAME TO transaction_types")
          db.execSQL("ALTER TABLE classifications_new RENAME TO classifications")
          db.execSQL("ALTER TABLE detail_items_new RENAME TO detail_items")
          db.execSQL("ALTER TABLE accounts_new RENAME TO accounts")
          db.execSQL("ALTER TABLE merchants_new RENAME TO merchants")
          db.execSQL("ALTER TABLE scheduled_rules_new RENAME TO scheduled_rules")
          db.execSQL("ALTER TABLE finance_transactions_new RENAME TO finance_transactions")

          // Recreate all Room indices
          db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_transaction_types_code ON transaction_types(code)"
          )
          db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_classifications_typeId ON classifications(typeId)"
          )
          db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_classifications_deletedAtMillis ON classifications(deletedAtMillis)"
          )
          db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_detail_items_classificationId ON detail_items(classificationId)"
          )
          db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_detail_items_deletedAtMillis ON detail_items(deletedAtMillis)"
          )
          db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_accounts_deletedAtMillis ON accounts(deletedAtMillis)"
          )
          db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_merchants_deletedAtMillis ON merchants(deletedAtMillis)"
          )
          db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_scheduled_rules_deletedAtMillis ON scheduled_rules(deletedAtMillis)"
          )
          db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_finance_transactions_fiscalPeriodKey ON finance_transactions(fiscalPeriodKey)"
          )
          db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_finance_transactions_operationEpochDay ON finance_transactions(operationEpochDay)"
          )
          db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_finance_transactions_accountId ON finance_transactions(accountId)"
          )
          db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_finance_transactions_deletedAtMillis ON finance_transactions(deletedAtMillis)"
          )

          // 8. Extend sync_settings with device identity and enrollment fields
          val generatedDeviceId = UUID.randomUUID().toString()
          db.execSQL("ALTER TABLE sync_settings ADD COLUMN deviceId TEXT NOT NULL DEFAULT ''")
          db.execSQL(
            "UPDATE sync_settings SET deviceId = ? WHERE deviceId = ''",
            arrayOf<Any>(generatedDeviceId),
          )
          db.execSQL("ALTER TABLE sync_settings ADD COLUMN enrollmentToken TEXT NOT NULL DEFAULT ''")
          db.execSQL(
            "ALTER TABLE sync_settings ADD COLUMN isDeviceEnrolled INTEGER NOT NULL DEFAULT 0"
          )
          db.execSQL(
            "ALTER TABLE sync_settings ADD COLUMN isDeviceRevoked INTEGER NOT NULL DEFAULT 0"
          )
          db.execSQL(
            "ALTER TABLE sync_settings ADD COLUMN lastSyncServerMillis INTEGER NOT NULL DEFAULT 0"
          )

          db.execSQL("PRAGMA foreign_keys=ON")
        }
      }

    fun getInstance(context: Context): AppDatabase {
      return INSTANCE
        ?: synchronized(this) {
          INSTANCE
            ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "mis_finanzas.db",
              )
              .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
              .addCallback(
                object : Callback() {
                  override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                      INSTANCE?.let { DatabaseSeeder.seedIfEmpty(it.financeDao()) }
                    }
                  }
                }
              )
              .build()
              .also { INSTANCE = it }
        }
    }
  }
}
