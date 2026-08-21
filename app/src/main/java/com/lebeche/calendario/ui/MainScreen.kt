package com.lebeche.calendario.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lebeche.calendario.Repository
import com.lebeche.calendario.data.CalInfo
import com.lebeche.calendario.data.Occurrence
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repository.get(app)

    var month by mutableStateOf(YearMonth.now())
    var selectedDate by mutableStateOf(LocalDate.now())
    var occurrences by mutableStateOf<List<Occurrence>>(emptyList())
    var calendars by mutableStateOf<List<CalInfo>>(emptyList())
    var isSyncing by mutableStateOf(false)
    var syncMessage by mutableStateOf<String?>(null)

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            calendars = repo.allCalendars()
            loadMonth()
        }
    }

    fun loadMonth() {
        viewModelScope.launch {
            val zone = ZoneId.systemDefault()
            val from = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            occurrences = repo.occurrences(from, to)
        }
    }

    fun prevMonth() {
        month = month.minusMonths(1)
        loadMonth()
    }

    fun nextMonth() {
        month = month.plusMonths(1)
        loadMonth()
    }

    fun select(date: LocalDate) {
        selectedDate = date
    }

    fun sync() {
        viewModelScope.launch {
            isSyncing = true
            val summary = repo.syncAll()
            isSyncing = false
            syncMessage = if (summary.errors.isEmpty()) "Sincronizado" else "Errores: ${summary.errors.joinToString()}"
            refresh()
        }
    }
}
