package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
    ],
  version = 1,
  exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun financeDao(): FinanceDao

  companion object {
    @Volatile private var INSTANCE: AppDatabase? = null

    fun getInstance(context: Context): AppDatabase {
      return INSTANCE
        ?: synchronized(this) {
          INSTANCE
            ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "mis_finanzas.db",
              )
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
