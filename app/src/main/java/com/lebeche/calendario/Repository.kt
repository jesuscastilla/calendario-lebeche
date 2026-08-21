package com.lebeche.calendario

import android.content.Context
import com.lebeche.calendario.cal.SystemCalendarSync
import com.lebeche.calendario.caldav.CalDavClient
import com.lebeche.calendario.caldav.ICalHelper
import com.lebeche.calendario.data.Account
import com.lebeche.calendario.data.CalInfo
import com.lebeche.calendario.data.Db
import com.lebeche.calendario.data.Event
import com.lebeche.calendario.data.Occurrence
import com.lebeche.calendario.notif.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SyncSummary(val errors: MutableList<String> = mutableListOf())

/** Punto de acceso a datos + lógica de sincronización para la interfaz. */
class Repository private constructor(private val context: Context) {

    private val db = Db.get(context)
    private val caldav = CalDavClient()

    companion object {
        @Volatile
        private var instance: Repository? = null

        fun get(context: Context): Repository =
            instance ?: synchronized(this) {
                instance ?: Repository(context.applicationContext).also { instance = it }
            }
    }

    // ------------------------------------------------------------------ lectura

    suspend fun accounts(): List<Account> = withContext(Dispatchers.IO) { db.getAccounts() }

    suspend fun calendars(accountId: Long): List<CalInfo> =
        withContext(Dispatchers.IO) { db.getCalendars(accountId) }

    suspend fun allCalendars(): List<CalInfo> = withContext(Dispatchers.IO) { db.getAllCalendars() }

    suspend fun event(id: Long): Event? = withContext(Dispatchers.IO) { db.getEvent(id) }

    /** Ocurrencias (incluye expansión de recurrentes) dentro de un rango temporal. */
    suspend fun occurrences(from: Long, to: Long): List<Occurrence> = withContext(Dispatchers.IO) {
        val enabled = db.getAllCalendars().filter { it.enabled }.map { it.id }.toSet()
        val result = mutableListOf<Occurrence>()
        for (e in db.getAllEvents()) {
            if (e.deleted || e.calendarId !in enabled) continue
            for ((s, en) in ICalHelper.expand(e, from, to)) {
                result.add(Occurrence(e, s, en, e.allDay))
            }
        }
        result.sortedBy { it.startMillis }
    }

    // ------------------------------------------------------------------ escritura de eventos

    suspend fun saveEvent(event: Event): Long = withContext(Dispatchers.IO) {
        val uid = if (event.remoteUid.isNullOrBlank()) java.util.UUID.randomUUID().toString() else event.remoteUid
        val e = event.copy(remoteUid = uid, dirty = true)

        val id: Long = if (e.id == 0L) db.insertEvent(e) else {
            db.updateEvent(e)
            e.id
        }

        val saved = db.getEvent(id) ?: return@withContext id
        val cal = db.getCalendar(saved.calendarId)

        if (cal != null) {
            SystemCalendarSync.upsertEvent(context, saved, cal)?.let { db.setEventSystemId(id, it) }
        }
        ReminderScheduler.scheduleForEvent(context, saved)

        try {
            val account = cal?.let { db.getAccount(it.accountId) }
            if (account != null && cal != null) {
                val put = caldav.putEvent(account, cal, saved, ICalHelper.serialize(saved), saved.etag)
                db.markEventRemote(id, uid, put.href, put.etag)
                db.setEventDirty(id, false)
            }
        } catch (ex: Exception) {
            // se subirá en la próxima sincronización
        }
        id
    }

    suspend fun deleteEvent(id: Long) = withContext(Dispatchers.IO) {
        val e = db.getEvent(id) ?: return@withContext
        SystemCalendarSync.deleteEvent(context, e)
        ReminderScheduler.cancelForEvent(context, id)

        val cal = db.getCalendar(e.calendarId)
        if (e.remoteHref != null && cal != null) {
            try {
                val account = db.getAccount(cal.accountId)
                if (account != null) {
                    caldav.deleteEvent(account, cal, e.remoteHref, e.etag)
                    db.deleteEvent(id)
                } else {
                    db.updateEvent(e.copy(deleted = true))
                }
            } catch (ex: Exception) {
                db.updateEvent(e.copy(deleted = true))
            }
        } else {
            db.deleteEvent(id)
        }
    }

    // ------------------------------------------------------------------ cuentas

    suspend fun addAccount(name: String, baseUrl: String, username: String, password: String, insecureTls: Boolean): Pair<Long, Int> =
        withContext(Dispatchers.IO) {
            val id = db.insertAccount(name, baseUrl, username, password, insecureTls)
            val account = Account(id, name, baseUrl, username, password, insecureTls)
            var discovered = 0
            try {
                for (c in caldav.discover(account)) {
                    db.insertCalendar(c.copy(accountId = id))
                    discovered++
                }
            } catch (ex: Exception) {
                // la cuenta se guarda igualmente; se puede reintentar sincronizando
            }
            id to discovered
        }

    suspend fun deleteAccount(id: Long) = withContext(Dispatchers.IO) {
        for (cal in db.getCalendars(id)) {
            for (e in db.getEventsByCalendar(cal.id)) {
                SystemCalendarSync.deleteEvent(context, e)
                ReminderScheduler.cancelForEvent(context, e.id)
            }
            SystemCalendarSync.deleteCalendar(context, cal)
        }
        db.deleteAccount(id)
    }

    suspend fun setCalendarEnabled(id: Long, enabled: Boolean) = withContext(Dispatchers.IO) {
        db.setCalendarEnabled(id, enabled)
    }

    // ------------------------------------------------------------------ sincronización

    suspend fun syncAll(): SyncSummary = withContext(Dispatchers.IO) {
        val summary = SyncSummary()
        for (account in db.getAccounts()) {
            try {
                syncAccount(account)
            } catch (e: Exception) {
                summary.errors.add("${account.name}: ${e.message ?: "error"}")
            }
        }
        ReminderScheduler.rescheduleAll(context)
        summary
    }

    suspend fun syncAccountNow(id: Long): SyncSummary = withContext(Dispatchers.IO) {
        val summary = SyncSummary()
        val account = db.getAccount(id)
        if (account != null) {
            try {
                syncAccount(account)
            } catch (e: Exception) {
                summary.errors.add("${account.name}: ${e.message ?: "error"}")
            }
        }
        ReminderScheduler.rescheduleAll(context)
        summary
    }

    private fun syncAccount(account: Account) {
        val calendars = db.getCalendars(account.id).filter { it.enabled }
        for (cal in calendars) {
            pushDirty(account, cal)
            pull(account, cal)
        }
        db.updateAccountSyncTime(account.id, System.currentTimeMillis())
    }

    private fun pushDirty(account: Account, cal: CalInfo) {
        for (e in db.getEventsByCalendar(cal.id)) {
            if (e.deleted) {
                if (e.remoteHref != null) {
                    try {
                        caldav.deleteEvent(account, cal, e.remoteHref, e.etag)
                        db.deleteEvent(e.id)
                        SystemCalendarSync.deleteEvent(context, e)
                    } catch (ex: Exception) {
                        // reintentar más tarde
                    }
                } else {
                    db.deleteEvent(e.id)
                    SystemCalendarSync.deleteEvent(context, e)
                }
            } else if (e.dirty) {
                try {
                    val put = caldav.putEvent(account, cal, e, ICalHelper.serialize(e), e.etag)
                    db.markEventRemote(e.id, e.remoteUid, put.href, put.etag)
                    db.setEventDirty(e.id, false)
                    val updated = db.getEvent(e.id)
                    if (updated != null) {
                        SystemCalendarSync.upsertEvent(context, updated, cal)?.let { db.setEventSystemId(e.id, it) }
                    }
                } catch (ex: Exception) {
                    // queda dirty para la próxima vez
                }
            }
        }
    }

    private fun pull(account: Account, cal: CalInfo) {
        val result = caldav.fetchEvents(account, cal, cal.syncToken)

        val local = db.getEventsByCalendar(cal.id)
        val byHref = local.filter { it.remoteHref != null }.associateBy { it.remoteHref!! }
        val byUid = local.filter { it.remoteUid != null }.associateBy { it.remoteUid!! }

        for (href in result.deletedHrefs) {
            val ev = byHref[href]
            if (ev != null) {
                db.deleteEvent(ev.id)
                SystemCalendarSync.deleteEvent(context, ev)
            }
        }

        for (re in result.events) {
            val parsed = ICalHelper.parse(re.icalData ?: continue, cal.id, re.href, re.etag) ?: continue
            val existing = parsed.remoteUid?.let { byUid[it] } ?: byHref[re.href]
            if (existing != null) {
                val updated = parsed.copy(
                    id = existing.id,
                    reminderMinutes = existing.reminderMinutes,
                    systemEventId = existing.systemEventId,
                    dirty = existing.dirty
                )
                db.updateEvent(updated)
                if (!existing.dirty) {
                    SystemCalendarSync.upsertEvent(context, updated, cal)?.let { db.setEventSystemId(existing.id, it) }
                }
            } else {
                val newId = db.insertEvent(parsed)
                val ev = parsed.copy(id = newId)
                SystemCalendarSync.upsertEvent(context, ev, cal)?.let { db.setEventSystemId(newId, it) }
            }
        }

        db.updateCalendarSync(cal.id, cal.ctag, result.newSyncToken)
    }
}

