package com.lebeche.calendario.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lebeche.calendario.data.Occurrence
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DefaultCalendarColor = 0xFF8FD6EF.toInt()

private fun occurrenceDate(o: Occurrence): LocalDate =
    if (o.allDay) Instant.ofEpochMilli(o.startMillis).atZone(ZoneOffset.UTC).toLocalDate()
    else Instant.ofEpochMilli(o.startMillis).atZone(ZoneId.systemDefault()).toLocalDate()

private fun timeLabel(o: Occurrence): String =
    if (o.allDay) "Todo el día"
    else DateTimeFormatter.ofPattern("HH:mm").format(
        Instant.ofEpochMilli(o.startMillis).atZone(ZoneId.systemDefault())
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onOpenEvent: (Long) -> Unit,
    onCreateEvent: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val vm: MainViewModel = viewModel()
    val colorMap = vm.calendars.associate { it.id to it.color }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calendario Lebeche") },
                actions = {
                    if (vm.isSyncing) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                    IconButton(onClick = { vm.sync() }) { Icon(Icons.Filled.Sync, "Sincronizar") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "Ajustes") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateEvent) { Icon(Icons.Filled.Add, "Nuevo evento") }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            vm.syncMessage?.let {
                Text(it, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.secondary)
            }
            MonthHeader(vm)
            MonthGrid(vm.month, vm.selectedDate, vm.occurrences, colorMap, vm::select)
            DayAgenda(Modifier.weight(1f), vm.selectedDate, vm.occurrences, colorMap, onOpenEvent)
        }
    }
}

@Composable
private fun MonthHeader(vm: MainViewModel) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { vm.prevMonth() }) { Icon(Icons.Filled.ChevronLeft, "Mes anterior") }
        Text(
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale("es", "ES")).format(vm.month),
            Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium
        )
        IconButton(onClick = { vm.nextMonth() }) { Icon(Icons.Filled.ChevronRight, "Mes siguiente") }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    selected: LocalDate,
    occurrences: List<Occurrence>,
    colorMap: Map<Long, Int>,
    onSelect: (LocalDate) -> Unit
) {
    val byDay = occurrences.groupBy { occurrenceDate(it) }
    val weekdays = listOf("L", "M", "X", "J", "V", "S", "D")
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        weekdays.forEach { w ->
            Text(
                w, Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }

    val offset = month.atDay(1).dayOfWeek.value - 1
    val cells = mutableListOf<LocalDate?>()
    repeat(offset) { cells.add(null) }
    for (d in 1..month.lengthOfMonth()) cells.add(month.atDay(d))
    while (cells.size < 42) cells.add(null)

    cells.chunked(7).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            row.forEach { date ->
                DayCell(
                    modifier = Modifier.weight(1f).aspectRatio(1f),
                    date = date,
                    selected = selected,
                    events = byDay[date].orEmpty(),
                    colorMap = colorMap,
                    onSelect = onSelect
                )
            }
        }
    }
}

@Composable
private fun DayCell(
    modifier: Modifier = Modifier,
    date: LocalDate?,
    selected: LocalDate,
    events: List<Occurrence>,
    colorMap: Map<Long, Int>,
    onSelect: (LocalDate) -> Unit
) {
    Box(
        modifier.clickable(enabled = date != null) { date?.let(onSelect) },
        contentAlignment = Alignment.Center
    ) {
        if (date != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val isSel = date == selected
                val bg = if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent
                val fg = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                Box(Modifier.size(30.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
                    Text(date.dayOfMonth.toString(), color = fg, style = MaterialTheme.typography.bodyMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    events.take(3).forEach { e ->
                        Box(
                            Modifier.size(5.dp).clip(CircleShape)
                                .background(Color(colorMap[e.event.calendarId] ?: DefaultCalendarColor))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayAgenda(
    modifier: Modifier,
    selected: LocalDate,
    occurrences: List<Occurrence>,
    colorMap: Map<Long, Int>,
    onOpenEvent: (Long) -> Unit
) {
    val dayOcc = occurrences.filter { occurrenceDate(it) == selected }.sortedBy { it.startMillis }
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale("es", "ES")).format(selected),
            style = MaterialTheme.typography.titleSmall
        )
        Spacer(Modifier.height(4.dp))
        if (dayOcc.isEmpty()) {
            Text(
                "Sin eventos este día", Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        } else {
            LazyColumn(Modifier.fillMaxHeight()) {
                items(dayOcc, key = { it.event.id.toString() + "_" + it.startMillis }) { o ->
                    EventRow(o, colorMap, onOpenEvent)
                }
            }
        }
    }
}

@Composable
private fun EventRow(o: Occurrence, colorMap: Map<Long, Int>, onOpenEvent: (Long) -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { onOpenEvent(o.event.id) }
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(10.dp).clip(CircleShape)
                    .background(Color(colorMap[o.event.calendarId] ?: DefaultCalendarColor))
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(o.event.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                if (o.event.location.isNotBlank()) {
                    Text(o.event.location, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
            Text(timeLabel(o), style = MaterialTheme.typography.labelMedium)
        }
    }
}

