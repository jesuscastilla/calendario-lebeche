package com.lebeche.calendario

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.lebeche.calendario.data.Db
import com.lebeche.calendario.ui.CalendarApp
import com.lebeche.calendario.ui.CalendarioLebecheTheme

class MainActivity : ComponentActivity() {

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestInitialPermissions()
        checkForUpdates()
        setContent {
            CalendarioLebecheTheme {
                CalendarApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Si el usuario sale de la app durante una actualización obligatoria y vuelve a entrar,
        // esto reanudará la pantalla de bloqueo de actualización.
        val updateManager = AppUpdateManagerFactory.create(this)
        updateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                updateManager.startUpdateFlowForResult(
                    info,
                    this,
                    AppUpdateOptions.defaultOptions(AppUpdateType.IMMEDIATE),
                    1001,
                )
            }
        }
    }

    private fun checkForUpdates() {
        val updateManager = AppUpdateManagerFactory.create(this)
        updateManager.appUpdateInfo.addOnSuccessListener { info ->
            // Si hay una actualización disponible y Google Play permite forzarla (modo IMMEDIATE)
            if ((info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE)
                && info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
            ) {
                // Borrar todos los datos de la base de datos (forzar cierre de sesión)
                Db.get(this).clearAllData()

                // Lanzar la pantalla bloqueante de actualización de Google Play
                updateManager.startUpdateFlowForResult(
                    info,
                    this,
                    AppUpdateOptions.defaultOptions(AppUpdateType.IMMEDIATE),
                    1001,
                )
            }
        }
    }

    private fun requestInitialPermissions() {
        val perms = mutableListOf<String>()
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) &&
            (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
        ) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.READ_CALENDAR)
        }
        if (checkSelfPermission(Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.WRITE_CALENDAR)
        }
        if (perms.isNotEmpty()) permissionLauncher.launch(perms.toTypedArray())
    }
}
