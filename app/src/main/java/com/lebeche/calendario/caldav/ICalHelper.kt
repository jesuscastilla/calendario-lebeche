package com.lebeche.calendario.caldav

import biweekly.Biweekly
import biweekly.ICalendar
import biweekly.component.VEvent
import biweekly.property.DateEnd
import biweekly.property.DateStart
import com.lebeche.calendario.data.Event
import java.time.Instant
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
        val start = dateStart?.time ?: 0L
        val end = dateEnd?.time ?: start

        return Event(
            calendarId = calendarId,
            remoteUid = v.getUid()?.getValue(),
            remoteHref = href,
            etag = etag,
            title = v.getSummary()?.getValue() ?: "(sin título)",
            description = v.getDescription()?.getValue() ?: "",
            location = v.getLocation()?.getValue() ?: "",
            dtstart = start,
            dtend = end,
            allDay = allDay,
            rrule = extractRrule(data),
            reminderMinutes = -1
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
        if (!e.rrule.isNullOrBlank()) {
            out = out.replaceFirst("END:VEVENT", "RRULE:${e.rrule}\r\nEND:VEVENT")
        }
        return out
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
}
