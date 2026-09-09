package com.lebeche.calendario.ui

/** Etiquetas de recordatorio compartidas por Ajustes y el editor de eventos. */
fun reminderLabel(minutes: Int, defaultMinutes: Int? = null): String = when (minutes) {
    -2 -> "Sin recordatorio"
    -1 -> if (defaultMinutes != null && defaultMinutes >= 0) {
        "Por defecto (" + reminderLabel(defaultMinutes) + ")"
    } else {
        "Sin recordatorio"
    }
    0 -> "En el momento"
    5 -> "5 minutos antes"
    10 -> "10 minutos antes"
    15 -> "15 minutos antes"
    30 -> "30 minutos antes"
    60 -> "1 hora antes"
    1440 -> "1 día antes"
    else -> if (minutes > 0) "$minutes minutos antes" else "Sin recordatorio"
}

/** Opciones para el selector del recordatorio por defecto global (Ajustes). */
fun defaultReminderChoices(): List<Int> = listOf(-1, 0, 5, 10, 15, 30, 60, 1440)

/** Opciones por evento: por defecto (si existe), sin aviso y valores fijos. */
fun eventReminderChoices(defaultMinutes: Int): List<Int> {
    val list = mutableListOf<Int>()
    if (defaultMinutes >= 0) list.add(-1)
    list.add(-2)
    list.addAll(listOf(0, 5, 10, 15, 30, 60, 1440).filter { it != defaultMinutes || defaultMinutes < 0 })
    return list
}