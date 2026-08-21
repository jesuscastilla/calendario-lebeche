package com.lebeche.calendario.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lebeche.calendario.Repository
import com.lebeche.calendario.data.Account
import com.lebeche.calendario.data.CalInfo
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repository.get(app)

    var accounts by mutableStateOf<List<Account>>(emptyList())
    var calendars by mutableStateOf<Map<Long, List<CalInfo>>>(emptyMap())
    var message by mutableStateOf<String?>(null)
    var isSyncing by mutableStateOf(false)

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            accounts = repo.accounts()
            val map = mutableMapOf<Long, List<CalInfo>>()
            for (a in accounts) map[a.id] = repo.calendars(a.id)
            calendars = map
        }
    }

    fun addAccount(name: String, url: String, user: String, pass: String, insecure: Boolean) {
        viewModelScope.launch {
            val (_, discovered) = repo.addAccount(name, url, user, pass, insecure)
            message = "Cuenta añadida · $discovered calendarios encontrados"
            refresh()
        }
    }

    fun deleteAccount(id: Long) {
        viewModelScope.launch {
            repo.deleteAccount(id)
            refresh()
        }
    }

    fun syncAccount(id: Long) {
        viewModelScope.launch {
            isSyncing = true
            val s = repo.syncAccountNow(id)
            isSyncing = false
            message = if (s.errors.isEmpty()) "Sincronizado" else "Errores: ${s.errors.joinToString()}"
            refresh()
        }
    }

    fun toggleCalendar(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            repo.setCalendarEnabled(id, enabled)
            refresh()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val vm: SettingsViewModel = viewModel()
    var showForm by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var insecure by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Volver") }
                }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            item {
                vm.message?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
                Spacer(Modifier.height(8.dp))
                Text("Cuentas CalDAV", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
            }

            if (vm.accounts.isEmpty() && !showForm) {
                item {
                    Text("No hay cuentas configuradas. Añade una para conectar con Synology u otro servidor CalDAV.")
                }
            }

            items(vm.accounts, key = { it.id }) { account ->
                AccountCard(
                    account = account,
                    calendars = vm.calendars[account.id].orEmpty(),
                    onSync = { vm.syncAccount(account.id) },
                    onDelete = { vm.deleteAccount(account.id) },
                    onToggle = { id, enabled -> vm.toggleCalendar(id, enabled) }
                )
            }

            item {
                Spacer(Modifier.height(12.dp))
                if (showForm) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text("Nombre (ej. Synology)") }, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = url, onValueChange = { url = it },
                        label = { Text("URL CalDAV (ej. https://tu-nas/caldav)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = user, onValueChange = { user = it },
                        label = { Text("Usuario") }, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pass, onValueChange = { pass = it },
                        label = { Text("Contraseña") }, modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Aceptar certificados autofirmados", Modifier.weight(1f))
                        Switch(checked = insecure, onCheckedChange = { insecure = it })
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            vm.addAccount(name.trim(), url.trim(), user.trim(), pass, insecure)
                            name = ""; url = ""; user = ""; pass = ""
                            showForm = false
                        },
                        enabled = url.isNotBlank() && user.isNotBlank() && pass.isNotBlank()
                    ) { Text("Conectar") }
                    TextButton(onClick = { showForm = false }) { Text("Cancelar") }
                } else {
                    Button(onClick = { showForm = true }) { Text("Añadir cuenta CalDAV") }
                }
            }
        }
    }
}

@Composable
private fun AccountCard(
    account: Account,
    calendars: List<CalInfo>,
    onSync: () -> Unit,
    onDelete: () -> Unit,
    onToggle: (Long, Boolean) -> Unit
) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(account.name, style = MaterialTheme.typography.titleMedium)
            Text(account.baseUrl, style = MaterialTheme.typography.bodySmall)
            Row {
                TextButton(onClick = onSync) { Text("Sincronizar") }
                TextButton(onClick = onDelete) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            }
            if (calendars.isEmpty()) {
                Text("Sin calendarios descubiertos", style = MaterialTheme.typography.bodySmall)
            } else {
                calendars.forEach { cal ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(10.dp).clip(CircleShape)
                                .background(Color(cal.color))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(cal.displayName, Modifier.weight(1f))
                        Switch(checked = cal.enabled, onCheckedChange = { onToggle(cal.id, it) })
                    }
                }
            }
        }
    }
}
