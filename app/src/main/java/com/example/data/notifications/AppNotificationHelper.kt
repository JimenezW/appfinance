package com.example.data.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

/**
 * Manages system notifications with strictly minimal permissions:
 * - Only requires POST_NOTIFICATIONS at runtime on Android 13+ (API 33+).
 * - Channel 1: Recurring scheduled movements awaiting user confirmation.
 * - Channel 2: Automatic Wi-Fi synchronization completion notices.
 */
object AppNotificationHelper {
  const val CHANNEL_SCHEDULED_ID = "mis_finanzas_scheduled_confirmations"
  const val CHANNEL_WIFI_SYNC_ID = "mis_finanzas_wifi_sync"

  const val EXTRA_OPEN_TAB = "com.example.EXTRA_OPEN_TAB"
  const val TAB_PROGRAMADOS = "PROGRAMADOS"
  const val TAB_ADMIN_SYNC = "ADMIN_SYNC"

  private const val NOTIFICATION_ID_SCHEDULED = 4101
  private const val NOTIFICATION_ID_WIFI_SYNC = 4102

  fun hasNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    } else {
      true
    }
  }

  fun ensureChannelsCreated(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager =
      context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

    val scheduledChannel =
      NotificationChannel(
          CHANNEL_SCHEDULED_ID,
          "Movimientos programados por confirmar",
          NotificationManager.IMPORTANCE_DEFAULT,
        )
        .apply {
          description =
            "Avisos de reglas recurrentes (nómina, servicios, renta) listas para confirmar"
        }

    val syncChannel =
      NotificationChannel(
          CHANNEL_WIFI_SYNC_ID,
          "Sincronización automática Wi-Fi",
          NotificationManager.IMPORTANCE_LOW,
        )
        .apply {
          description = "Confirmación de respaldo y sincronización automática al conectar a Wi-Fi"
        }

    manager.createNotificationChannel(scheduledChannel)
    manager.createNotificationChannel(syncChannel)
  }

  fun notifyPendingScheduledRules(
    context: Context,
    pendingCount: Int,
    titlesPreview: List<String>,
  ) {
    if (pendingCount <= 0 || !hasNotificationPermission(context)) return
    ensureChannelsCreated(context)

    val intent =
      Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(EXTRA_OPEN_TAB, TAB_PROGRAMADOS)
      }
    val pendingIntent =
      PendingIntent.getActivity(
        context,
        NOTIFICATION_ID_SCHEDULED,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val title =
      if (pendingCount == 1) "1 movimiento recurrente por confirmar"
      else "$pendingCount movimientos recurrentes por confirmar"
    val preview = titlesPreview.take(3).joinToString(", ")
    val body =
      if (preview.isNotEmpty()) {
        "Pendientes: $preview. Toca para revisarlos y confirmar."
      } else {
        "Tienes movimientos programados listos para confirmar en Mis Finanzas."
      }

    val notification =
      NotificationCompat.Builder(context, CHANNEL_SCHEDULED_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .build()

    runCatching {
      NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_SCHEDULED, notification)
    }
  }

  fun cancelPendingScheduledRulesNotification(context: Context) {
    runCatching {
      NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_SCHEDULED)
    }
  }

  fun notifyWifiSyncCompleted(
    context: Context,
    sentCount: Int,
    pulledCount: Int,
  ) {
    if ((sentCount <= 0 && pulledCount <= 0) || !hasNotificationPermission(context)) return
    ensureChannelsCreated(context)

    val intent =
      Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(EXTRA_OPEN_TAB, TAB_ADMIN_SYNC)
      }
    val pendingIntent =
      PendingIntent.getActivity(
        context,
        NOTIFICATION_ID_WIFI_SYNC,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val parts = buildList {
      if (sentCount > 0) add("$sentCount enviados")
      if (pulledCount > 0) add("$pulledCount descargados")
    }
    val detail = parts.joinToString(" • ")
    val body = "Sincronización Wi-Fi completada ($detail)."

    val notification =
      NotificationCompat.Builder(context, CHANNEL_WIFI_SYNC_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle("Sincronización Wi-Fi realizada")
        .setContentText(body)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .build()

    runCatching {
      NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_WIFI_SYNC, notification)
    }
  }
}
