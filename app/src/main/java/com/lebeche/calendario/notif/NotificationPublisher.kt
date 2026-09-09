package com.lebeche.calendario.notif

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lebeche.calendario.MainActivity
import com.lebeche.calendario.R
import com.lebeche.calendario.data.Db
import com.lebeche.calendario.data.Event
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Muestra la notificacion del evento y reprograma la siguiente ocurrencia. */
class NotificationPublisher : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getLongExtra(ReminderScheduler.EXTRA_EVENT_ID, -1L)
        if (eventId < 0) return
        val event = Db.get(context).getEvent(eventId) ?: return
        if (event.deleted) return

        ensureChannel(context)
        if (!canNotify(context)) return

        val text = buildString {
            append(whenLabel(event))
            if (event.location.isNotBlank()) {
                append(" · ")
                append(event.location)
            }
        }

        val openApp = PendingIntent.getActivity(
            context, eventId.toInt(),
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(event.title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .build()
        NotificationManagerCompat.from(context).notify(eventId.toInt(), notif)

        ReminderScheduler.scheduleForEvent(context, event)
    }

    private fun whenLabel(e: Event): String {
        val zone = if (e.allDay) ZoneOffset.UTC else ZoneId.systemDefault()
        val start = Instant.ofEpochMilli(e.dtstart).atZone(zone)
        return if (e.allDay) {
            "Todo el dia · " + DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale("es", "ES")).format(start)
        } else {
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM · HH:mm", Locale("es", "ES")).format(start)
        }
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