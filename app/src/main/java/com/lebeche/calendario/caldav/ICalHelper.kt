package com.lebeche.calendario.caldav

import biweekly.Biweekly
import biweekly.ICalendar
import biweekly.component.VEvent
import biweekly.property.DateEnd
import biweekly.property.DateStart
import com.lebeche.calendario.data.Event
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.TimeZone

/** Conversión entre el modelo interno [Event] y el formato iCalendar (RFC 5545). */
object ICalHelper {

    fun parse(data: String, calendarId: Long, href: String, etag: String?): Event? {
        val ical: ICalendar? = Biweekly.parse(data).all().firstOrNull()
        val v: VEvent = ical?.getEvents()?.firstOrNull() ?: return null

        val dateStart = v.getDateStart()?.getValue() // ICalDate extiende java.util.Date
        val dateEnd = v.getDateEnd()?.getValue()
        val allDay = dateStart?.hasTime() == false
        val start = dateStart?.let { toEpochMillis(it, allDay) } ?: 0L
        val end = when {
            dateEnd != null -> toEpochMillis(dateEnd, allDay)
            allDay -> start + 86_400_000L
            else -> start
        }

        return Event(
            calendarId = calendarId,
            remoteUid = v.getUid()?.getValue(),
            remoteHref = href,
            etag = etag,
            title = v.getSummary()?.getValue() ?: "(sin título)",
            description = v.getDescription()?.getValue() ?: "",
            location = v.getLocation()?.getValue() ?: "",
            categories = extractCategories(data),
            dtstart = start,
            dtend = end,
            allDay = allDay,
            rrule = extractRrule(data),
            reminderMinutes = extractReminderMinutes(data)
        )
    }

    fun serialize(e: Event): String {
        val ical = ICalendar()
        val v = VEvent()
        if (!e.remoteUid.isNullOrBlank()) v.setUid(e.remoteUid)
        v.setSummary(e.title)
        if (e.description.isNotBlank()) v.setDescription(e.description)
        if (e.location.isNotBlank()) v.setLocation(e.location)
        v.setDateStart(DateStart(Date(e.dtstart), !e.allDay))
        v.setDateEnd(DateEnd(Date(e.dtend), !e.allDay))
        ical.addEvent(v)
        var out = Biweekly.write(ical).go()

        // Se inyectan las propiedades que no se modelan con biweekly: RRULE,
        // VALARM (recordatorio) y CATEGORIES (etiquetas), como en RFC 5545.
        val extra = StringBuilder()
        if (!e.rrule.isNullOrBlank()) {
            extra.append("RRULE:").append(e.rrule).append("\r\n")
        }
        if (e.reminderMinutes >= 0) {
            extra.append("BEGIN:VALARM\r\n")
                .append("ACTION:DISPLAY\r\n")
                .append("TRIGGER:").append(triggerIso(e.reminderMinutes)).append("\r\n")
                .append("END:VALARM\r\n")
        }
        if (e.categories.isNotEmpty()) {
            extra.append("CATEGORIES:").append(e.categories.joinToString(",")).append("\r\n")
        }
        if (extra.isNotEmpty()) {
            out = out.replaceFirst("END:VEVENT", extra.toString() + "END:VEVENT")
        }
        return out
    }

    /** Convierte los minutos previos al inicio en un TRIGGER ISO-8601 negativo. */
    private fun triggerIso(minutes: Int): String = when {
        minutes <= 0 -> "PT0S"
        minutes % 1440 == 0 -> "-P${minutes / 1440}D"
        else -> "-PT${minutes}M"
    }

    /** Extrae los minutos de antelación del primer VALARM con ACTION:DISPLAY. */
    private fun extractReminderMinutes(data: String): Int {
        val lines = data.replace("\r\n", "\n").split("\n")
        var i = 0
        while (i < lines.size) {
            if (lines[i].trimStart().startsWith("BEGIN:VALARM", ignoreCase = true)) {
                var j = i + 1
                val block = mutableListOf<String>()
                while (j < lines.size && !lines[j].trimStart().startsWith("END:VALARM", ignoreCase = true)) {
                    block.add(lines[j])
                    j++
                }
                val idx = block.indexOfFirst { it.trimStart().startsWith("TRIGGER", ignoreCase = true) }
                if (idx >= 0) {
                    val sb = StringBuilder(block[idx].substringAfter(':'))
                    var k = idx + 1
                    while (k < block.size && (block[k].startsWith(" ") || block[k].startsWith("\t"))) {
                        sb.append(block[k].trim())
                        k++
                    }
                    val m = parseTriggerMinutes(sb.toString())
                    if (m >= 0) return m
                }
                i = j + 1
            } else {
                i++
            }
        }
        return -1
    }

    /** Interpreta una duración ISO-8601: solo soportamos "antes del inicio". */
    private fun parseTriggerMinutes(value: String): Int {
        val s = value.trim()
        if (!s.startsWith("P")) return -1
        val negative = s.startsWith("-P")
        val t = if (negative) s.substring(1) else s
        val m = Regex("""P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?""").matchEntire(t) ?: return -1
        fun g(i: Int): Long = m.groupValues[i].toLongOrNull() ?: 0L
        val total = g(1) * 1440L + g(2) * 60L + g(3) + if (g(4) > 0L) 1L else 0L
        if (total == 0L) return 0
        if (!negative) return -1 // recordatorio posterior al inicio: no soportado
        return total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    /** Extrae la propiedad CATEGORIES (varias líneas permitidas, separadas por comas). */
    private fun extractCategories(data: String): List<String> {
        val lines = data.replace("\r\n", "\n").split("\n")
        val result = mutableListOf<String>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.startsWith("CATEGORIES", ignoreCase = true) && line.contains(':')) {
                val sb = StringBuilder(line.substringAfter(':'))
                var j = i + 1
                while (j < lines.size && (lines[j].startsWith(" ") || lines[j].startsWith("\t"))) {
                    sb.append(lines[j].trim())
                    j++
                }
                sb.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { result.add(it) }
                i = j
            } else {
                i++
            }
        }
        return result.distinct()
    }

    /**
     * Devuelve las ocurrencias (inicio, fin) de un evento dentro del rango [from, to].
     * Para eventos recurrentes se expande la RRULE usando biweekly.
     */
    fun expand(event: Event, from: Long, to: Long): List<Pair<Long, Long>> {
        val duration = if (event.dtend > event.dtstart) event.dtend - event.dtstart else 0L
        if (event.rrule.isNullOrBlank()) {
            return if (event.dtstart < to && (event.dtend > from || event.dtstart > from)) {
                listOf(event.dtstart to (event.dtstart + duration))
            } else {
                emptyList()
            }
        }

        val v = buildRecurringVEvent(event) ?: return emptyList()
        val it = v.getDateIterator(TimeZone.getTimeZone("UTC"))
        val result = mutableListOf<Pair<Long, Long>>()
        var count = 0
        while (it.hasNext() && count < 10000) {
            val d = it.next()
            val startMillis = d.time
            if (startMillis >= to) break
            if (startMillis + duration > from) {
                result.add(startMillis to (startMillis + duration))
            }
            count++
        }
        return result
    }

    private fun buildRecurringVEvent(event: Event): VEvent? {
        val rrule = event.rrule ?: return null
        val dt = if (event.allDay) {
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC)
                .format(Instant.ofEpochMilli(event.dtstart))
        } else {
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
                .format(Instant.ofEpochMilli(event.dtstart))
        }
        val icalStr = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Lebeche//Calendario//ES\r\n" +
            "BEGIN:VEVENT\r\nDTSTART:$dt\r\nRRULE:$rrule\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n"
        return Biweekly.parse(icalStr).all().firstOrNull()?.getEvents()?.firstOrNull()
    }

    private fun extractRrule(data: String): String? {
        val lines = data.split("\r\n", "\n")
        for (i in lines.indices) {
            val line = lines[i]
            if (line.startsWith("RRULE", ignoreCase = true) && line.contains(":")) {
                val sb = StringBuilder(line.substringAfter(':'))
                var j = i + 1
                while (j < lines.size && (lines[j].startsWith(" ") || lines[j].startsWith("\t"))) {
                    sb.append(lines[j].trim())
                    j++
                }
                return sb.toString().trim()
            }
        }
        return null
    }

    /**
     * Convierte un ICalDate a epoch millis. Para eventos "todo el día", biweekly
     * representa la fecha a las 00:00 en la zona local del dispositivo; lo
     * normalizamos a medianoche UTC para que no se desplace un día.
     */
    private fun toEpochMillis(d: java.util.Date, allDay: Boolean): Long {
        if (!allDay) return d.time
        val c = java.util.Calendar.getInstance()
        c.time = d
        return LocalDate.of(
            c.get(java.util.Calendar.YEAR),
            c.get(java.util.Calendar.MONTH) + 1,
            c.get(java.util.Calendar.DAY_OF_MONTH)
        ).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }
}