package com.lebeche.calendario.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lebeche.calendario.Repository
import kotlinx.coroutines.launch

class WelcomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository.get(app)

    var isConnecting by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)

    fun connect(username: String, password: String, insecureTls: Boolean, onSuccess: () -> Unit) {
        viewModelScope.launch {
            isConnecting = true
            message = null
            val result = repo.addAccount(
                name = Repository.DEFAULT_ACCOUNT_NAME,
                baseUrl = Repository.DEFAULT_CALDAV_URL,
                username = username.trim(),
                password = password.trim(),
                insecureTls = insecureTls
            )
            isConnecting = false
            if (result.discovered > 0) {
                onSuccess()
            } else {
                if (result.accountId > 0L) repo.deleteAccount(result.accountId)
                message = result.error?.takeIf { it.isNotBlank() }
                    ?.let { "No se pudo conectar.\n$it" }
                    ?: "No se encontraron calendarios. Revisa tus credenciales o contacta con Pelotxo."
            }
        }
    }
}

@Composable
fun WelcomeScreen(onConnected: () -> Unit) {
    val vm: WelcomeViewModel = viewModel()
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var insecure by remember { mutableStateOf(false) }
    var passVisible by remember { mutableStateOf(false) }

    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Calendario Lebeche", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))
            Text(
                "Bienvenido/a a la agenda de la asociación.\nPara saber tus credenciales de acceso (usuario y contraseña) contacta con Pelotxo.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(28.dp))
            OutlinedTextField(
                value = user, onValueChange = { user = it },
                label = { Text("Usuario") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = pass, onValueChange = { pass = it },
                label = { Text("Contraseña") },
                visualTransformation = if (passVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    autoCorrectEnabled = false,
                    capitalization = KeyboardCapitalization.None
                ),
                trailingIcon = {
                    IconButton(onClick = { passVisible = !passVisible }) {
                        Icon(
                            imageVector = if (passVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (passVisible) "Ocultar contraseña" else "Mostrar contraseña"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Aceptar certificados autofirmados", Modifier.weight(1f))
                Switch(checked = insecure, onCheckedChange = { insecure = it })
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { vm.connect(user, pass, insecure) { onConnected() } },
                enabled = user.isNotBlank() && pass.isNotBlank() && !vm.isConnecting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (vm.isConnecting) "Conectando…" else "Conectar")
            }
            vm.message?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
        }
    }
}
