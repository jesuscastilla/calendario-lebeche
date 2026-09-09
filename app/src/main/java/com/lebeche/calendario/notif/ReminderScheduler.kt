package com.lebeche.calendario.notif

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.lebeche.calendario.caldav.ICalHelper
import com.lebeche.calendario.data.Db
import com.lebeche.calendario.data.Event
import com.lebeche.calendario.data.Prefs

/**
 * Programa las alarmas de recordatorio de eventos (incluye recurrentes).
 *
 * Reglas para calcular los minutos de antelación de un evento:
 *  - reminderMinutes >= 0: valor fijo del evento (viaja en el VALARM del servidor).
 *  - reminderMinutes == -2: sin recordatorio para este evento.
 *  - reminderMinutes == -1: se usa el recordatorio por defecto global de Ajustes.
 */
object ReminderScheduler {

    const val EXTRA_EVENT_ID = "event_id"
    private const val HORIZON_DAYS = 90L

    fun rescheduleAll(context: Context) {
        val db = Db.get(context)
        val now = System.currentTimeMillis()
        for (event in db.getAllEvents()) {
            cancelForEvent(context, event.id)
            if (event.deleted) continue
            val cal = db.getCalendar(event.calendarId)
            if (cal == null || !cal.enabled) continue
            scheduleNext(context, event, now)
        }
    }

    fun scheduleForEvent(context: Context, event: Event) {
        cancelForEvent(context, event.id)
        if (event.deleted) return
        val cal = Db.get(context).getCalendar(event.calendarId)
        if (cal == null || !cal.enabled) return
        scheduleNext(context, event, System.currentTimeMillis())
    }

    fun cancelForEvent(context: Context, eventId: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context, eventId))
    }

    /** Minutos de antelacion efectivos para un evento (puede ser -1 = sin aviso). */
    fun effectiveMinutes(context: Context, event: Event): Int = when {
        event.reminderMinutes >= 0 -> event.reminderMinutes
        event.reminderMinutes == -2 -> -1
        else -> Prefs.defaultReminderMinutes(context)
    }

    private fun scheduleNext(context: Context, event: Event, now: Long) {
        val minutes = effectiveMinutes(context, event)
        if (minutes < 0) return

        val horizon = now + HORIZON_DAYS * 24L * 60L * 60L * 1000L
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for ((start, _) in ICalHelper.expand(event, now, horizon)) {
            val fireAt = start - minutes * 60L * 1000L
            if (fireAt <= now) continue
            val pi = pendingIntent(context, event.id)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pi)
            }
            return
        }
    }

    private fun pendingIntent(context: Context, eventId: Long): PendingIntent {
        val intent = Intent(context, NotificationPublisher::class.java)
        intent.putExtra(EXTRA_EVENT_ID, eventId)
        return PendingIntent.getBroadcast(
            context, eventId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}