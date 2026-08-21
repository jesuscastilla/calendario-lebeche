package com.lebeche.calendario.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lebeche.calendario.Repository
import com.lebeche.calendario.data.Event
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

class DetailViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository.get(app)
    var event by mutableStateOf<Event?>(null)
    var calendarName by mutableStateOf("")
    var loading by mutableStateOf(true)

    fun load(id: Long) {
        viewModelScope.launch {
            loading = true
            val e = repo.event(id)
            event = e
            calendarName = e?.let { ev ->
                repo.allCalendars().firstOrNull { it.id == ev.calendarId }?.displayName ?: ""
            } ?: ""
            loading = false
        }
    }

    fun delete(id: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.deleteEvent(id)
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(eventId: Long, onBack: () -> Unit, onEdit: () -> Unit) {
    val vm: DetailViewModel = viewModel()
    LaunchedEffect(eventId) { vm.load(eventId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalle del evento") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Volver") }
                },
                actions = {
                    IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Editar") }
                    IconButton(onClick = { vm.delete(eventId, onBack) }) {
                        Icon(Icons.Filled.Delete, "Eliminar")
                    }
                }
            )
        }
    ) { padding ->
        val e = vm.event
        if (e == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (vm.loading) "Cargando…" else "Evento no encontrado")
            }
        } else {
            Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
                Text(e.title, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))
                DetailRow("Fecha", formatDates(e))
                if (e.location.isNotBlank()) DetailRow("Lugar", e.location)
                if (e.description.isNotBlank()) DetailRow("Descripción", e.description)
                DetailRow("Calendario", vm.calendarName.ifBlank { "—" })
                if (!e.rrule.isNullOrBlank()) DetailRow("Repetición", e.rrule)
            }
        }
    }
}

private fun formatDates(e: Event): String {
    val zone = if (e.allDay) ZoneOffset.UTC else ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(e.dtstart).atZone(zone)
    if (e.allDay) {
        return DateTimeFormatter.ofPattern("EEEE d 'de' MMMM yyyy", Locale("es", "ES")).format(start)
    }
    val fmt = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM yyyy · HH:mm", Locale("es", "ES"))
    val endFmt = DateTimeFormatter.ofPattern("HH:mm", Locale("es", "ES"))
    val end = Instant.ofEpochMilli(e.dtend).atZone(zone)
    return fmt.format(start) + " - " + endFmt.format(end)
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
    HorizontalDivider()
}
