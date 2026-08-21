package com.lebeche.calendario.notif

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lebeche.calendario.R
import com.lebeche.calendario.data.Db

/** Muestra la notificación del evento y reprograma la siguiente ocurrencia. */
class NotificationPublisher : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getLongExtra(ReminderScheduler.EXTRA_EVENT_ID, -1L)
        if (eventId < 0) return
        val event = Db.get(context).getEvent(eventId) ?: return
        if (event.deleted) return

        ensureChannel(context)
        if (!canNotify(context)) return

        val text = if (event.location.isNotBlank()) event.location else "Recordatorio de evento"
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(event.title)
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify(eventId.toInt(), notif)

        ReminderScheduler.scheduleForEvent(context, event)
    }

    companion object {
        const val CHANNEL_ID = "eventos"

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Recordatorios de eventos",
                    NotificationManager.IMPORTANCE_HIGH
                )
                context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
            }
        }

        fun canNotify(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) return false
            return NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }
}
