package com.lebeche.calendario

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.lebeche.calendario.ui.CalendarApp
import com.lebeche.calendario.ui.CalendarioLebecheTheme

class MainActivity : ComponentActivity() {

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestInitialPermissions()
        setContent {
            CalendarioLebecheTheme {
                CalendarApp()
            }
        }
    }

    private fun requestInitialPermissions() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
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
