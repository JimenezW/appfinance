package com.example.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.local.AppDatabase
import java.util.concurrent.TimeUnit

/**
 * Background CoroutineWorker that pushes pending offline records and pulls differential changes
 * automatically whenever the device connects to an unmetered Wi-Fi network, even if the app is closed.
 */
class WifiSyncWorker(
  appContext: Context,
  workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

  override suspend fun doWork(): Result {
    val dao = AppDatabase.getInstance(applicationContext).financeDao()
    val settings = dao.getSyncSettingsOnce() ?: return Result.success()

    if (!settings.autoSyncOnWifi || settings.isDeviceRevoked || settings.apiEndpointUrl.isBlank()) {
      return Result.success()
    }

    val syncManager = SyncManager(dao = dao, transport = OkHttpSyncTransport())
    val status =
      syncManager.syncPendingIfReady(
        isWifiConnected = true,
        forceAttemptWhenNotWifi = false,
        pullAfterPush = true,
      )

    return when (status.phase) {
      SyncPhase.SYNCED -> {
        if (status.lastSentCount > 0 || status.lastPulledCount > 0) {
          com.example.data.notifications.AppNotificationHelper.notifyWifiSyncCompleted(
            context = applicationContext,
            sentCount = status.lastSentCount,
            pulledCount = status.lastPulledCount,
          )
        }
        Result.success()
      }
      SyncPhase.IDLE,
      SyncPhase.WAITING_API_CONFIG,
      SyncPhase.REVOKED -> Result.success()
      SyncPhase.WAITING_WIFI,
      SyncPhase.SYNCING,
      SyncPhase.ERROR -> Result.retry()
    }
  }
}

object WifiSyncScheduler {
  private const val UNIQUE_ONETIME_WORK = "mis_finanzas_wifi_sync_once"
  private const val UNIQUE_PERIODIC_WORK = "mis_finanzas_wifi_sync_periodic"

  private fun wifiConstraints(): Constraints =
    Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build()

  fun scheduleImmediateOnWifi(context: Context) {
    runCatching {
      val request =
        OneTimeWorkRequestBuilder<WifiSyncWorker>()
          .setConstraints(wifiConstraints())
          .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
          .build()
      WorkManager.getInstance(context.applicationContext)
        .enqueueUniqueWork(UNIQUE_ONETIME_WORK, ExistingWorkPolicy.KEEP, request)
    }
  }

  fun schedulePeriodicOnWifi(context: Context) {
    runCatching {
      val request =
        PeriodicWorkRequestBuilder<WifiSyncWorker>(6, TimeUnit.HOURS)
          .setConstraints(wifiConstraints())
          .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
          .build()
      WorkManager.getInstance(context.applicationContext)
        .enqueueUniquePeriodicWork(UNIQUE_PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }
  }

  fun cancelAll(context: Context) {
    runCatching {
      val wm = WorkManager.getInstance(context.applicationContext)
      wm.cancelUniqueWork(UNIQUE_ONETIME_WORK)
      wm.cancelUniqueWork(UNIQUE_PERIODIC_WORK)
    }
  }
}
