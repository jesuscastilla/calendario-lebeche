package com.lebeche.calendario.data

import android.content.Context
import androidx.core.content.edit

/** Preferencias locales de la app (recordatorio por defecto, etc.). */
object Prefs {
    private const val FILE = "calendario_prefs"
    private const val KEY_DEFAULT_REMINDER = "default_reminder_minutes"

    /** Recordatorio por defecto (minutos antes) aplicado a eventos sin aviso propio. */
    const val DEFAULT_DEFAULT_REMINDER = 15

    fun defaultReminderMinutes(context: Context): Int =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getInt(KEY_DEFAULT_REMINDER, DEFAULT_DEFAULT_REMINDER)

    fun setDefaultReminderMinutes(context: Context, minutes: Int) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit { putInt(KEY_DEFAULT_REMINDER, minutes) }
    }
}