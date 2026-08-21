package com.lebeche.calendario.cal

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.lebeche.calendario.data.CalInfo
import com.lebeche.calendario.data.Event

/**
 * Espejo de eventos en el calendario del sistema Android (CalendarProvider).
 * Permite que los eventos de CalDAV se vean en la app "Calendario" del móvil.
 */
object SystemCalendarSync {

    private const val ACCOUNT_NAME = "Calendario Lebeche"
    private const val ACCOUNT_TYPE = "com.lebeche.calendario"

    private fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    fun ensureCalendar(context: Context, cal: CalInfo): Long? {
        if (!hasPermission(context)) return null
        cal.systemCalendarId?.let { return it }

        val proj = arrayOf(CalendarContract.Calendars._ID)
        val sel = "${CalendarContract.Calendars.ACCOUNT_TYPE} = ? AND " +
            "${CalendarContract.Calendars.ACCOUNT_NAME} = ? AND " +
            "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} = ?"
        val args = arrayOf(ACCOUNT_TYPE, ACCOUNT_NAME, cal.displayName)
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI, proj, sel, args, null
        )?.use { c -> if (c.moveToFirst()) return c.getLong(0) }

        val cv = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
            put(CalendarContract.Calendars.NAME, cal.displayName)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, cal.displayName)
            put(CalendarContract.Calendars.CALENDAR_COLOR, cal.color)
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, "local")
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, "UTC")
        }
        val uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
            .build()
        return context.contentResolver.insert(uri, cv)?.lastPathSegment?.toLongOrNull()
    }

    fun upsertEvent(context: Context, event: Event, cal: CalInfo): Long? {
        if (!hasPermission(context)) return null
        val calId = ensureCalendar(context, cal) ?: return null

        val cv = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calId)
            put(CalendarContract.Events.TITLE, event.title)
            put(CalendarContract.Events.DESCRIPTION, event.description)
            put(CalendarContract.Events.EVENT_LOCATION, event.location)
            put(CalendarContract.Events.DTSTART, event.dtstart)
            put(CalendarContract.Events.DTEND, event.dtend)
            put(CalendarContract.Events.ALL_DAY, if (event.allDay) 1 else 0)
            put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
            put(CalendarContract.Events.SYNC_DATA1, event.id.toString())
            if (!event.rrule.isNullOrBlank()) put(CalendarContract.Events.RRULE, event.rrule)
        }

        val existing = event.systemEventId
        return if (existing != null) {
            context.contentResolver.update(
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, existing),
                cv, null, null
            )
            existing
        } else {
            context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, cv)
                ?.lastPathSegment?.toLongOrNull()
        }
    }

    fun deleteEvent(context: Context, event: Event) {
        if (!hasPermission(context)) return
        event.systemEventId?.let {
            context.contentResolver.delete(
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, it),
                null, null
            )
        }
    }

    fun deleteCalendar(context: Context, cal: CalInfo) {
        if (!hasPermission(context)) return
        cal.systemCalendarId?.let {
            context.contentResolver.delete(
                ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, it),
                null, null
            )
        }
    }
}
