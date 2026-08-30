package com.lebeche.calendario.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lebeche.calendario.Repository
import kotlinx.coroutines.launch

sealed class Screen {
    object Main : Screen()
    data class Detail(val eventId: Long) : Screen()
    data class Edit(val eventId: Long?) : Screen()
    object Settings : Screen()
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository.get(app)
    var initialized by mutableStateOf(false)
    var hasAccounts by mutableStateOf(false)

    init {
        viewModelScope.launch {
            hasAccounts = repo.accounts().isNotEmpty()
            initialized = true
        }
    }

    fun refresh() {
        viewModelScope.launch {
            hasAccounts = repo.accounts().isNotEmpty()
        }
    }
}

/** Raíz de la app: gestiona la navegación simple entre pantallas. */
@Composable
fun CalendarApp() {
    val appVm: AppViewModel = viewModel()
    var screen by remember { mutableStateOf<Screen>(Screen.Main) }

    when {
        !appVm.initialized -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        !appVm.hasAccounts -> WelcomeScreen(onConnected = { appVm.refresh() })

        else -> when (val s = screen) {
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
}
