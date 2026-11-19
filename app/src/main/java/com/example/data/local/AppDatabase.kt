package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
  version = 3,
  exportSchema = false,
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

    fun getInstance(context: Context): AppDatabase {
      return INSTANCE
        ?: synchronized(this) {
          INSTANCE
            ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "mis_finanzas.db",
              )
              .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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
