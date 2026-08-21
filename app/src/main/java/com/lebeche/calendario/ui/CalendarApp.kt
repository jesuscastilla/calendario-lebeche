package com.lebeche.calendario.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

sealed class Screen {
    object Main : Screen()
    data class Detail(val eventId: Long) : Screen()
    data class Edit(val eventId: Long?) : Screen()
    object Settings : Screen()
}

/** Raíz de la app: gestiona la navegación simple entre pantallas. */
@Composable
fun CalendarApp() {
    var screen by remember { mutableStateOf<Screen>(Screen.Main) }

    when (val s = screen) {
        is Screen.Main -> MainScreen(
            onOpenEvent = { screen = Screen.Detail(it) },
            onCreateEvent = { screen = Screen.Edit(null) },
            onOpenSettings = { screen = Screen.Settings }
        )

        is Screen.Detail -> EventDetailScreen(
            eventId = s.eventId,
            onBack = { screen = Screen.Main },
            onEdit = { screen = Screen.Edit(s.eventId) }
        )

        is Screen.Edit -> EventEditScreen(
            eventId = s.eventId,
            onDone = { screen = Screen.Main }
        )

        is Screen.Settings -> SettingsScreen(
            onBack = { screen = Screen.Main }
        )
    }
}
