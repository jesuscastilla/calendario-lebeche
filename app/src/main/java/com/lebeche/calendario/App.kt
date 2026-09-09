package com.lebeche.calendario

import android.app.Application
import com.lebeche.calendario.notif.NotificationPublisher
import com.lebeche.calendario.notif.ReminderScheduler
import com.lebeche.calendario.sync.SyncWorker

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            NotificationPublisher.ensureChannel(this)
            SyncWorker.schedule(this)
            // Reprograma recordatorios (también aplica el recordatorio por defecto de Ajustes).
            Thread { ReminderScheduler.rescheduleAll(this) }.start()
        } catch (e: Exception) {
            // Log or handle initial setup failure without crashing the entire app
            e.printStackTrace()
        }
    }
}