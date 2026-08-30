package com.lebeche.calendario.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.lebeche.calendario.Repository
import com.lebeche.calendario.data.CalInfo
import com.lebeche.calendario.data.Event
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault())

private fun reminderLabel(minutes: Int): String = when (minutes) {
    -1 -> "Sin recordatorio"
    0 -> "En el momento"
    10 -> "10 minutos antes"
    30 -> "30 minutos antes"
    60 -> "1 hora antes"
    1440 -> "1 día antes"
    else -> "$minutes min antes"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditScreen(eventId: Long?, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val repo = remember { Repository.get(context.applicationContext) }

    var loading by remember { mutableStateOf(true) }
    var title by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var allDay by remember { mutableStateOf(false) }
    var calendars by remember { mutableStateOf<List<CalInfo>>(emptyList()) }
    var selectedCalendarId by remember { mutableStateOf(0L) }
    var startDate by remember { mutableStateOf(LocalDate.now()) }
    var endDate by remember { mutableStateOf(LocalDate.now()) }
    var startHour by remember { mutableStateOf(9) }
    var startMinute by remember { mutableStateOf(0) }
    var endHour by remember { mutableStateOf(10) }
    var endMinute by remember { mutableStateOf(0) }
    var reminderMinutes by remember { mutableStateOf(-1) }
    var remoteUid by remember { mutableStateOf<String?>(null) }
    var remoteHref by remember { mutableStateOf<String?>(null) }
    var etag by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    LaunchedEffect(eventId) {
        val cals = repo.allCalendars().filter { it.enabled }
        calendars = cals
        if (eventId != null) {
            repo.event(eventId)?.let { e ->
                title = e.title
                location = e.location
                description = e.description
                allDay = e.allDay
                selectedCalendarId = e.calendarId
                reminderMinutes = e.reminderMinutes
                remoteUid = e.remoteUid
                remoteHref = e.remoteHref
                etag = e.etag
                val zone = if (e.allDay) ZoneOffset.UTC else ZoneId.systemDefault()
                val s = Instant.ofEpochMilli(e.dtstart).atZone(zone)
                val en = Instant.ofEpochMilli(e.dtend).atZone(zone)
                startDate = s.toLocalDate()
                endDate = if (e.allDay) en.toLocalDate().minusDays(1) else en.toLocalDate()
                startHour = s.hour
                startMinute = s.minute
                endHour = en.hour
                endMinute = en.minute
            }
        } else {
            selectedCalendarId = cals.firstOrNull { !it.readOnly }?.id ?: cals.firstOrNull()?.id ?: 0L
        }
        loading = false
    }

    fun save() {
        val zone = if (allDay) ZoneOffset.UTC else ZoneId.systemDefault()
        val s = if (allDay) startDate.atStartOfDay(zone).toInstant().toEpochMilli()
        else startDate.atTime(startHour, startMinute).atZone(zone).toInstant().toEpochMilli()
        val en = if (allDay) endDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        else endDate.atTime(endHour, endMinute).atZone(zone).toInstant().toEpochMilli()

        val e = Event(
            id = eventId ?: 0L,
            calendarId = selectedCalendarId,
            remoteUid = remoteUid,
            remoteHref = remoteHref,
            etag = etag,
            title = title.trim().ifBlank { "(sin título)" },
            description = description.trim(),
            location = location.trim(),
            dtstart = s,
            dtend = en,
            allDay = allDay,
            reminderMinutes = reminderMinutes
        )
        scope.launch {
            val result = repo.saveEvent(e)
            if (result.error != null) {
                errorMessage = result.error
            } else {
                onDone()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (eventId == null) "Nuevo evento" else "Editar evento") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                },
                actions = {
                    TextButton(onClick = { save() }, enabled = !loading) { Text("Guardar") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("Título") }, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            errorMessage?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Todo el día", Modifier.weight(1f))
                Switch(checked = allDay, onCheckedChange = { allDay = it })
            }
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = dateFormatter.format(startDate), onValueChange = {}, readOnly = true,
                label = { Text("Fecha inicio") },
                modifier = Modifier.fillMaxWidth().clickable { showStartDatePicker = true },
                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) }
            )
            Spacer(Modifier.height(12.dp))

            if (!allDay) {
                OutlinedTextField(
                    value = String.format(Locale.getDefault(), "%02d:%02d", startHour, startMinute),
                    onValueChange = {}, readOnly = true,
                    label = { Text("Hora inicio") },
                    modifier = Modifier.fillMaxWidth().clickable { showStartTimePicker = true },
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) }
                )
                Spacer(Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = dateFormatter.format(endDate), onValueChange = {}, readOnly = true,
                label = { Text("Fecha fin") },
                modifier = Modifier.fillMaxWidth().clickable { showEndDatePicker = true },
                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) }
            )
            Spacer(Modifier.height(12.dp))

            if (!allDay) {
                OutlinedTextField(
                    value = String.format(Locale.getDefault(), "%02d:%02d", endHour, endMinute),
                    onValueChange = {}, readOnly = true,
                    label = { Text("Hora fin") },
                    modifier = Modifier.fillMaxWidth().clickable { showEndTimePicker = true },
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) }
                )
                Spacer(Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = location, onValueChange = { location = it },
                label = { Text("Lugar") }, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = description, onValueChange = { description = it },
                label = { Text("Descripción") }, modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
            Spacer(Modifier.height(12.dp))

            CalendarSelector(calendars, selectedCalendarId) { selectedCalendarId = it }
            Spacer(Modifier.height(12.dp))

            ReminderSelector(reminderMinutes) { reminderMinutes = it }
            Spacer(Modifier.height(12.dp))

            if (showStartDatePicker) {
                val st = rememberDatePickerState(
                    initialSelectedDateMillis = startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                )
                DatePickerDialog(
                    onDismissRequest = { showStartDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            st.selectedDateMillis?.let { ms ->
                                startDate = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                                if (endDate < startDate) endDate = startDate
                            }
                            showStartDatePicker = false
                        }) { Text("Aceptar") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showStartDatePicker = false }) { Text("Cancelar") }
                    }
                ) { DatePicker(state = st) }
            }

            if (showEndDatePicker) {
                val st = rememberDatePickerState(
                    initialSelectedDateMillis = endDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                )
                DatePickerDialog(
                    onDismissRequest = { showEndDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            st.selectedDateMillis?.let { ms ->
                                endDate = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                                if (endDate < startDate) startDate = endDate
                            }
                            showEndDatePicker = false
                        }) { Text("Aceptar") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showEndDatePicker = false }) { Text("Cancelar") }
                    }
                ) { DatePicker(state = st) }
            }

            if (showStartTimePicker) {
                val tp = rememberTimePickerState(startHour, startMinute, true)
                Dialog(onDismissRequest = { showStartTimePicker = false }) {
                    Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 6.dp) {
                        Column(Modifier.padding(16.dp)) {
                            TimePicker(state = tp)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { showStartTimePicker = false }) { Text("Cancelar") }
                                TextButton(onClick = {
                                    startHour = tp.hour
                                    startMinute = tp.minute
                                    showStartTimePicker = false
                                }) { Text("Aceptar") }
                            }
                        }
                    }
                }
            }

            if (showEndTimePicker) {
                val tp = rememberTimePickerState(endHour, endMinute, true)
                Dialog(onDismissRequest = { showEndTimePicker = false }) {
                    Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 6.dp) {
                        Column(Modifier.padding(16.dp)) {
                            TimePicker(state = tp)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { showEndTimePicker = false }) { Text("Cancelar") }
                                TextButton(onClick = {
                                    endHour = tp.hour
                                    endMinute = tp.minute
                                    showEndTimePicker = false
                                }) { Text("Aceptar") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarSelector(calendars: List<CalInfo>, selectedId: Long, onSelect: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = calendars.firstOrNull { it.id == selectedId }?.let {
        if (it.readOnly) "${it.displayName} (solo lectura)" else it.displayName
    } ?: "Seleccionar calendario"
    Box {
        OutlinedTextField(
            value = label, onValueChange = {}, readOnly = true,
            label = { Text("Calendario") }, modifier = Modifier.fillMaxWidth(),
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) }
        )
        Box(Modifier.matchParentSize().clickable { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            calendars.forEach { c ->
                DropdownMenuItem(
                    text = { Text(if (c.readOnly) "${c.displayName} (solo lectura)" else c.displayName) },
                    onClick = { onSelect(c.id); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun ReminderSelector(selected: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(-1, 0, 10, 30, 60, 1440)
    Box {
        OutlinedTextField(
            value = reminderLabel(selected), onValueChange = {}, readOnly = true,
            label = { Text("Recordatorio") }, modifier = Modifier.fillMaxWidth(),
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) }
        )
        Box(Modifier.matchParentSize().clickable { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { m ->
                DropdownMenuItem(text = { Text(reminderLabel(m)) }, onClick = { onSelect(m); expanded = false })
            }
        }
    }
}

