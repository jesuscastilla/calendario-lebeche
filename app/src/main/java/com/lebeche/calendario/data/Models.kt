package com.lebeche.calendario.data

/** Cuenta CalDAV (servidor + credenciales). */
data class Account(
    val id: Long = 0,
    val name: String,
    val baseUrl: String,
    val username: String,
    val password: String,
    val insecureTls: Boolean = false,
    val lastSyncAt: Long? = null
)

/** Calendario remoto descubierto en una cuenta CalDAV. */
data class CalInfo(
    val id: Long = 0,
    val accountId: Long,
    val href: String,
    val displayName: String,
    val color: Int,
    val ctag: String? = null,
    val syncToken: String? = null,
    val enabled: Boolean = true,
    val systemCalendarId: Long? = null
)

/** Evento del calendario. Las fechas se guardan como epoch millis UTC. */
data class Event(
    val id: Long = 0,
    val calendarId: Long,
    val remoteUid: String? = null,
    val remoteHref: String? = null,
    val etag: String? = null,
    val title: String,
    val description: String = "",
    val location: String = "",
    val dtstart: Long,
    val dtend: Long,
    val allDay: Boolean = false,
    val rrule: String? = null,
    val reminderMinutes: Int = -1,
    val dirty: Boolean = false,
    val deleted: Boolean = false,
    val systemEventId: Long? = null
)

/** Ocurrencia concreta de un evento (para eventos recurrentes se expande). */
data class Occurrence(
    val event: Event,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean
)
