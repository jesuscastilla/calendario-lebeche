package com.lebeche.calendario.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Base de datos local (SQLite) de la aplicación.
 * Almacena cuentas, calendarios remotos y eventos; la contraseña va cifrada.
 */
class Db(context: Context) : SQLiteOpenHelper(context.applicationContext, "calendario.db", null, 2) {

    companion object {
        @Volatile
        private var instance: Db? = null

        fun get(context: Context): Db =
            instance ?: synchronized(this) {
                instance ?: Db(context).also { instance = it }
            }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE accounts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                base_url TEXT NOT NULL,
                username TEXT NOT NULL,
                password TEXT NOT NULL,
                insecure_tls INTEGER NOT NULL DEFAULT 0,
                last_sync_at INTEGER
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE calendars (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                account_id INTEGER NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
                href TEXT NOT NULL,
                display_name TEXT NOT NULL,
                color INTEGER NOT NULL DEFAULT 0,
                ctag TEXT,
                sync_token TEXT,
                enabled INTEGER NOT NULL DEFAULT 1,
                read_only INTEGER NOT NULL DEFAULT 0,
                system_calendar_id INTEGER
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                calendar_id INTEGER NOT NULL REFERENCES calendars(id) ON DELETE CASCADE,
                remote_uid TEXT,
                remote_href TEXT,
                etag TEXT,
                title TEXT NOT NULL,
                description TEXT NOT NULL DEFAULT '',
                location TEXT NOT NULL DEFAULT '',
                dtstart INTEGER NOT NULL,
                dtend INTEGER NOT NULL,
                all_day INTEGER NOT NULL DEFAULT 0,
                rrule TEXT,
                reminder_minutes INTEGER NOT NULL DEFAULT -1,
                dirty INTEGER NOT NULL DEFAULT 0,
                deleted INTEGER NOT NULL DEFAULT 0,
                system_event_id INTEGER
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE calendars ADD COLUMN read_only INTEGER NOT NULL DEFAULT 0")
        }
    }

    // ------------------------------------------------------------------ cuentas

    fun insertAccount(name: String, baseUrl: String, username: String, password: String, insecureTls: Boolean): Long {
        val cv = ContentValues().apply {
            put("name", name)
            put("base_url", baseUrl)
            put("username", username)
            put("password", Crypto.encrypt(password))
            put("insecure_tls", if (insecureTls) 1 else 0)
        }
        return writableDatabase.insertOrThrow("accounts", null, cv)
    }

    fun getAccounts(): List<Account> {
        val list = mutableListOf<Account>()
        readableDatabase.rawQuery("SELECT * FROM accounts ORDER BY id", null).use { c ->
            while (c.moveToNext()) list.add(readAccount(c))
        }
        return list
    }

    fun getAccount(id: Long): Account? {
        readableDatabase.rawQuery("SELECT * FROM accounts WHERE id=?", arrayOf(id.toString())).use { c ->
            return if (c.moveToFirst()) readAccount(c) else null
        }
    }

    fun deleteAccount(id: Long) {
        writableDatabase.delete("accounts", "id=?", arrayOf(id.toString()))
    }

    fun updateAccountSyncTime(id: Long, millis: Long) {
        val cv = ContentValues().apply { put("last_sync_at", millis) }
        writableDatabase.update("accounts", cv, "id=?", arrayOf(id.toString()))
    }

    private fun readAccount(c: Cursor): Account = Account(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        name = c.getString(c.getColumnIndexOrThrow("name")),
        baseUrl = c.getString(c.getColumnIndexOrThrow("base_url")),
        username = c.getString(c.getColumnIndexOrThrow("username")),
        password = Crypto.decrypt(c.getString(c.getColumnIndexOrThrow("password"))),
        insecureTls = c.getInt(c.getColumnIndexOrThrow("insecure_tls")) == 1,
        lastSyncAt = c.getLong(c.getColumnIndexOrThrow("last_sync_at")).let {
            if (c.isNull(c.getColumnIndexOrThrow("last_sync_at"))) null else it
        }
    )

    // ----------------------------------------------------------------- calendarios

    fun insertCalendar(cal: CalInfo): Long {
        val cv = ContentValues().apply {
            put("account_id", cal.accountId)
            put("href", cal.href)
            put("display_name", cal.displayName)
            put("color", cal.color)
            put("ctag", cal.ctag)
            put("sync_token", cal.syncToken)
            put("enabled", if (cal.enabled) 1 else 0)
            put("read_only", if (cal.readOnly) 1 else 0)
            if (cal.systemCalendarId != null) put("system_calendar_id", cal.systemCalendarId)
        }
        return writableDatabase.insertOrThrow("calendars", null, cv)
    }

    fun getCalendars(accountId: Long): List<CalInfo> {
        val list = mutableListOf<CalInfo>()
        readableDatabase.rawQuery(
            "SELECT * FROM calendars WHERE account_id=? ORDER BY id",
            arrayOf(accountId.toString())
        ).use { c -> while (c.moveToNext()) list.add(readCalendar(c)) }
        return list
    }

    fun getAllCalendars(): List<CalInfo> {
        val list = mutableListOf<CalInfo>()
        readableDatabase.rawQuery("SELECT * FROM calendars ORDER BY id", null).use { c ->
            while (c.moveToNext()) list.add(readCalendar(c))
        }
        return list
    }

    fun getCalendar(id: Long): CalInfo? {
        readableDatabase.rawQuery("SELECT * FROM calendars WHERE id=?", arrayOf(id.toString())).use { c ->
            return if (c.moveToFirst()) readCalendar(c) else null
        }
    }

    fun updateCalendarSync(id: Long, ctag: String?, syncToken: String?) {
        val cv = ContentValues().apply {
            put("ctag", ctag)
            put("sync_token", syncToken)
        }
        writableDatabase.update("calendars", cv, "id=?", arrayOf(id.toString()))
    }

    fun setCalendarEnabled(id: Long, enabled: Boolean) {
        val cv = ContentValues().apply { put("enabled", if (enabled) 1 else 0) }
        writableDatabase.update("calendars", cv, "id=?", arrayOf(id.toString()))
    }

    fun setCalendarSystemId(id: Long, systemId: Long?) {
        val cv = ContentValues().apply { put("system_calendar_id", systemId) }
        writableDatabase.update("calendars", cv, "id=?", arrayOf(id.toString()))
    }

    private fun readCalendar(c: Cursor): CalInfo = CalInfo(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        accountId = c.getLong(c.getColumnIndexOrThrow("account_id")),
        href = c.getString(c.getColumnIndexOrThrow("href")),
        displayName = c.getString(c.getColumnIndexOrThrow("display_name")),
        color = c.getInt(c.getColumnIndexOrThrow("color")),
        ctag = c.getString(c.getColumnIndexOrThrow("ctag")),
        syncToken = c.getString(c.getColumnIndexOrThrow("sync_token")),
        enabled = c.getInt(c.getColumnIndexOrThrow("enabled")) == 1,
        readOnly = c.getInt(c.getColumnIndexOrThrow("read_only")) == 1,
        systemCalendarId = c.getLong(c.getColumnIndexOrThrow("system_calendar_id")).let {
            if (c.isNull(c.getColumnIndexOrThrow("system_calendar_id"))) null else it
        }
    )

    // -------------------------------------------------------------------- eventos

    fun insertEvent(e: Event): Long {
        val cv = eventValues(e)
        return writableDatabase.insertOrThrow("events", null, cv)
    }

    fun updateEvent(e: Event) {
        writableDatabase.update("events", eventValues(e), "id=?", arrayOf(e.id.toString()))
    }

    fun getEvent(id: Long): Event? {
        readableDatabase.rawQuery("SELECT * FROM events WHERE id=?", arrayOf(id.toString())).use { c ->
            return if (c.moveToFirst()) readEvent(c) else null
        }
    }

    fun getEventsByCalendar(calendarId: Long): List<Event> {
        val list = mutableListOf<Event>()
        readableDatabase.rawQuery(
            "SELECT * FROM events WHERE calendar_id=? ORDER BY dtstart",
            arrayOf(calendarId.toString())
        ).use { c -> while (c.moveToNext()) list.add(readEvent(c)) }
        return list
    }

    fun getAllEvents(): List<Event> {
        val list = mutableListOf<Event>()
        readableDatabase.rawQuery("SELECT * FROM events ORDER BY dtstart", null).use { c ->
            while (c.moveToNext()) list.add(readEvent(c))
        }
        return list
    }

    fun findEventByUid(calendarId: Long, uid: String): Event? {
        readableDatabase.rawQuery(
            "SELECT * FROM events WHERE calendar_id=? AND remote_uid=?",
            arrayOf(calendarId.toString(), uid)
        ).use { c -> return if (c.moveToFirst()) readEvent(c) else null }
    }

    fun deleteEvent(id: Long) {
        writableDatabase.delete("events", "id=?", arrayOf(id.toString()))
    }

    fun markEventRemote(id: Long, uid: String?, href: String?, etag: String?) {
        val cv = ContentValues().apply {
            put("remote_uid", uid)
            put("remote_href", href)
            put("etag", etag)
        }
        writableDatabase.update("events", cv, "id=?", arrayOf(id.toString()))
    }

    fun setEventDirty(id: Long, dirty: Boolean) {
        val cv = ContentValues().apply { put("dirty", if (dirty) 1 else 0) }
        writableDatabase.update("events", cv, "id=?", arrayOf(id.toString()))
    }

    fun setEventSystemId(id: Long, systemId: Long?) {
        val cv = ContentValues().apply { put("system_event_id", systemId) }
        writableDatabase.update("events", cv, "id=?", arrayOf(id.toString()))
    }

    private fun eventValues(e: Event): ContentValues = ContentValues().apply {
        put("calendar_id", e.calendarId)
        put("remote_uid", e.remoteUid)
        put("remote_href", e.remoteHref)
        put("etag", e.etag)
        put("title", e.title)
        put("description", e.description)
        put("location", e.location)
        put("dtstart", e.dtstart)
        put("dtend", e.dtend)
        put("all_day", if (e.allDay) 1 else 0)
        put("rrule", e.rrule)
        put("reminder_minutes", e.reminderMinutes)
        put("dirty", if (e.dirty) 1 else 0)
        put("deleted", if (e.deleted) 1 else 0)
        put("system_event_id", e.systemEventId)
    }

    private fun readEvent(c: Cursor): Event = Event(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        calendarId = c.getLong(c.getColumnIndexOrThrow("calendar_id")),
        remoteUid = c.getString(c.getColumnIndexOrThrow("remote_uid")),
        remoteHref = c.getString(c.getColumnIndexOrThrow("remote_href")),
        etag = c.getString(c.getColumnIndexOrThrow("etag")),
        title = c.getString(c.getColumnIndexOrThrow("title")),
        description = c.getString(c.getColumnIndexOrThrow("description")),
        location = c.getString(c.getColumnIndexOrThrow("location")),
        dtstart = c.getLong(c.getColumnIndexOrThrow("dtstart")),
        dtend = c.getLong(c.getColumnIndexOrThrow("dtend")),
        allDay = c.getInt(c.getColumnIndexOrThrow("all_day")) == 1,
        rrule = c.getString(c.getColumnIndexOrThrow("rrule")),
        reminderMinutes = c.getInt(c.getColumnIndexOrThrow("reminder_minutes")),
        dirty = c.getInt(c.getColumnIndexOrThrow("dirty")) == 1,
        deleted = c.getInt(c.getColumnIndexOrThrow("deleted")) == 1,
        systemEventId = c.getLong(c.getColumnIndexOrThrow("system_event_id")).let {
            if (c.isNull(c.getColumnIndexOrThrow("system_event_id"))) null else it
        }
    )
}
